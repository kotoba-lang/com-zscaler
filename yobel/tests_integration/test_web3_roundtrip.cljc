(ns yobel.tests-integration.test-web3-roundtrip
  "EVM integration harness (bb port of tests_integration/test_web3_roundtrip.py,
  ADR-2605201800; final web3.py/eth_account → eth-crypto-clj prune). Operator-run:
  needs foundry's `anvil` on PATH.

  Skip-not-fail invariant
  ───────────────────────
  When `anvil` is ABSENT (CI / no foundry) the live-chain leg SKIPS GRACEFULLY —
  it logs and passes with zero assertions (mirrors pytest `-m 'not integration'`),
  so the suite stays green. The Python harness could never run here either (it is
  `pytest.mark.integration`, also gated on anvil). What runs UNCONDITIONALLY:
    • ABI selector verification — keccak256(sig)[0:4] for every registry method the
      harness calls, reconstructed from the COMMITTED abi/*.json and matched.
    • Signing path — a deploy tx built + signed by the verified `sign-tx-legacy`
      (EIP-155, spec-verified in eth-crypto-clj) to a well-formed RLP raw tx.
    • EIP-712 signed-consent accept/reject — pure secp256k1, needs no chain, so it
      runs offline (a DEVIATION from the Python test, which gated it on the deployed
      contract only for the verifying-contract address; here any address works and
      the genuine/forged crypto is exercised in every green run).

  When anvil IS present the `evm-roundtrip` deftest additionally spawns it, deploys
  YobelRiteRegistry + YobelReleaseRegistry from the abi/*.json bytecode via
  eth_sendRawTransaction (tx signed with sign-tx-legacy, anvil dev account 0), runs
  declare→ratify→registerDebtCap→record-release through the cljc ports, and asserts
  the §2(b) over-cap release reverts. Anvil is torn down in a `finally`."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [eth-crypto.core :as eth]
            [yobel.ports]
            [yobel.concrete-ports.web3-rpc :as rpc]
            [yobel.concrete-ports.web3-rite-registry :as rite]
            [yobel.concrete-ports.web3-release-registry :as rel]
            [yobel.concrete-ports.eip712-erc725 :as erc725]))

;; ─── env / fixtures ───────────────────────────────────────────────────

(def ^:private abi-dir
  (let [here (str (fs/parent (fs/parent *file*)))]    ; …/yobel/tests_integration/x.cljc → …/yobel
    (str here "/abi")))

(defn- load-artifact [name] (json/parse-string (slurp (str abi-dir "/" name)) true))

;; anvil dev account 0 (deterministic; safe to commit — it is the public anvil key)
(def ^:private dev-privkey
  (eth/hex->bytes "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"))
(def ^:private dev-address "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
(def ^:private chain-id 31337)

(defn- anvil-available? [] (some? (fs/which "anvil")))

(defn- free-port ^long []
  (let [s (java.net.ServerSocket. 0)]
    (try (.getLocalPort s) (finally (.close s)))))

;; ─── deploy via eth_sendRawTransaction (signed by sign-tx-legacy) ──────

(defn- deploy-tx
  "Build a legacy contract-creation tx map (to = \"\" so RLP encodes the empty
  recipient). `data` = bytecode-hex ++ abi-encoded constructor args."
  [rpc-url nonce data]
  {:nonce nonce :gas-price 1000000000 :gas 6000000 :to "" :value 0
   :data data :chain-id chain-id})

(defn- nonce-of [rpc-url addr]
  (.longValue (rpc/dec-uint (eth/strip0x (rpc/rpc! rpc-url "eth_getTransactionCount" [addr "pending"])))))

(defn- deploy!
  "Sign + send a creation tx, wait for the receipt, return its contractAddress."
  [rpc-url nonce data]
  (let [raw (eth/sign-tx-legacy (deploy-tx rpc-url nonce data) dev-privkey)
        tx  (rpc/rpc! rpc-url "eth_sendRawTransaction" [raw])
        rcpt (rpc/wait-receipt rpc-url tx)]
    (or (:contractAddress rcpt) (get rcpt "contractAddress"))))

;; ─── EIP-712 consent signing (genuine + forged), pure crypto ──────────

(def ^:private consent-types
  {"CreditorConsent" [{:name "riteId" :type "bytes32"}
                      {:name "creditorDidHash" :type "bytes32"}
                      {:name "debtsRootHash" :type "bytes32"}]})

(defn- canonical-debts-hash ^bytes [debts]
  (eth/keccak256 (eth/utf8 (erc725/canonical-json debts))))

(defn- sign-consent
  "Produce the 0x… 65-byte signature the Eip712Erc725Port expects, signing the
  CreditorConsent digest with `privkey`."
  ^String [verifying-contract creditor-did rite-id debts privkey]
  (let [domain  {"name" "YobelCreditorConsent" "version" "1"
                 "chainId" chain-id "verifyingContract" (eth/eip55-checksum verifying-contract)}
        message {"riteId" (eth/keccak256 (eth/utf8 rite-id))
                 "creditorDidHash" (eth/keccak256 (eth/utf8 creditor-did))
                 "debtsRootHash" (canonical-debts-hash debts)}
        digest  (eth/eip712-digest domain consent-types "CreditorConsent" message)
        {:keys [r s recovery-id]} (eth/secp256k1-sign privkey digest)
        sig (byte-array 65)]
    (System/arraycopy (eth/hex->bytes (rpc/enc-uint r)) 0 sig 0 32)
    (System/arraycopy (eth/hex->bytes (rpc/enc-uint s)) 0 sig 32 32)
    (aset-byte sig 64 (unchecked-byte (+ recovery-id 27)))
    (str "0x" (eth/bytes->hex sig))))

;; ─── ABI selector verification (unconditional) ────────────────────────

(defn- abi-signature
  "Canonical solidity signature `name(t1,t2,…)` reconstructed from a committed
  abi/*.json function entry."
  ^String [artifact fn-name]
  (let [e (->> (:abi artifact)
               (filter #(and (= "function" (:type %)) (= fn-name (:name %))))
               first)]
    (assert e (str "no abi entry for " fn-name))
    (str fn-name "(" (str/join "," (map :type (:inputs e))) ")")))

(deftest abi-selectors-match-committed-abi
  (testing "the selector strings the harness/ports call match the COMMITTED abi/*.json"
    (let [rite-art (load-artifact "YobelRiteRegistry.json")
          rel-art  (load-artifact "YobelReleaseRegistry.json")
          ;; the literal signatures hard-coded in the cljc ports + this harness
          rite-sigs ["declareRite(bytes32,uint8,bytes32,bytes32,uint64,uint64,bytes32)"
                     "ratifyRite(bytes32,bytes32,uint16)"
                     "rites(bytes32)"]
          rel-sigs  ["registerDebtCap(bytes32,bytes32,uint256,uint256)"
                     "recordRelease(bytes32,bytes32,bytes32,bytes32,bytes32,uint256,uint8,bytes32)"
                     "getDebtCap(bytes32,bytes32)"
                     "releaseCount()"
                     "releaseCountByRite(bytes32)"]
          check (fn [art sig]
                  (let [fn-name (subs sig 0 (str/index-of sig "("))
                        from-abi (abi-signature art fn-name)
                        sel (rpc/function-selector sig)]
                    ;; (a) hard-coded sig == sig reconstructed from committed ABI
                    (is (= sig from-abi) (str fn-name ": port sig vs committed ABI"))
                    ;; (b) keccak256(sig)[0:4] is a well-formed 4-byte selector
                    (is (re-matches #"[0-9a-f]{8}" sel) (str fn-name ": selector well-formed"))
                    ;; (c) selector recomputed from the ABI-derived sig agrees
                    (is (= sel (rpc/function-selector from-abi)) (str fn-name ": selector stable"))))]
      (doseq [s rite-sigs] (check rite-art s))
      (doseq [s rel-sigs]  (check rel-art s))
      ;; spot-check the known canonical 4-byte values
      (is (= "313ce567" (rpc/function-selector "decimals()")) "sanity: known selector"))))

;; ─── Signing path (unconditional) ─────────────────────────────────────

(deftest deploy-tx-signs-to-wellformed-rlp
  (testing "the dev privkey controls anvil account 0"
    (is (= dev-address (eth/address-of-privkey dev-privkey))))
  (testing "a registry deploy tx signs to a well-formed RLP raw tx"
    (let [rite-art (load-artifact "YobelRiteRegistry.json")
          rite-bc  (:bytecode rite-art)
          rel-art  (load-artifact "YobelReleaseRegistry.json")
          ;; Release constructor takes the Rite address — append an encoded address arg
          rel-data (str (:bytecode rel-art) (rpc/enc-address dev-address))
          tx0 (deploy-tx nil 0 rite-bc)
          raw (eth/sign-tx-legacy tx0 dev-privkey)]
      (is (str/starts-with? raw "0x") "0x-prefixed")
      (is (even? (count (eth/strip0x raw))) "even-length hex")
      ;; long legacy txs RLP-encode as a list with a 0xf8/0xf9 length-prefixed header
      (is (contains? #{"f8" "f9" "fa"} (subs (eth/strip0x raw) 0 2)) "RLP list header")
      (is (= raw (eth/sign-tx-legacy tx0 dev-privkey)) "deterministic (RFC-6979)")
      ;; the constructor-arg deploy also signs (data = bytecode ++ enc-address)
      (is (str/starts-with? (eth/sign-tx-legacy (deploy-tx nil 1 rel-data) dev-privkey) "0x")))))

;; ─── EIP-712 accept/reject (pure crypto, runs offline) ────────────────

(deftest eip712-signed-consent-accept-reject
  (let [verifying  "0x5FbDB2315678afecb367f032d93F642f64180aa3"  ; deterministic anvil addr; offline OK
        creditor   "did:web:creditor.example"
        rite-id    "shmita-5786"
        debts      [{:debt_id "d1" :principal_micro_usdc 100 :origination_date "2022-01-01T00:00:00Z"}]
        signer-priv (eth/hex->bytes (str "0x" (apply str (repeat 32 "11"))))
        signer-addr (eth/address-of-privkey signer-priv)
        forger-priv (eth/hex->bytes (str "0x" (apply str (repeat 32 "33"))))
        port (erc725/make-eip712-erc725-port chain-id verifying
                                             (fn [did] (when (= did creditor) signer-addr)))
        payload {:rite-id rite-id :creditor-did creditor :debts debts}]
    (testing "genuine signature is accepted"
      (is (true? (yobel.ports/verify-eip712-signed-consent
                  port creditor payload
                  (sign-consent verifying creditor rite-id debts signer-priv)))))
    (testing "forged signature (different key) is rejected"
      (is (false? (yobel.ports/verify-eip712-signed-consent
                   port creditor payload
                   (sign-consent verifying creditor rite-id debts forger-priv)))))))

;; ─── live EVM roundtrip (skips gracefully when anvil absent) ───────────

(deftest evm-roundtrip
  (if-not (anvil-available?)
    (println "  [skip] anvil not on PATH, skipping EVM integration (suite stays green)")
    (let [port (free-port)
          rpc-url (str "http://127.0.0.1:" port)
          proc (p/process ["anvil" "--port" (str port) "--chain-id" (str chain-id) "--silent"]
                          {:out :inherit :err :inherit})]
      (try
        ;; poll readiness
        (let [deadline (+ (System/currentTimeMillis) 15000)]
          (loop []
            (when (> (System/currentTimeMillis) deadline)
              (throw (ex-info "anvil did not come up within 15s" {})))
            (let [up (try (rpc/rpc! rpc-url "eth_chainId" []) (catch Exception _ nil))]
              (when-not up (Thread/sleep 150) (recur)))))
        ;; deploy both registries from committed bytecode (signed raw txs)
        (let [rite-art (load-artifact "YobelRiteRegistry.json")
              rel-art  (load-artifact "YobelReleaseRegistry.json")
              n0       (nonce-of rpc-url dev-address)
              rite-addr (deploy! rpc-url n0 (:bytecode rite-art))
              rel-addr  (deploy! rpc-url (inc n0)
                                 (str (:bytecode rel-art) (rpc/enc-address rite-addr)))
              rite-port (rite/make-web3-rite-registry-port rpc-url rite-addr)
              rel-port  (rel/make-web3-release-registry-port rpc-url rel-addr dev-address)]
          (is (str/starts-with? rite-addr "0x"))
          (is (str/starts-with? rel-addr "0x"))
          ;; declare + ratify a rite via eth_sendTransaction (anvil unlocks account0)
          (rpc/send-tx rpc-url dev-address rite-addr
                       (rpc/call-data "declareRite(bytes32,uint8,bytes32,bytes32,uint64,uint64,bytes32)"
                                      (rpc/enc-bytes32 (rpc/to-bytes32 "yobel-test-one-way"))
                                      (rpc/enc-uint 0)
                                      (rpc/enc-bytes32 (rpc/to-bytes32 "d"))
                                      (rpc/enc-bytes32 (rpc/to-bytes32 "s"))
                                      (rpc/enc-uint 1758551400) (rpc/enc-uint 1789142400)
                                      (rpc/enc-bytes32 (rpc/to-bytes32 "i"))))
          (Thread/sleep 200)
          (rpc/send-tx rpc-url dev-address rite-addr
                       (rpc/call-data "ratifyRite(bytes32,bytes32,uint16)"
                                      (rpc/enc-bytes32 (rpc/to-bytes32 "yobel-test-one-way"))
                                      (rpc/enc-bytes32 (rpc/to-bytes32 "rat"))
                                      (rpc/enc-uint 4)))
          (Thread/sleep 200)
          ;; read the rite back through the cljc port
          (let [r (yobel.ports/get-rite rite-port "yobel-test-one-way")]
            (is (some? r))
            (is (= "shmita_7yr" (:rite-type r))))
          ;; register a debt cap + a release AT the cap through the cljc release port
          (rel/register-debt-cap rel-port "yobel-test-one-way" "test-debt-1" 100000000 0)
          (rel/record-release rel-port {:release-id "rel-at-cap"
                                        :rite-id "yobel-test-one-way" :debt-id "test-debt-1"
                                        :debtor-did "did:web:debtor.test"
                                        :creditor-did "did:web:creditor.test"
                                        :released-micro-usdc 100000000
                                        :release-method "base_l2_transfer"})
          (is (>= (rel/release-count rel-port) 1))
          ;; §2(b) one-way invariant: a release OVER the cap MUST revert (contract throw)
          (is (thrown? Exception
                       (rel/record-release rel-port {:release-id "rel-over-cap"
                                                     :rite-id "yobel-test-one-way" :debt-id "test-debt-1"
                                                     :debtor-did "did:web:debtor.test"
                                                     :creditor-did "did:web:creditor.test"
                                                     :released-micro-usdc 1
                                                     :release-method "voluntary_bookkeeping"}))
              "cumulative-over-cap release reverts on-chain"))
        (finally
          (p/destroy-tree proc)
          (try @(p/process ["true"]) (catch Exception _ nil)))))))
