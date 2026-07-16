(ns monosashi.methods.kotoba
  "monosashi 物差し — kotoba Datom-log writer. ADR-2606271800 + 2605312345. Mirrors
  hakoniwa.methods.kotoba.

  Canonical state = the kotoba Datom log: content-addressed EAVT assertions, append-only.
  Each tx is content-addressed (sha256 over canonical datoms + previous tx CID → a commit-DAG),
  so tampering any earlier tx breaks every later CID (verify-chain detects it). EAVT = [op entity
  attribute value]; op is :db/add only. Self-contained sha-256 + canonical-JSON (no third-party
  deps). Deterministic — the caller supplies tx-id + as-of; no wall clock. JVM/IO behind
  #?(:clj …) so the .cljc stays portable."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (def log-default
     (str (System/getProperty "user.dir") "/20-actors/monosashi/data/monosashi.datoms.kotoba.edn")))

(defn- add [entity attr value] [":db/add" entity attr value])

(defn- round6 ^double [^double v]
  (/ (Math/rint (* v 1000000.0)) 1000000.0))

(defn band-datoms
  "A skill-band record → append-only EAVT assertions. NO point datom; :eval/point-asserted false is
  the structural marker (G1), and NO :eval/reward attribute is ever emitted (G3 anti-Goodhart —
  this explicit whitelist IS the firewall). Deterministic attribute order."
  [band]
  (let [eid (str "eval." (:eval/actor band) "." (:eval/baseline band) "." (:eval/as-of band))]
    (vec (concat
          (for [[a v] [[":eval/actor" (:eval/actor band)]
                       [":eval/baseline" (:eval/baseline band)]
                       [":eval/as-of" (:eval/as-of band)]
                       [":eval/n" (:eval/n band)]
                       [":eval/kind" (:eval/kind band)]
                       [":eval/use" (:eval/use band)]
                       [":eval/skilled" (:eval/skilled band)]]
                :when (some? v)]
            (add eid a v))
          (for [[a v] [[":eval/p10" (:eval/p10 band)] [":eval/p50" (:eval/p50 band)]
                       [":eval/p90" (:eval/p90 band)]
                       [":eval/calibration-deviation" (:eval/calibration-deviation band)]
                       [":eval/coherence" (:eval/coherence band)]]
                :when (some? v)]
            (add eid a (round6 (double v))))
          [(add eid ":eval/point-asserted" false)]))))

(defn post-datoms
  "Post records → EAVT. Keys are SORTED before emit so the CID is parity-stable across
  re-implementations (a hash-map's iteration order must not feed content-addressing — N5)."
  [posts]
  (vec (for [[i p] (map-indexed vector posts)
             :let [pid (str "post." (get p ":post/actor-evaluated" i))]
             a (sort (keys p))
             :let [v (get p a)]
             :when (some? v)]
         (add pid a v))))

;; ── sha-256 hex ──────────────────────────────────────────────────────────────────────────────
(defn- sha256-hex ^String [^String s]
  #?(:clj (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
            (apply str (map #(format "%02x" (bit-and % 0xff)) d)))
     :cljs (throw (ex-info "monosashi(cljs): sha256 host binding required" {}))))

;; ── canonical JSON (mirrors json.dumps(ensure_ascii=False, sort_keys=True, separators=(",",":"))) ─
(defn- json-str ^String [^String s]
  (str "\"" (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
                (str/replace "\n" "\\n") (str/replace "\r" "\\r") (str/replace "\t" "\\t")) "\""))

(defn- json-val [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "null"
    (integer? v) (str v)
    (and (number? v) (not (integer? v))) (let [d (double v)]
                                           (if (== d (Math/rint d)) (str (long d) ".0")
                                               (Double/toString d)))
    (string? v) (json-str v)
    (sequential? v) (str "[" (str/join "," (map json-val v)) "]")
    (map? v) (str "{" (str/join "," (map (fn [k] (str (json-str (str k)) ":" (json-val (get v k))))
                                         (sort (map str (keys v))))) "}")
    :else (json-str (str v))))

(defn- canonical [datoms prev-cid]
  (str "{\"datoms\":" (json-val datoms) ",\"prev\":" (json-str prev-cid) "}"))

(defn tx-cid
  "Content address of a transaction = 'b' + sha256 hex over (prev_cid, datoms)."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (sha256-hex (canonical datoms prev-cid)))))

(defn make-tx
  [datoms {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {":tx/id" tx-id
   ":tx/as-of" as-of
   ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid)
   ":tx/count" (count datoms)
   ":tx/datoms" datoms})

#?(:clj
   (defn read-log
     "Read the append-only log file into a vector of tx maps (or [] when missing)."
     ([] (read-log log-default))
     ([path]
      (if (.exists (io/file path))
        (->> (slurp path) str/split-lines
             (map str/trim) (remove #(or (str/blank? %) (str/starts-with? % ";")))
             (mapv edn/read-string))
        []))))

#?(:clj
   (defn last-tx
     "The final tx map in the log (or nil)."
     ([] (last-tx log-default))
     ([path] (last (read-log path)))))

#?(:clj
   (defn last-cid
     "The :tx/cid of the final tx (or \"\" when none)."
     ([] (last-cid log-default))
     ([path] (or (some-> (last-tx path) (get ":tx/cid")) ""))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from (datoms, prev) and verify the commit-DAG is intact (tamper-evident).
     Returns {\"ok\" b \"length\" n \"broken_at\" i} (broken_at -1 when ok)."
     ([] (verify-chain log-default))
     ([path]
      (let [txs (read-log path)]
        (loop [i 0, prev "", ts (seq txs)]
          (if-not ts
            {"ok" true "length" (count txs) "broken_at" -1}
            (let [tx (first ts)
                  expect (tx-cid (get tx ":tx/datoms" []) prev)]
              (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
                {"ok" false "length" (count txs) "broken_at" i}
                (recur (inc i) (get tx ":tx/cid") (next ts))))))))))

#?(:clj
   (defn append-tx!
     "Append one tx (EDN line) to the append-only log file. Returns the tx."
     [path tx]
     (io/make-parents path)
     (spit path (str (pr-str tx) "\n") :append true)
     tx))
