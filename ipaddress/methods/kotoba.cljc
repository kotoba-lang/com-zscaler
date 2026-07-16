(ns ipaddress.methods.kotoba
  "kotoba.py — ipaddress kotoba Datom-log writer (local, content-addressed).
  1:1 Clojure port of `methods/kotoba.py` (ADR-2605301400 §T2 + 2605262130 + 2605312345).

  The local, autonomous-loop write path: a self-driving heartbeat appends content-addressed
  transactions to a local append-only EDN log with NO external I/O.

    - graph-datoms(rows)         → EAVT assertions for every entity in the merged IP/ASN graph
    - derived-datoms(concentr.)  → EAVT assertions for the analyzer's :ipnet/* concentration
    - make-tx(...)               → a content-addressed transaction (links prev CID → commit-DAG)
    - append-tx(...)             → append ONE transaction line to the append-only log
    - read-log / head-cid / verify-chain — read back + verify the content-addressed DAG

  EAVT = [op entity attribute value]; op is :db/add only (append-only). Deterministic:
  the caller supplies tx_id + as_of (no wall clock) so a re-run reproduces the same CIDs.

  House style: ':…' keyword strings stay strings; pure fns; file/sha I/O behind #?(:clj …).
  SELF-CONTAINED: inlines sha-256 + canonical JSON (no external deps). The Python __main__
  demo is omitted. NOTE: NO `socket`/`urllib`/`http`/`subprocess`/`requests` here — the
  no-external-I/O test scans this source string."
  (:require [clojure.string :as str]
            [ipaddress.methods.analyze :as analyze]
            [ipaddress.methods.ip-edn :as ip-edn]))

;; entity-id keys, in priority order (same set transact uses for E selection)
(def id-keys
  [":rir/id" ":asn/id" ":iprange/id" ":ip/id" ":net.announce/id"
   ":net.member/id" ":geo/id" ":rdns/id" ":whois/id"])

(def ^:private id-keys-set (set id-keys))

(defn- add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(defn graph-datoms
  "Flatten the merged IP/ASN graph (list of entity maps) into append-only EAVT assertions.
  E = the entity's id; cardinality-many list values fan out into one datom each."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some (fn [k] (when (contains? r k) (get r k))) id-keys)]
         (if (nil? e)
           out
           (reduce
            (fn [out [k v]]
              (if (contains? id-keys-set k)
                out
                (reduce (fn [out item] (conj out (add e k item)))
                        out
                        (if (sequential? v) v [v]))))
            out
            (ip-edn/ordered-items r))))))
   []
   rows))

