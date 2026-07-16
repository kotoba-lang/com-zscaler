;; ported from 20-actors/hakoniwa/methods/kotoba.py — real port replacing the unit_refactor
;; stage-0 "TODO: port-failed" stubs. NS fixed root.hakoniwa.* → hakoniwa.* (20-actors source root).
(ns hakoniwa.methods.kotoba
  "kotoba.py — hakoniwa 箱庭 kotoba Datom-log writer. 1:1 Clojure port. ADR-2606111500 + 2605312345.

  Canonical state = the kotoba Datom log: content-addressed EAVT assertions, append-only (非終末論).
  The log materialises as an append-only EDN transaction file; each tx is content-addressed
  (sha256 over its canonical datoms + the previous tx CID → a commit-DAG), so tampering any earlier
  tx breaks every later CID. EAVT = [op entity attribute value]; op is :db/add only.

  Self-contained: own sha-256 + canonical-JSON + EDN reader (no third-party deps). File I/O behind
  #?(:clj ...). Deterministic — the caller supplies tx_id + as-of; no wall clock."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [hakoniwa.methods.world :as w]))

#?(:clj
   (def log-default
     (str (System/getProperty "user.dir") "/20-actors/hakoniwa/data/hakoniwa.datoms.kotoba.edn")))

(defn- add [entity attr value] [":db/add" entity attr value])

(defn world-datoms
  "Flatten the box into append-only EAVT assertions (nodes, 縁, run config). Mirrors world_datoms."
  [nodes edges meta]
  (let [node-d (for [[nid n] nodes
                     [a v] n
                     :when (and (not= a ":sim/id") (some? v))]
                 (add nid a v))
        edge-d (for [e edges
                     :let [eid (str "en." (get e ":en/from") "."
                                    (str/replace (get e ":en/kind") #"^:+" "") "."
                                    (get e ":en/to"))]
                     [a v] e
                     :when (some? v)]
                 (add eid a v))
        run "run.hakoniwa"
        run-d (for [[a v] [[":run/steps" (get meta "steps")] [":run/replicas" (get meta "replicas")]
                           [":run/seed" (get meta "seed")] [":run/jitter" (get meta "jitter")]
                           [":run/kernel" ":friedkin-johnsen"]]
                    :when (some? v)]
                (add run a v))]
    (vec (concat node-d edge-d run-d))))

(defn- round6 ^double [^double v]
  (/ (Math/rint (* v 1000000.0)) 1000000.0))

(defn distribution-datoms
  "The outcome DISTRIBUTION as append-only EAVT — quantiles + mean/stdev. NO point datom;
  :forecast/point-asserted false is the structural marker (G2). Mirrors distribution_datoms."
  ([dist] (distribution-datoms dist "outcome.adoption"))
  ([dist outcome]
   (let [q (get dist "quantiles")]
     (vec (concat
           (for [[qk qv] q]
             (add outcome (str ":forecast/" (str/replace qk #"^:+" "")) (round6 (double qv))))
           [(add outcome ":forecast/mean" (round6 (double (get dist "mean"))))
            (add outcome ":forecast/stdev" (round6 (double (get dist "stdev"))))
            (add outcome ":forecast/kind" ":distribution")
            (add outcome ":forecast/point-asserted" false)])))))

(defn post-datoms
  ([posts] (post-datoms posts "post"))
  ([posts prefix]
   (vec (for [[i p] (map-indexed vector posts)
              :let [pid (str prefix "-" (get p ":post/subject" i))]
              [a v] p
              :when (some? v)]
          (add pid a v)))))

;; ── sha-256 hex ──────────────────────────────────────────────────────────────────────────────
(defn- sha256-hex ^String [^String s]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) d))))

;; ── canonical JSON (mirrors json.dumps(ensure_ascii=False, sort_keys=True, separators=(",",":"))) ─
(defn- json-str ^String [^String s]
  (str "\"" (-> s
                (str/replace "\\" "\\\\")
                (str/replace "\"" "\\\"")
                (str/replace "\n" "\\n")
                (str/replace "\r" "\\r")
                (str/replace "\t" "\\t"))
       "\""))

(defn- json-val [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "null"
    (integer? v) (str v)
    (and (number? v) (not (integer? v))) (let [d (double v)]
                                           (if (== d (Math/rint d))
                                             ;; Python json renders an integral float as e.g. 1.0
                                             (str (long d) ".0")
                                             (Double/toString d)))
    (string? v) (json-str v)
    (sequential? v) (str "[" (str/join "," (map json-val v)) "]")
    (map? v) (str "{" (str/join "," (map (fn [k] (str (json-str (str k)) ":" (json-val (get v k))))
                                         (sort (map str (keys v))))) "}")
    :else (json-str (str v))))

(defn- canonical [datoms prev-cid]
  ;; keys "datoms" < "prev" sorted → emit in sorted order
  (str "{\"datoms\":" (json-val datoms) ",\"prev\":" (json-str prev-cid) "}"))

(defn tx-cid
  "Content address of a transaction = 'b' + sha256 hex over (prev_cid, datoms)."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid]
   (str "b" (sha256-hex (canonical datoms prev-cid)))))

