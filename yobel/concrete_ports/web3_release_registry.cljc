(ns yobel.concrete-ports.web3-release-registry
  "Web3 concrete impl wrapping YobelReleaseRegistry.sol — cljc port of
  concrete_ports/web3_release_registry.py (ADR-2605201800). Utility port the
  release_settlement cell uses to mirror its release record on-chain (NOT a full
  Protocol impl). Writes go through eth_sendTransaction with a node-managed
  `sender` account (matching web3.py .transact + the anvil harness); reads via
  eth_call. The one-way debt-cap invariant (Charter Rider §2(b)) is enforced
  on-chain by the contract revert, surfaced here as a thrown JSON-RPC error."
  (:require [eth-crypto.core :as eth]
            [yobel.concrete-ports.web3-rpc :as rpc]))

(def ^:private RELEASE-METHOD-BY-NAME
  {"voluntary_bookkeeping" 0
   "base_l2_transfer" 1
   "court_order" 2
   "sovereign_decree" 3
   "ecclesiastical_indulgence" 4})

(def ^:private ZERO32 (byte-array 32))

(defrecord Web3ReleaseRegistryPort [rpc-url address sender])

(defn make-web3-release-registry-port [rpc-url address sender-address]
  (->Web3ReleaseRegistryPort rpc-url
                             (eth/eip55-checksum address)
                             (eth/eip55-checksum sender-address)))

;; ─── Writes ──────────────────────────────────────────────────────────

(defn register-debt-cap
  "registerDebtCap(bytes32 rite, bytes32 debt, uint256 principal, uint256 accrued) → tx-hash."
  [{:keys [rpc-url address sender]} rite-id debt-id principal-micro-usdc accrued-micro-usdc]
  (let [data (rpc/call-data "registerDebtCap(bytes32,bytes32,uint256,uint256)"
                            (rpc/enc-bytes32 (rpc/to-bytes32 rite-id))
                            (rpc/enc-bytes32 (rpc/to-bytes32 debt-id))
                            (rpc/enc-uint principal-micro-usdc)
                            (rpc/enc-uint accrued-micro-usdc))
        tx (rpc/send-tx rpc-url sender address data)]
    (rpc/wait-receipt rpc-url tx)
    tx))

(defn record-release
  "recordRelease(...) → tx-hash. Throws if the contract reverts (e.g. cumulative
  release over the registered debt cap — the §2(b) one-way hard gate)."
  [{:keys [rpc-url address sender]}
   {:keys [release-id rite-id debt-id debtor-did creditor-did
           released-micro-usdc release-method base-l2-tx-hash-cross-ref]}]
  (let [method-idx (RELEASE-METHOD-BY-NAME release-method)]
    (when (nil? method-idx)
      (throw (ex-info (str "unknown release_method: " release-method) {:release-method release-method})))
    (let [data (rpc/call-data
                "recordRelease(bytes32,bytes32,bytes32,bytes32,bytes32,uint256,uint8,bytes32)"
                (rpc/enc-bytes32 (rpc/to-bytes32 release-id))
                (rpc/enc-bytes32 (rpc/to-bytes32 rite-id))
                (rpc/enc-bytes32 (rpc/to-bytes32 debt-id))
                (rpc/enc-bytes32 (rpc/did-hash debtor-did))
                (rpc/enc-bytes32 (rpc/did-hash creditor-did))
                (rpc/enc-uint released-micro-usdc)
                (rpc/enc-uint method-idx)
                (rpc/enc-bytes32 (or base-l2-tx-hash-cross-ref ZERO32)))
          tx (rpc/send-tx rpc-url sender address data)]
      (rpc/wait-receipt rpc-url tx)
      tx)))

;; ─── Reads ───────────────────────────────────────────────────────────

(defn get-debt-cap
  "getDebtCap(bytes32,bytes32) → {:principal-micro-usdc :accrued-micro-usdc
   :total-released-micro-usdc :registrar :registered-at}."
  [{:keys [rpc-url address]} rite-id debt-id]
  (let [data (rpc/call-data "getDebtCap(bytes32,bytes32)"
                            (rpc/enc-bytes32 (rpc/to-bytes32 rite-id))
                            (rpc/enc-bytes32 (rpc/to-bytes32 debt-id)))
        w (rpc/words (rpc/eth-call rpc-url address data))]
    {:principal-micro-usdc (rpc/dec-uint (nth w 0))
     :accrued-micro-usdc (rpc/dec-uint (nth w 1))
     :total-released-micro-usdc (rpc/dec-uint (nth w 2))
     :registrar (rpc/dec-address-hex (nth w 3))
     :registered-at (.longValue (rpc/dec-uint (nth w 4)))}))

(defn release-count [{:keys [rpc-url address]}]
  (.longValue (rpc/dec-uint (first (rpc/words
                                    (rpc/eth-call rpc-url address (rpc/call-data "releaseCount()")))))))

(defn release-count-by-rite [{:keys [rpc-url address]} rite-id]
  (.longValue (rpc/dec-uint (first (rpc/words
                                    (rpc/eth-call rpc-url address
                                                  (rpc/call-data "releaseCountByRite(bytes32)"
                                                                 (rpc/enc-bytes32 (rpc/to-bytes32 rite-id)))))))))