(defn derived-datoms
  "Flatten the analyzer's derived :ipnet/* concentration metrics into EAVT assertions, each
  flagged :ipnet/derived true. `concentration` is the map returned by analyze/analyze."
  ([concentration] (derived-datoms concentration "ipnet"))
  ([concentration prefix]
   (let [a concentration
         lstrip (fn [s] (str/replace (str s) #"^:+" ""))
         sorted-desc (fn [m] (sort-by (fn [[_ v]] (- v)) (analyze/ordered-items m)))
         out (transient [])]
     ;; rir_addr
     (doseq [[rir addr] (sorted-desc (get a "rir_addr"))]
       (let [e (str prefix "-rir-" (lstrip rir))]
         (conj! out (add e ":ipnet/rir" rir))
         (conj! out (add e ":ipnet/ranges" (get-in a ["rir_ranges" rir] 0)))
         (conj! out (add e ":ipnet/addresses" addr))
         (conj! out (add e ":ipnet/derived" true))))
     ;; asn_prefix
     (doseq [[aid name pref cls _cc] (get a "asn_prefix")]
       (let [e (str prefix "-asn-" (lstrip aid))]
         (conj! out (add e ":ipnet/asn-prefix-load" aid))
         (conj! out (add e ":ipnet/asn-name" name))
         (conj! out (add e ":ipnet/prefixes" pref))
         (conj! out (add e ":ipnet/hosting-class" cls))
         (conj! out (add e ":ipnet/derived" true))))
     ;; hosting_addr
     (doseq [[cls addr] (sorted-desc (get a "hosting_addr"))]
       (let [e (str prefix "-hclass-" (lstrip cls))]
         (conj! out (add e ":ipnet/hosting-class-load" cls))
         (conj! out (add e ":ipnet/addresses" addr))
         (conj! out (add e ":ipnet/derived" true))))
     ;; country_addr
     (doseq [[cc addr] (sorted-desc (get a "country_addr"))]
       (let [e (str prefix "-cc-" cc)]
         (conj! out (add e ":ipnet/country-load" cc))
         (conj! out (add e ":ipnet/addresses" addr))
         (conj! out (add e ":ipnet/derived" true))))
     ;; hhi
     (let [e (str prefix "-hhi")]
       (conj! out (add e ":ipnet/space-hhi" (get a "space_hhi")))
       (conj! out (add e ":ipnet/prefix-hhi" (get a "prefix_hhi")))
       (conj! out (add e ":ipnet/v4-ranges" (get a "v4")))
       (conj! out (add e ":ipnet/v6-ranges" (get a "v6")))
       (conj! out (add e ":ipnet/derived" true)))
     (persistent! out))))

;; ── canonical JSON (json.dumps(ensure_ascii=False, sort_keys=True, separators=(",",":"))) ──
(defn- json-escape ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

;; number → JSON exactly as Python json.dumps renders it (int → "n", float → repr).
;; All values flowing into datoms from the EDN reader are strings/longs/doubles/bools/nil.
(defn- json-num [x]
  (cond
    (integer? x) (str x)
    (and (number? x) (== (double x) (Math/floor (double x)))
         (not (Double/isInfinite (double x))))
    (str (long x) ".0")
    :else (str x)))

(defn- canonical-json ^String [v]
  (cond
    (string? v)  (str "\"" (json-escape v) "\"")
    (boolean? v) (if v "true" "false")
    (nil? v)     "null"
    (number? v)  (json-num v)
    (map? v)     (str "{" (str/join "," (map (fn [k] (str "\"" (json-escape (str k)) "\":"
                                                          (canonical-json (get v k))))
                                             (sort (map str (keys v))))) "}")
    (sequential? v) (str "[" (str/join "," (map canonical-json v)) "]")
    :else (throw (ex-info "canonical-json: unsupported value" {:value v}))))

(defn- canonical
  "Canonical string for content addressing: stable JSON of {prev, datoms}."
  [datoms prev-cid]
  (canonical-json {"prev" prev-cid "datoms" datoms}))

(defn- sha256-hex
  ^String [^String s]
  #?(:clj (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
            (apply str (map #(format "%02x" (bit-and % 0xff)) d)))
     :default (throw (ex-info "bind a sha-256 impl on this host" {}))))

(defn tx-cid
  "Content address of a transaction = 'b' + sha256 over (prev_cid, datoms)."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (sha256-hex (canonical datoms prev-cid)))))

(defn make-tx
  "Build a content-addressed transaction. tx-id + as-of supplied by the caller (no wall clock)."
  [datoms {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {":tx/id" tx-id
   ":tx/as-of" as-of
   ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid)
   ":tx/count" (count datoms)
   ":tx/datoms" datoms})

(defn- tx-to-edn
  "Serialize one transaction as a single-line EDN map (the kotoba ingest body shape)."
  [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map ip-edn/edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of") " "
         ":tx/prev " (ip-edn/edn-val (get tx ":tx/prev")) " :tx/cid " (ip-edn/edn-val (get tx ":tx/cid")) " "
         ":tx/count " (get tx ":tx/count") " :tx/datoms [" datoms "]}")))

#?(:clj
   (defn append-tx
     "Append ONE transaction to the append-only log (never rewrites existing lines).
     Returns the tx CID. The only mutation: the log only ever grows."
     [tx log-path]
     (let [f (clojure.java.io/file (str log-path))]
       (.mkdirs (.getParentFile (.getAbsoluteFile f)))
       (when-not (.exists f)
         (spit f (str ";; ipaddress kotoba Datom log — append-only EAVT transactions "
                      "(content-addressed DAG). DO NOT hand-edit. ADR-2605301400 §T2.\n")))
       (spit f (str (tx-to-edn tx) "\n") :append true)
       (get tx ":tx/cid"))))

#?(:clj
   (defn read-log
     "Read the log back as a list of transaction maps (uses the shared ip-edn reader)."
     [log-path]
     (let [f (clojure.java.io/file (str log-path))]
       (if-not (.exists f)
         []
         (->> (str/split-lines (slurp f))
              (map str/trim)
              (remove (fn [line] (or (= "" line) (str/starts-with? line ";"))))
              ;; each log line is one tx-map; ip-edn/read-all is the complete EDN reader
              ;; (the clj-port referenced ip-edn/parse + ip-edn/tokens, which were never implemented).
              (mapv (fn [line] (ip-edn/read-all line))))))))

#?(:clj
   (defn head-cid
     "The content-addressed HEAD = the last transaction's CID."
     [log-path]
     (let [txs (read-log log-path)]
       (if (seq txs) (get (last txs) ":tx/cid") ""))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from its datoms + prev and verify the DAG is intact.
     Returns {ok length broken_at}."
     [log-path]
     (let [txs (read-log log-path)]
       (loop [i 0, prev "", txs txs]
         (if (empty? txs)
           {"ok" true "length" (count (read-log log-path)) "broken_at" -1}
           (let [tx (first txs)
                 expect (tx-cid (get tx ":tx/datoms" []) prev)]
             (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
               {"ok" false "length" (count (read-log log-path)) "broken_at" i}
               (recur (inc i) (get tx ":tx/cid") (rest txs)))))))))
