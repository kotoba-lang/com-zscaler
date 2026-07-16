;; kotoba_bridge.clj — push the LOCAL revenue commit-DAG log into the LIVE kotoba engine.
;; danjo 弾正, ADR-2605301600 + ADR-2606101200 §R3 (mirrors ibuki/methods/kotoba_bridge.py in clj).
;;
;; revenue_ledger.clj persists each cycle to a LOCAL append-only EDN tx log (kotoba-native by
;; design). This module is the missing hop: each local tx becomes one
;; `com.etzhayyim.apps.kotoba.datomic.transact` call against a running kotoba node (the engine of
;; ADR-2605262130 / ADR-2605301625 on :8077), so the revenue Datoms land on the REAL distributed
;; Datom graph (IPFS-backed, IPNS-headed) — not just a file.
;;
;; Durability + honesty (same discipline as ibuki):
;;   - push cursor is a `:bridge/*` checkpoint ON the local log → exactly-once per local tx;
;;   - every pushed tx carries `:danjo.tx/*` provenance meta (local id / CID / prev);
;;   - the previous push's remote commit-cid is sent as `expected_parent` (optimistic
;;     concurrency: a fork on the remote head fails loudly, never silently overwrites);
;;   - host allowlist (loopback + EVO-X2 LAN, ADR-2605215000) — any other endpoint raises
;;     BEFORE I/O; live mode is DANJO_KOTOBA_LIVE=1, default is a DRY-RUN export returning the
;;     exact request bodies with NO I/O;
;;   - no-server-key: live auth is the node's PUBLIC operator DID as an unsigned loopback bearer
;;     (signature verification is the edge/loopback trust boundary's job).
;;
;; Pure-ish + JVM stdlib only (java.net for the live leg); deterministic dry-run.
(ns root.danjo.methods.kotoba-bridge
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]
           [java.net URI]
           [java.io ByteArrayOutputStream]))

(load-file "revenue_ledger.clj")
(alias 'rl 'root.danjo.methods.revenue-ledger)

(def allowed-kotoba-hosts
  #{"127.0.0.1:8077" "localhost:8077" "192.168.1.70:8077"})  ; EVO-X2 fleet (ADR-2605215000)

(def default-endpoint "http://127.0.0.1:8077/xrpc/com.etzhayyim.apps.kotoba.datomic.transact")
(def default-graph "danjo-revenue")
(def live-env "DANJO_KOTOBA_LIVE")
(def operator-did-env "DANJO_KOTOBA_OPERATOR_DID")

(defn assert-kotoba!
  "Raise if `endpoint` is outside the kotoba fleet allowlist (BEFORE any I/O)."
  [endpoint]
  (let [u (URI. endpoint)
        host (str (.getHost u) ":" (.getPort u))]
    (when-not (and (= "http" (.getScheme u)) (contains? allowed-kotoba-hosts host))
      (throw (ex-info (str "kotoba endpoint " endpoint " is outside the fleet allowlist "
                           (sort allowed-kotoba-hosts))
                      {:endpoint endpoint})))))

;; ── kotoba graph CID (CIDv1 + dag-cbor 0x71 + sha2-256 multihash, base32lower 'b') ──
(def ^:private b32-alpha "abcdefghijklmnopqrstuvwxyz234567")  ; RFC4648 base32, lowercased

(defn- base32 [^bytes bs]
  "RFC4648 base32, lowercase, no padding (multibase 'b' body)."
  (let [sb (StringBuilder.)]
    (loop [bits 0 nbits 0 i 0]
      (cond
        (>= nbits 5)
        (do (.append sb (.charAt b32-alpha (bit-and (unsigned-bit-shift-right bits (- nbits 5)) 0x1f)))
            (recur bits (- nbits 5) i))
        (< i (alength bs))
        (recur (bit-or (bit-shift-left bits 8) (bit-and (aget bs i) 0xff)) (+ nbits 8) (inc i))
        (pos? nbits)   ; flush remaining bits, left-padded with zeros
        (.append sb (.charAt b32-alpha (bit-and (bit-shift-left bits (- 5 nbits)) 0x1f)))))
    (.toString sb)))

(defn graph-cid
  "KotobaCid::from_bytes(name): CIDv1 + dag-cbor(0x71) + sha2-256 over the raw name bytes,
   multibase base32lower ('b'). Mirrors kotoba-core cid.rs (same shape ibuki uses)."
  [name]
  (let [md (MessageDigest/getInstance "SHA-256")
        digest (.digest md (.getBytes ^String name "UTF-8"))
        raw (byte-array (concat [(byte 0x01) (byte 0x71) (byte 0x12) (byte 0x20)]
                                (seq digest)))]
    (str "b" (base32 raw))))

;; ── tx → transact body ──
(defn- edn-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (number? v)  (pr-str v)
    (keyword? v) (str v)
    (vector? v)  (str "[" (str/join " " (map edn-val v)) "]")
    :else        (pr-str (str v))))