(defn make-tx
  [datoms {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {":tx/id" tx-id
   ":tx/as-of" as-of
   ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid)
   ":tx/count" (count datoms)
   ":tx/datoms" datoms})

;; ── EDN value writer (mirrors _edn_val: ints/floats via repr, strings quoted, :kw raw) ──────────
(defn- edn-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (and (number? v) (not (integer? v))) (Double/toString (double v))
    (and (string? v) (str/starts-with? v ":")) v
    (string? v) (json-str v)
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (json-str (str v))))

(defn- tx->edn [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]")) (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of") " "
         ":tx/prev " (json-str (get tx ":tx/prev")) " :tx/cid " (json-str (get tx ":tx/cid")) " "
         ":tx/count " (get tx ":tx/count") " :tx/datoms [" datoms "]}")))

#?(:clj
   (defn append-tx
     "Append ONE transaction to the append-only log (never rewrites). Returns the tx CID."
     ([tx] (append-tx tx log-default))
     ([tx log-path]
      (let [f (io/file (str log-path))]
        (io/make-parents f)
        (when-not (.exists f)
          (spit f (str ";; hakoniwa 箱庭 kotoba Datom log — append-only EAVT transactions "
                       "(content-addressed DAG). DO NOT hand-edit. ADR-2606111500.\n")))
        (spit f (str (tx->edn tx) "\n") :append true)
        (get tx ":tx/cid")))))

;; ── log reader (mirrors kotoba.py's OWN _read_tokens/_atom/_parse — _atom uses json.loads for
;;    quoted strings, so escaped \n/\t round-trip back to real chars, unlike world's reader) ─────
(defn- unescape-json-string [^String t]
  ;; t includes surrounding quotes
  (let [s (subs t 1 (dec (count t)))
        sb (StringBuilder.)]
    (loop [i 0]
      (if (>= i (count s))
        (.toString sb)
        (let [c (nth s i)]
          (if (= c \\)
            (let [e (nth s (inc i))]
              (case e
                \" (do (.append sb \") (recur (+ i 2)))
                \\ (do (.append sb \\) (recur (+ i 2)))
                \/ (do (.append sb \/) (recur (+ i 2)))
                \b (do (.append sb \backspace) (recur (+ i 2)))
                \f (do (.append sb \formfeed) (recur (+ i 2)))
                \n (do (.append sb \newline) (recur (+ i 2)))
                \r (do (.append sb \return) (recur (+ i 2)))
                \t (do (.append sb \tab) (recur (+ i 2)))
                \u (do (.append sb (char (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16))) (recur (+ i 6)))
                (do (.append sb e) (recur (+ i 2)))))
            (do (.append sb c) (recur (inc i)))))))))

(defn- log-atom [^String t]
  (cond
    (str/starts-with? t "\"") (unescape-json-string t)
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else (or (try (Long/parseLong t) (catch Exception _ nil))
              (try (Double/parseDouble t) (catch Exception _ nil))
              t)))

(declare log-parse-one)
(defn- log-parse-one [toks i]
  (let [t (nth toks i)]
    (cond
      (= t "[") (let [[v ni] (loop [i (inc i), acc []]
                               (let [[x ni done?] (log-parse-one toks i)]
                                 (if done? [acc ni] (recur ni (conj acc x)))))]
                  [v ni nil])
      (= t "{") (let [[v ni] (loop [i (inc i), acc {}]
                               (let [[k ni done?] (log-parse-one toks i)]
                                 (if done? [acc ni]
                                     (let [[val ni2 _] (log-parse-one toks ni)]
                                       (recur ni2 (assoc acc k val))))))]
                  [v ni nil])
      (or (= t "]") (= t "}")) [nil (inc i) t]
      :else [(log-atom t) (inc i) nil])))

(defn- read-tx-line [line]
  (first (log-parse-one (w/tokens line) 0)))

#?(:clj
   (defn read-log
     ([] (read-log log-default))
     ([log-path]
      (let [f (io/file (str log-path))]
        (if-not (.exists f)
          []
          (->> (str/split-lines (slurp f))
               (map str/trim)
               (remove (fn [l] (or (= "" l) (str/starts-with? l ";"))))
               (mapv read-tx-line)))))))

#?(:clj
   (defn head-cid
     ([] (head-cid log-default))
     ([log-path]
      (let [txs (read-log log-path)]
        (if (seq txs) (get (last txs) ":tx/cid") "")))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from its datoms + prev and verify the DAG is intact.
     Returns {\"ok\" b \"length\" n \"broken_at\" i}."
     ([] (verify-chain log-default))
     ([log-path]
      (let [txs (read-log log-path)]
        (loop [i 0, prev "", txs (seq txs)]
          (if-not txs
            {"ok" true "length" (count (read-log log-path)) "broken_at" -1}
            (let [tx (first txs)
                  expect (tx-cid (get tx ":tx/datoms" []) prev)]
              (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
                {"ok" false "length" (count (read-log log-path)) "broken_at" i}
                (recur (inc i) (get tx ":tx/cid") (next txs))))))))))
