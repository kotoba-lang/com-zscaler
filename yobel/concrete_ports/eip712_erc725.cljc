(ns yobel.concrete-ports.eip712-erc725
  "Real EIP-712 signed-consent verifier — cljc port of
  concrete_ports/eip712_erc725.py (ADR-2605201800 / ADR-0074 Ethereum Identity
  Bridge). The creditor signs a canonical EIP-712 typed-data envelope over the
  rite + debts payload; this port recovers the signer (secp256k1 ecrecover) and
  asserts it matches the creditor DID's registered ERC725 owner address (EIP-55
  checksum compare). Satisfies yobel.ports/Erc725Port.

  EIP-712 domain:
    name              = \"YobelCreditorConsent\"
    version           = \"1\"
    chainId           = the chain where YobelRiteRegistry is deployed
    verifyingContract = YobelRiteRegistry address

  Type:
    CreditorConsent { bytes32 riteId, bytes32 creditorDidHash, bytes32 debtsRootHash }
      creditorDidHash = keccak256(creditor_did)
      debtsRootHash   = keccak256(canonical-JSON(debts))   ; json.dumps sort_keys, (\",\",\":\")"
  (:require [clojure.string :as str]
            [yobel.ports :as ports]
            [eth-crypto.core :as eth]))

(def ^:private DOMAIN-NAME "YobelCreditorConsent")
(def ^:private DOMAIN-VERSION "1")

(def ^:private CONSENT-TYPES
  {"CreditorConsent" [{:name "riteId" :type "bytes32"}
                      {:name "creditorDidHash" :type "bytes32"}
                      {:name "debtsRootHash" :type "bytes32"}]})

;; ─── canonical JSON (matches Python json.dumps sort_keys, separators (",",":"),
;;     ensure_ascii=True default) so debtsRootHash is byte-identical to the Python
;;     signer's. Keyword keys are emitted via (name k) — feed snake_case lexicon
;;     field names (debt_id, principal_micro_usdc, …) for cross-consistency. ───

(defn- json-string ^String [s]
  (let [sb (StringBuilder. "\"")]
    (doseq [ch (str s)]
      (let [c (int ch)]
        (cond
          (= ch \") (.append sb "\\\"")
          (= ch \\) (.append sb "\\\\")
          (= ch \newline) (.append sb "\\n")
          (= ch \return) (.append sb "\\r")
          (= ch \tab) (.append sb "\\t")
          (= c 8) (.append sb "\\b")
          (= c 12) (.append sb "\\f")
          (< c 0x20) (.append sb (format "\\u%04x" c))
          (> c 0x7e) (.append sb (format "\\u%04x" c))
          :else (.append sb ch))))
    (.append sb "\"")
    (.toString sb)))

(defn- key->str ^String [k]
  (cond (keyword? k) (name k)
        (string? k) k
        :else (str k)))

(defn canonical-json ^String [x]
  (cond
    (nil? x) "null"
    (map? x) (let [pairs (sort-by first (map (fn [[k v]] [(key->str k) v]) x))]
               (str "{"
                    (str/join "," (map (fn [[k v]] (str (json-string k) ":" (canonical-json v))) pairs))
                    "}"))
    (sequential? x) (str "[" (str/join "," (map canonical-json x)) "]")
    (string? x) (json-string x)
    (keyword? x) (json-string (name x))
    (boolean? x) (if x "true" "false")
    (integer? x) (str x)
    (float? x) (str x)
    :else (json-string (str x))))

;; ─── helpers ─────────────────────────────────────────────────────────

(defn- pget
  "Read a payload value tolerating kebab keyword, snake keyword, or snake string keys."
  [m kw snake]
  (cond
    (contains? m kw) (get m kw)
    (contains? m (keyword snake)) (get m (keyword snake))
    (contains? m snake) (get m snake)
    :else nil))

(defn- ^bytes normalize-bytes32
  "rite_id → 32 bytes: a 0x-prefixed 32-byte hex string verbatim, a 32-byte array
  verbatim, else keccak256(utf8 text)."
  [v]
  (cond
    (bytes? v) v
    (and (string? v) (str/starts-with? v "0x") (= (count v) 66)) (eth/hex->bytes v)
    :else (eth/keccak256 (eth/utf8 (str v)))))

(defn- ^bytes debts-root-hash [debts]
  (eth/keccak256 (eth/utf8 (canonical-json (or debts [])))))

;; ─── port ────────────────────────────────────────────────────────────

(defrecord Eip712Erc725Port [chain-id verifying-contract resolve-fn]
  ports/Erc725Port
  (verify-eip712-signed-consent [_ signer-did payload signature]
    (let [expected-owner (resolve-fn signer-did)]
      (if (nil? expected-owner)
        false
        (let [domain {"name" DOMAIN-NAME
                      "version" DOMAIN-VERSION
                      "chainId" chain-id
                      "verifyingContract" verifying-contract}
              message {"riteId" (normalize-bytes32 (pget payload :rite-id "rite_id"))
                       "creditorDidHash" (eth/keccak256 (eth/utf8 (str (pget payload :creditor-did "creditor_did"))))
                       "debtsRootHash" (debts-root-hash (pget payload :debts "debts"))}]
          (try
            (let [digest (eth/eip712-digest domain CONSENT-TYPES "CreditorConsent" message)
                  recovered (eth/ecrecover digest (eth/hex->bytes signature))]
              (= (eth/eip55-checksum recovered)
                 (eth/eip55-checksum expected-owner)))
            (catch #?(:clj Exception :cljs :default) _ false)))))))

(defn make-eip712-erc725-port
  "chain-id (int), verifying-contract (0x address hex), resolve-fn (did -> owner-addr | nil)."
  [chain-id verifying-contract resolve-fn]
  (->Eip712Erc725Port chain-id
                      (eth/eip55-checksum verifying-contract)
                      resolve-fn))
