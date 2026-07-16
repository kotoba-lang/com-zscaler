(ns shionome.methods.kotoba
  "kotoba.cljc — 潮目 (shionome) kotoba Datom-log writer. ADR-2606072200 + ADR-2605312345.
  1:1 Clojure port of methods/kotoba.py.

  Canonical state is the kotoba Datom log — content-addressed EAVT assertions, append-only
  (G10 / 非終末論). At R0 the log materializes as an append-only EDN transaction file; each
  transaction is content-addressed (sha256 over its canonical datoms + the previous tx's CID
  → a commit-DAG). Mirrors the proven meisai content-addressed log.

    graph-datoms  → EAVT assertions for every validated bucket / flow / snapshot
    post-datoms   → EAVT assertions for each dry-run social post
    make-tx       → a content-addressed transaction (links to prev CID)
    append-tx     → append ONE transaction line to the append-only log (never rewrites)
    read-log / head-cid / verify-chain — read back + verify the content-addressed DAG

  op is :db/add only (append-only — no :db/retract, non-eschatological). read-log parses each
  line with the shared shionome EDN reader, so a post body with newlines round-trips through
  write→read (the reader's JSON-faithful unescape closes the DAG). SHA-256 via
  java.security.MessageDigest; file I/O via slurp/spit. Deterministic (caller supplies tx_id +
  as-of; no wall clock)."
  (:require [clojure.string :as str]
            [shionome.methods.edn :as sedn]
            #?(:clj [clojure.java.io :as io]))
  #?(:clj (:import [java.security MessageDigest])))

(defn- add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(defn graph-datoms
  "Flatten a woven graph into append-only EAVT assertions (buckets, flows, snapshots)."
  [g]
  (vec (concat
        (for [[bid b] (get g "buckets") [a v] b] (add bid a v))
        (for [f (get g "flows") [a v] f] (add (get f ":flow/id") a v))
        (for [s (get g "snapshots") [a v] s] (add (get s ":snap/id") a v)))))

(defn post-datoms
  "Flatten dry-run social posts into append-only EAVT assertions (G8: status stays dry-run)."
  ([posts] (post-datoms posts "post"))
  ([posts prefix]
   (vec (mapcat (fn [i p]
                  (let [pid (str prefix "-" (get p ":post/subject" i))]
                    (for [[a v] p] (add pid a v))))
                (range) posts))))

;; ── content-addressing (internal-consistency CID; sha256 over canonical bytes) ──

(defn- json-escape [s]
  (-> s
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\b" "\\b")
      (str/replace "\f" "\\f")
      (str/replace "\n" "\\n")
      (str/replace "\r" "\\r")
      (str/replace "\t" "\\t")))

(defn- json-str [s] (str "\"" (json-escape s) "\""))

(defn- json-val [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (number? v) (str v)
    (string? v) (json-str v)
    (sequential? v) (str "[" (str/join "," (map json-val v)) "]")
    :else (json-str (str v))))

(defn- canonical
  "Canonical bytes for content addressing: stable JSON of (prev, datoms) with sorted keys
  ('datoms' < 'prev') and tight separators — mirror of the Python _canonical."
  [datoms prev-cid]
  (str "{\"datoms\":["
       (str/join "," (map (fn [d] (str "[" (str/join "," (map json-val d)) "]")) datoms))
       "],\"prev\":" (json-str prev-cid) "}"))

(defn tx-cid
  "Content address of a transaction = 'b' + sha256 over (prev-cid, datoms) → a commit-DAG CID."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid]
   #?(:clj
      (let [md (MessageDigest/getInstance "SHA-256")]
        (.update md (.getBytes (canonical datoms prev-cid) "UTF-8"))
        (str "b" (apply str (map #(format "%02x" (bit-and % 0xFF)) (.digest md)))))
      :cljs (throw (ex-info "tx-cid requires SHA-256 on the JVM" {})))))

(defn make-tx
  "Build a content-addressed transaction. tx-id + as-of are caller-supplied (no wall clock —
  keeps the log deterministic + resume-safe)."
  [datoms {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {":tx/id" tx-id
   ":tx/as-of" as-of
   ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid)
   ":tx/count" (count datoms)
   ":tx/datoms" datoms})

;; ── EDN log line serialization (the kotoba ingest body shape) ────────────────

(defn- edn-val [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (number? v) (str v)
    (string? v) (if (str/starts-with? v ":") v (json-str v))   ; keyword kept bare
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (json-str (str v))))

(defn- tx->edn [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of")
         " :tx/prev " (json-str (get tx ":tx/prev"))
         " :tx/cid " (json-str (get tx ":tx/cid"))
         " :tx/count " (get tx ":tx/count")
         " :tx/datoms [" datoms "]}")))

#?(:clj
   (defn append-tx
     "Append ONE transaction to the append-only log (never rewrites existing lines). Returns
     the tx CID. This is the only mutation: the log only ever grows (G10 / 非終末論)."
     [tx log-path]
     (let [f (io/file (str log-path))]
       (io/make-parents f)
       (when-not (.exists f)
         (spit f (str ";; shionome kotoba Datom log — append-only EAVT transactions "
                      "(content-addressed DAG). DO NOT hand-edit. ADR-2606072200.\n")))
       (spit f (str (tx->edn tx) "\n") :append true)
       (get tx ":tx/cid"))))

#?(:clj
   (defn read-log
     "Read the log back as a list of transaction maps (uses the shared EDN reader)."
     [log-path]
     (let [f (io/file (str log-path))]
       (if-not (.exists f)
         []
         (->> (str/split-lines (slurp f))
              (map str/trim)
              (remove (fn [l] (or (str/blank? l) (str/starts-with? l ";"))))
              (mapv sedn/parse-edn))))))

#?(:clj
   (defn head-cid
     "The content-addressed HEAD = the last transaction's CID."
     [log-path]
     (let [txs (read-log log-path)]
       (if (seq txs) (get (peek txs) ":tx/cid") ""))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from its datoms + prev and verify the DAG is intact (no tampering).
     Returns {ok length broken_at}."
     [log-path]
     (let [txs (read-log log-path)]
       (loop [i 0, prev ""]
         (if (>= i (count txs))
           {"ok" true "length" (count txs) "broken_at" -1}
           (let [tx (nth txs i)
                 expect (tx-cid (get tx ":tx/datoms" []) prev)]
             (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
               {"ok" false "length" (count txs) "broken_at" i}
               (recur (inc i) (get tx ":tx/cid")))))))))