(defn tx->edn-vec
  "One local tx → the `tx_edn` string the transact lexicon takes: [:db/add e a v] forms +
   `:danjo.tx/*` provenance meta (local id / CID / prev / as-of)."
  [tx]
  (let [meta-e (str "danjo-tx-" (:tx/id tx))
        forms  (concat (:tx/datoms tx)
                       [[:db/add meta-e :danjo.tx/id (:tx/id tx)]
                        [:db/add meta-e :danjo.tx/local-cid (:tx/cid tx)]
                        [:db/add meta-e :danjo.tx/local-prev (:tx/prev tx)]
                        [:db/add meta-e :danjo.tx/as-of (:tx/as-of tx)]])]
    (str "[" (str/join " "
              (map (fn [[op e a v]]
                     (str "[" (str op) " " (pr-str (str e)) " " (str a) " " (edn-val v) "]"))
                   forms)) "]")))

(defn parse-resp
  "Extract the fields the transact endpoint echoes back. The engine returns JSON, so pull the
   flat fields by regex (dep-free): {:status :tx-cid :commit-cid :datom-count}."
  [s]
  (let [g (fn [re] (second (re-find re (or s ""))))
        n (fn [re] (some-> (g re) Long/parseLong))]
    {:status      (g #"\"status\"\s*:\s*\"([^\"]*)\"")
     :tx-cid      (or (g #"\"tx_cid\"\s*:\s*\"([^\"]*)\"") "")
     :commit-cid  (or (g #"\"commit_cid\"\s*:\s*\"([^\"]*)\"") "")
     :datom-count (or (n #"\"datom_count\"\s*:\s*(\d+)") 0)}))

(defn operator-bearer
  "Unsigned loopback bearer whose `sub` is the node's PUBLIC operator DID (env, not a secret).
   No key material is held or read. Raises if the DID env is unset."
  []
  (let [did (System/getenv operator-did-env)]
    (when (str/blank? did)
      (throw (ex-info (str "live push requires " operator-did-env
                           " (the node's PUBLIC operator DID)") {})))
    (let [b64 (fn [^String s] (-> (.encodeToString (java.util.Base64/getUrlEncoder) (.getBytes s "UTF-8"))
                                  (str/replace #"=+$" "")))]
      (str (b64 "{\"alg\":\"none\"}") "." (b64 (str "{\"sub\":\"" did "\"}")) ".unsigned-loopback"))))

;; ── push cursor (replayed from the local log's :bridge/* checkpoints) ──
(defn bridge-state
  "Replay the durable push cursor: the LAST :bridge/* checkpoint wins. {:pushed-to :parent-commit}."
  [txs]
  (reduce (fn [st tx]
            (reduce (fn [st [_ _ a v]]
                      (case a
                        :bridge/pushed-to-tx (assoc st :pushed-to v)
                        :bridge/parent-commit (assoc st :parent-commit v)
                        st))
                    st (:tx/datoms tx)))
          {:pushed-to 0 :parent-commit ""} txs))

(defn- http-post [endpoint body operator-auth?]
  (assert-kotoba! endpoint)
  (let [conn (.openConnection (.toURL (URI. endpoint)))
        payload (.getBytes ^String body "UTF-8")]
    (doto conn
      (.setRequestMethod "POST")
      (.setDoOutput true)
      (.setConnectTimeout 60000) (.setReadTimeout 60000)
      (.setRequestProperty "Content-Type" "application/json"))
    (when operator-auth?
      (.setRequestProperty conn "Authorization" (str "Bearer " (operator-bearer))))
    (with-open [os (.getOutputStream conn)] (.write os payload))
    (let [code (.getResponseCode conn)
          in (if (>= code 400) (.getErrorStream conn) (.getInputStream conn))
          baos (ByteArrayOutputStream.)]
      (when in (io/copy in baos))
      (let [resp (.toString baos "UTF-8")]
        (when (>= code 400)
          (throw (ex-info (str "kotoba transact HTTP " code ": " (subs resp 0 (min 200 (count resp)))) {})))
        resp))))

(defn push
  "Push every local tx the cursor has not yet sent (oldest first, one transact per tx). Live
   mode requires DANJO_KOTOBA_LIVE=1 (or :live true); otherwise DRY-RUN export (NO I/O). After a
   live push, ONE :bridge/* checkpoint tx is appended (exactly-once). Returns a summary map.

   `:transport` (fn [endpoint body-str operator-auth?] → response-str) is injectable for tests;
   when given, the push runs live without touching the network or the env."
  [{:keys [log-path graph endpoint live transport]
    :or {log-path rl/log-default graph default-graph endpoint default-endpoint}}]
  (assert-kotoba! endpoint)
  (let [graph-id (if (and (str/starts-with? graph "b") (> (count graph) 40)) graph (graph-cid graph))
        txs      (rl/read-log log-path)
        state    (bridge-state txs)
        pending  (filter #(and (rl/data-tx? %) (> (:tx/id %) (:pushed-to state))) txs)
        bodies   (map (fn [tx] {:graph graph-id :tx_edn (tx->edn-vec tx)}) pending)
        is-live  (or (true? live) (and (nil? live) (= "1" (System/getenv live-env))))]
    (if (and (not is-live) (nil? transport))
      {:mode :dry-run :pending (count bodies) :bodies (vec bodies)
       :graph graph-id :pushed-to (:pushed-to state)}
      ;; live (or injected transport)
      (let [t (or transport (fn [ep b oa?] (http-post ep b oa?)))]
        (loop [ps pending, parent (:parent-commit state), confirmed 0, remote []]
          (if (empty? ps)
            (let [last-id (if (seq pending) (apply max (map :tx/id pending)) (:pushed-to state))
                  ck-id   (inc (apply max 0 (map :tx/id txs)))
                  ck-datoms [[:db/add (str "bridge-" ck-id) :bridge/pushed-to-tx last-id]
                             [:db/add (str "bridge-" ck-id) :bridge/parent-commit parent]]
                  ck-tx   (rl/make-tx ck-datoms {:tx-id ck-id :as-of 0 :prev-cid (rl/head-cid log-path)})]
              (when (seq pending) (rl/append-tx! ck-tx log-path))
              {:mode :live :pushed (count pending) :datoms-confirmed confirmed
               :remote-cids remote :parent-commit parent :pushed-to last-id})
            (let [tx   (first ps)
                  body-str (str "{\"graph\":" (pr-str graph-id)
                                (when (seq parent) (str ",\"expected_parent\":" (pr-str parent)))
                                ",\"tx_edn\":" (pr-str (tx->edn-vec tx)) "}")
                  out  (t endpoint body-str true)
                  m    (parse-resp out)]
              (when-not (contains? #{"ok" "committed" "success" nil} (:status m))
                (throw (ex-info (str "kotoba transact refused tx " (:tx/id tx) ": " out) {})))
              (recur (rest ps) (:commit-cid m)
                     (+ confirmed (:datom-count m))
                     (conj remote (:tx-cid m))))))))))

(defn -main [& args]
  (let [r (push {:log-path (or (first args) rl/log-default)})]
    (println "mode:" (:mode r) " pending:" (:pending r) " graph:" (:graph r))
    (doseq [b (:bodies r)] (println "  body:" (pr-str b)))))
