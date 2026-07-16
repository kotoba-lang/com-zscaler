(ns yobel.concrete-ports.web3-rpc
  "Minimal Ethereum JSON-RPC + ABI codec for yobel's registry adapters
  (cljc port; ADR-2605201800). babashka.http-client POSTs eth_call (reads) and
  eth_sendTransaction (writes via a node-managed/unlocked account, matching
  web3.py's .transact in the Python ports + anvil integration harness). Only the
  STATIC ABI types the Yobel registries use are supported: bytes32, address,
  uint8/16/64/256, bool — no dynamic head/tail encoding needed."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [eth-crypto.core :as eth]))

;; ─── ABI encode (static words) ───────────────────────────────────────

(defn function-selector
  "First 4 bytes of keccak256(signature), e.g. \"registerDebtCap(bytes32,bytes32,uint256,uint256)\"."
  ^String [^String signature]
  (subs (eth/bytes->hex (eth/keccak256 (eth/utf8 signature))) 0 8))

(defn enc-uint ^String [v]
  (eth/bytes->hex (let [^bytes ba (.toByteArray (biginteger v))
                        n (alength ba) o (byte-array 32)]
                    (cond (= n 32) ba
                          (< n 32) (do (System/arraycopy ba 0 o (- 32 n) n) o)
                          :else (java.util.Arrays/copyOfRange ba (- n 32) n)))))

(defn enc-bytes32 ^String [v]
  (let [^bytes b (if (string? v) (eth/hex->bytes v) v)
        o (byte-array 32)]
    (System/arraycopy b 0 o 0 (min 32 (alength b)))
    (eth/bytes->hex o)))

(defn enc-address ^String [v]
  (let [^bytes b (if (string? v) (eth/hex->bytes v) v)
        o (byte-array 32)]
    (System/arraycopy b 0 o (- 32 (alength b)) (alength b))
    (eth/bytes->hex o)))

(defn call-data
  "selector ++ concatenated 64-hex-char encoded words → 0x-prefixed call data."
  ^String [^String signature & words]
  (str "0x" (function-selector signature) (apply str words)))

;; ─── ABI decode ──────────────────────────────────────────────────────

(defn words
  "Split a 0x return blob into 64-hex-char (32-byte) words."
  [^String hex]
  (let [h (eth/strip0x hex)]
    (mapv #(subs h % (+ % 64)) (range 0 (count h) 64))))

(defn dec-uint ^BigInteger [^String word] (BigInteger. word 16))
(defn dec-int   [^String word] (.intValue (BigInteger. word 16)))
(defn dec-bytes32-hex ^String [^String word] word)                       ; 64 hex chars
(defn dec-address-hex ^String [^String word] (str "0x" (subs word 24)))  ; last 20 bytes

;; ─── JSON-RPC transport ──────────────────────────────────────────────

(def ^:private rpc-id (atom 0))

(defn rpc!
  "POST a single JSON-RPC request; return the parsed :result or throw on :error."
  [rpc-url method params]
  (let [body (json/generate-string {:jsonrpc "2.0" :id (swap! rpc-id inc)
                                    :method method :params params})
        resp (http/post rpc-url {:headers {"content-type" "application/json"} :body body})
        parsed (json/parse-string (:body resp) true)]
    (if-let [err (:error parsed)]
      (throw (ex-info (str "JSON-RPC error: " (:message err)) {:error err :method method}))
      (:result parsed))))

(defn eth-call ^String [rpc-url to data]
  (rpc! rpc-url "eth_call" [{:to to :data data} "latest"]))

(defn send-tx ^String [rpc-url from to data]
  (rpc! rpc-url "eth_sendTransaction" [{:from from :to to :data data}]))

(defn wait-receipt
  "Poll eth_getTransactionReceipt until mined (or timeout); return the receipt map."
  [rpc-url tx-hash & {:keys [timeout-ms poll-ms] :or {timeout-ms 30000 poll-ms 200}}]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [r (rpc! rpc-url "eth_getTransactionReceipt" [tx-hash])]
        (cond
          (some? r) r
          (> (System/currentTimeMillis) deadline)
          (throw (ex-info "tx receipt timeout" {:tx tx-hash}))
          :else (do (Thread/sleep poll-ms) (recur)))))))

;; ─── shared id normalizers (match the Python _to_bytes32 / _did_hash) ──

(defn to-bytes32
  "0x-prefixed 32-byte hex → bytes verbatim; 32-byte array verbatim; else keccak256(utf8 text)."
  ^bytes [v]
  (cond
    (bytes? v) (if (= 32 (alength ^bytes v)) v
                   (throw (ex-info "bytes32 must be 32 bytes" {:len (alength ^bytes v)})))
    (and (string? v) (str/starts-with? v "0x") (= (count v) 66)) (eth/hex->bytes v)
    :else (eth/keccak256 (eth/utf8 (str v)))))

(defn did-hash ^bytes [did] (eth/keccak256 (eth/utf8 (str did))))
