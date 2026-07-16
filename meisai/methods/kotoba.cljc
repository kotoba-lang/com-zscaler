(ns meisai.methods.kotoba
  "kotoba.cljc — meisai 明細 kotoba Datom-log writer (local, content-addressed).
  1:1 Clojure port of `methods/kotoba.py` (ADR-2606122400 + ADR-2605262130 + ADR-2605312345).

  Persists the MEMBER's OWN card statements (利用明細) through the actor-family local autonomous-loop
  write path: a heartbeat appends content-addressed transactions to a local append-only EDN log
  with NO external I/O. MEMBER-OWN data only — the log lives under the gitignored data/ (G3
  local-only); credentials + full card numbers are UNREPRESENTABLE (G2, enforced in ingest).

  BYTE-PARITY (do-not-weaken): tx CID = 'b' + sha256-hex over the Python-canonical JSON
  `json.dumps({\"prev\":…,\"datoms\":…}, sort_keys=True, separators=(',',':'), ensure_ascii=False)`.
  `canonical` reproduces that string exactly, so the Clojure and Python heartbeats build the SAME
  commit-DAG. Keys stay ':…' STRINGS. EAVT = [op entity attribute value]; op is :db/add only.
  Deterministic: caller supplies tx-id + as-of (no wall clock) → resume-safe. Self-contained EDN
  reader (parse-edn), consistent with the Python method (no cross-actor dependency)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(defn add [entity attr value] [":db/add" entity attr value])

;; ── Python-faithful helpers ──────────────────────────────────────────────────
(defn- py-float-str [x] (str (double x)))

(defn- json-escape [s]
  (let [sb (StringBuilder.)]
    (doseq [c (str s)]
      (let [code (int c)]
        (cond
          (= c \") (.append sb "\\\"")
          (= c \\) (.append sb "\\\\")
          (= c \newline) (.append sb "\\n")
          (= c \return) (.append sb "\\r")
          (= c \tab) (.append sb "\\t")
          (= code 8) (.append sb "\\b")
          (= code 12) (.append sb "\\f")
          (< code 0x20) (.append sb (format "\\u%04x" code))
          :else (.append sb c))))
    (str sb)))

(defn- json-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (nil? v) "null"
    (integer? v) (str v)
    (float? v) (py-float-str v)
    (string? v) (str \" (json-escape v) \")
    (sequential? v) (str "[" (str/join "," (map json-val v)) "]")
    :else (str v)))

(defn- canonical [datoms prev-cid]
  (str "{\"datoms\":[" (str/join "," (map json-val datoms)) "],\"prev\":" (json-val prev-cid) "}"))

(defn- sha256-hex [^String s]
  (let [b (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) b))))

(defn tx-cid
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (sha256-hex (canonical datoms prev-cid)))))

(defn make-tx
  [datoms tx-id as-of prev-cid]
  {":tx/id" tx-id ":tx/as-of" as-of ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid) ":tx/count" (count datoms) ":tx/datoms" datoms})

;; ── EDN log serialization (1:1 with _edn_val / _tx_to_edn) ───────────────────
(defn- edn-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (py-float-str v)
    (string? v) (if (str/starts-with? v ":") v (str \" (json-escape v) \"))
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (str v)))

(defn tx->edn [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of")
         " :tx/prev " (str \" (json-escape (get tx ":tx/prev")) \")
         " :tx/cid " (str \" (json-escape (get tx ":tx/cid")) \")
         " :tx/count " (get tx ":tx/count") " :tx/datoms [" datoms "]}")))

;; ── minimal EDN reader (subset), self-contained (1:1 with the Python parse_edn) ──
(def ^:private tok-re #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)] (if (nil? t) (step) (cons t (step))))))))))

(defn- atom-of [t]
  (cond
    (str/starts-with? t "\"") (-> (subs t 1 (dec (count t)))
                                  (str/replace "\\\"" "\"") (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else (let [l (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
            (if (not= l ::nan) l
                (let [d (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
                  (if (not= d ::nan) d t))))))

(def ^:private end-marker ::end)

(defn- parse-step [toks i]
  (let [t (nth toks i) i (inc i)]
    (cond
      (= t "[") (loop [i i out []] (let [[x i] (parse-step toks i)]
                                     (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{") (loop [i i out (vary-meta {} assoc ::order [])]
                  (let [[k i] (parse-step toks i)]
                    (if (= k end-marker) [out i]
                        (let [[v i] (parse-step toks i)]
                          (recur i (vary-meta (assoc out k v) update ::order conj k))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn parse-edn
  "Parse ONE EDN form (map / vector / atom) from a string (1:1 with parse_edn)."
  [s]
  (first (parse-step (vec (tokens s)) 0)))

(defn keys-in-order [m] (or (::order (meta m)) (keys m)))

#?(:clj
   (do
     (def log-default
       (-> (io/file *file*) .getParentFile .getParentFile
           (io/file "data" "persisted" "meisai.datoms.kotoba.edn") str))

     (defn append-tx [tx log-path]
       (let [f (io/file log-path)]
         (when-let [p (.getParentFile f)] (.mkdirs p))
         (when-not (.exists f)
           (spit f (str ";; meisai kotoba Datom log — append-only EAVT transactions "
                        "(content-addressed DAG). MEMBER-OWN card statements only; this file "
                        "lives under the gitignored data/ and is NEVER committed, pinned, or "
                        "published (G3). DO NOT hand-edit. ADR-2606122400.\n")))
         (spit f (str (tx->edn tx) "\n") :append true)
         (get tx ":tx/cid")))

     (defn read-log [log-path]
       (let [f (io/file log-path)]
         (if-not (.exists f) []
                 (->> (str/split-lines (slurp f))
                      (map str/trim)
                      (remove #(or (empty? %) (str/starts-with? % ";")))
                      (mapv parse-edn)))))

     (defn head-cid [log-path]
       (let [txs (read-log log-path)] (if (seq txs) (get (last txs) ":tx/cid") "")))

     (defn verify-chain [log-path]
       (let [txs (read-log log-path) n (count txs)]
         (loop [i 0 prev "" ts txs]
           (if (empty? ts) {:ok true :length n :broken-at -1}
               (let [tx (first ts) expect (tx-cid (get tx ":tx/datoms") prev)]
                 (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
                   {:ok false :length n :broken-at i}
                   (recur (inc i) (get tx ":tx/cid") (rest ts))))))))))
