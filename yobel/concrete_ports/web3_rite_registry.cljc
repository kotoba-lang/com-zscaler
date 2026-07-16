(ns yobel.concrete-ports.web3-rite-registry
  "Web3 concrete impl of RiteRegistryPort backed by YobelRiteRegistry.sol — cljc
  port of concrete_ports/web3_rite_registry.py (ADR-2605201800). Read-side: maps
  the on-chain `rites(bytes32)` public-mapping struct to a yobel.ports/Rite.
  Writes (declare/ratify/…) go through the contract directly, not this port."
  (:require [clojure.string :as str]
            [yobel.ports :as ports]
            [eth-crypto.core :as eth]
            [yobel.concrete-ports.web3-rpc :as rpc]))

(def ^:private RITE-TYPE-BY-INDEX
  ["shmita_7yr" "yobel_50yr" "tokusei_rei" "religious_jubilee" "political_amnesty"])
(def ^:private STATUS-BY-INDEX
  ["unknown" "declared" "active" "completed" "cancelled" "superseded"])

;; rites(bytes32) tuple layout (each a single static 32-byte word):
;;  0 riteId b32 · 1 riteType u8 · 2 doctrinalBasisHash b32 · 3 scopeHash b32
;;  4 effectiveDate u64 · 5 expiryDate u64 · 6 declarer addr · 7 issuerDidHash b32
;;  8 status u8 · 9 ratificationHash b32 · 10 ratifierCount u16 · 11 declaredAt u64
;;  12 ratifiedAt u64 · 13 completedOrSupersededAt u64 · 14 supersededByRiteId b32

(defn- ts->iso [^long ts]
  (if (zero? ts) "" (.toString (java.time.Instant/ofEpochSecond ts))))

(defn- normalize-id ^bytes [rite-id]
  (if (and (string? rite-id) (str/starts-with? rite-id "0x") (= (count rite-id) 66))
    (eth/hex->bytes rite-id)
    (eth/keccak256 (eth/utf8 (str rite-id)))))

(defn rite-from-words
  "Pure decode of the 15-word `rites(bytes32)` ABI return → yobel.ports/Rite (or
  nil when uninitialized / Status.Unknown). Exposed for unit tests."
  [w]
  (when (and (seq w) (>= (count w) 15))
    (let [status-idx (rpc/dec-int (nth w 8))]
      (when-not (zero? status-idx)                      ; Status.Unknown(0) = uninitialized
        (let [expiry (.longValue (rpc/dec-uint (nth w 5)))]
          (ports/->Rite
           (rpc/dec-bytes32-hex (nth w 0))               ; rite-id
           (nth RITE-TYPE-BY-INDEX (rpc/dec-int (nth w 1)))   ; rite-type
           (nth STATUS-BY-INDEX status-idx)              ; status
           (ts->iso (.longValue (rpc/dec-uint (nth w 4))))   ; effective-date
           (when (pos? expiry) (ts->iso expiry))         ; expiry-date
           (str "sha256:" (rpc/dec-bytes32-hex (nth w 3)))   ; scope
           []                                            ; scope-jurisdictions
           (str "hash:" (rpc/dec-bytes32-hex (nth w 7)))     ; issuer-did
           (str "sha256:" (rpc/dec-bytes32-hex (nth w 2)))))))))  ; doctrinal-basis

(defrecord Web3RiteRegistryPort [rpc-url address]
  ports/RiteRegistryPort
  (get-rite [_ rite-id]
    (let [key-hex (eth/bytes->hex (normalize-id rite-id))
          data (rpc/call-data "rites(bytes32)" key-hex)
          result (rpc/eth-call rpc-url address data)]
      (rite-from-words (rpc/words result)))))

(defn make-web3-rite-registry-port [rpc-url address]
  (->Web3RiteRegistryPort rpc-url (eth/eip55-checksum address)))
