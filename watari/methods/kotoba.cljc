(ns watari.methods.kotoba
  "kotoba.py — watari kotoba Datom-log writer (local, content-addressed). ADR-2606041827
  + ADR-2605262130 + ADR-2605312345. 1:1 Clojure port of `methods/kotoba.py`.

  The local, autonomous-loop write path: a self-driving heartbeat appends content-addressed
  transactions to a local append-only EDN log with NO external I/O. Canonical state = the kotoba
  Datom log (content-addressed EAVT assertions, append-only — 非終末論; the fix stream IS the
  trajectory, the latest fix IS the current position).

    - graph-datoms(rows)          → EAVT assertions for every entity (craft / fix / leg / lane).
                                    E = the entity's id; cardinality-many list values fan out. The
                                    seed carries operator-as-company only, never a person (G4).
    - derived-datoms(craft ln a)  → EAVT assertions for the analyzer's derived :movement/* signals
                                    (chokepoint-transit, lane-load by kind, stale-track tail),
                                    flagged :movement/derived. Mirrors analyze.render_datoms.
    - make-tx / append-tx / read-log / head-cid / verify-chain — content-addressed commit-DAG.

  EAVT = [op entity attribute value]; op is :db/add only (append-only — no :db/retract).
  Deterministic: the caller supplies tx-id + as-of (no wall clock) → resume-safe.

  House style (mirrors danjo.methods.kotoba): map keys stay verbatim string keys, Python
  ':ns/name' keyword strings stay literal strings; pure fns; file I/O only behind #?(:clj …).
  SELF-CONTAINED: own sha-256 + canonical JSON + EDN reader, no sibling-stub require. The tx CID
  reproduces Python `'b' + hashlib.sha256(json.dumps({'prev':…,'datoms':…}, ensure_ascii=False,
  sort_keys=True, separators=(',',':')).encode('utf-8')).hexdigest()` byte-for-byte.
  (The Python `__main__` heartbeat printer is omitted — it is the autorun.cljc -main concern.)"
  (:require [clojure.string :as str]))

;; ── sha-256 host seam ─────────────────────────────────────────────────────────
(def ^:dynamic *sha256-hex*
  "String → lowercase hex sha-256 digest (UTF-8). Rebind on hosts without MessageDigest."
  #?(:clj (fn [^String s]
            (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                             (.getBytes s "UTF-8"))]
              (str/join (map #(let [h (Integer/toHexString (bit-and % 0xff))]
                                (if (= 1 (count h)) (str "0" h) h))
                             d))))
     :default (fn [_] (throw (ex-info "bind watari.methods.kotoba/*sha256-hex* on this host" {})))))

;; ── EAVT assertion ────────────────────────────────────────────────────────────
(def id-keys
  "ID keys, in priority order (mirrors ingest ID_KEYS). E = the first present id key."
  [":craft/id" ":craft.fix/id" ":craft.leg/id" ":lane/id"])

(def ^:private non-domain-keys
  "Keys that are NOT domain facts and must never be flattened into a graph datom, even though
  they are not entity-id candidates. `:db/id` is the Datomic/Datascript tx-data framing key the
  repo-wide edn-datomize pass adds to each seed record so the file is directly usable as
  (d/transact conn …) tx-data; it is scaffolding around the record, not a fact ABOUT the
  craft/lane/fix/leg, so it must not appear in the persisted EAVT log or the content-addressed
  CID (keeps `test-cid-matches-python`'s pinned literals byte-identical)."
  #{":db/id"})

(defn add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(defn- record-keys
  "Keys of a parsed record in first-touch (Python dict insertion) order. Falls back to seq order
  for maps that carry no ::korder metadata (e.g. an array-map ≤8 keys still iterates insertion-order)."
  [r]
  (or (::korder (meta r)) (keys r)))

(defn graph-datoms
  "Flatten the moving-craft graph into append-only EAVT assertions. E = the entity's id (the
  first present id key in id-keys order); cardinality-many list values fan out. Persists craft
  identity / position fixes / legs / lanes as-is — operator-as-company only, never a person (G4).
  Record key iteration follows the EDN parse (insertion) order — Python dict order — so the tx
  CID reproduces python3 byte-for-byte (requires rows parsed via this ns's load-edn)."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some (fn [k] (when (contains? r k) (get r k))) id-keys)]
         (if (nil? e)
           out
           (reduce (fn [out k]
                     (if (or (some #{k} id-keys) (contains? non-domain-keys k))
                       out
                       (let [v (get r k)
                             items (if (sequential? v) v [v])]
                         (reduce (fn [out item] (conj out (add e k item))) out items))))
                   out
                   (record-keys r))))))
   []
   rows))

(defn derived-datoms
  "Flatten the analyzer's derived :movement/* signals into EAVT assertions, each flagged
  :movement/derived true (aggregate situational-awareness recomputed on read, never re-ingested
  as fact, never a per-craft follow/targeting feed — G2/G4). Mirrors analyze.render_datoms.
  `a` is analyze.analyze()."
  [_craft _lanes a]
  (let [choke (get a "choke_transit")
        lane-load (get a "lane_load")
        lane-kind (get a "lane_kind")
        latest (get a "latest")
        stale (get a "stale")
        ;; sorted(d, key=lambda k: -d[k]) over an insertion-ordered dict — stable on ties.
        choke-order (sort-by (fn [k] (- (get choke k))) (keys choke))
        lane-order (sort-by (fn [k] (- (get lane-load k))) (keys lane-load))]
    (cond-> []
      true (into (mapcat (fn [cp]
                           [(add (str "movement-choke-" cp) ":movement/chokepoint" cp)
                            (add (str "movement-choke-" cp) ":movement/chokepoint-transit" (get choke cp))
                            (add (str "movement-choke-" cp) ":movement/derived" true)])
                         choke-order))
      true (into (mapcat (fn [ln]
                           (let [vk (count (get-in lane-kind [ln ":vessel"] #{}))
                                 ak (count (get-in lane-kind [ln ":aircraft"] #{}))
                                 e (str "movement-lane-" ln)]
                             [(add e ":movement/lane" ln)
                              (add e ":movement/lane-load" (get lane-load ln))
                              (add e ":movement/vessels" vk)
                              (add e ":movement/aircraft" ak)
                              (add e ":movement/derived" true)]))
                         lane-order))
      true (into (mapcat (fn [c]
                           (let [fx (get latest c)
                                 e (str "movement-stale-" c)]
                             [(add e ":movement/craft" c)
                              (add e ":movement/stale" true)
                              (add e ":movement/last-seen" (get fx ":craft.fix/observed-at" ""))
                              (add e ":movement/derived" true)]))
                         (sort stale))))))

;; ── canonical JSON for the CID preimage ──────────────────────────────────────
;; Mirrors _canonical's json.dumps({"prev":…,"datoms":…}, ensure_ascii=False, sort_keys=True,
;; separators=(",",":")). ensure_ascii=FALSE → non-ASCII emitted RAW, not \uXXXX.
(defn- json-escape-utf8 ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn- canonical-json-utf8 ^String [v]
  (cond
    (string? v)     (str "\"" (json-escape-utf8 v) "\"")
    (boolean? v)    (if v "true" "false")
    (nil? v)        "null"
    (integer? v)    (str v)
    (number? v)     (str v)
    (map? v)        (str "{" (str/join "," (map (fn [k] (str "\"" (json-escape-utf8 (str k)) "\":"
                                                             (canonical-json-utf8 (get v k))))
                                                (sort (keys v)))) "}")
    (sequential? v) (str "[" (str/join "," (map canonical-json-utf8 v)) "]")
    :else (throw (ex-info "canonical-json-utf8: unsupported value" {:value v}))))

(defn- canonical [datoms prev-cid]
  ;; sort_keys=True orders the top map's keys alphabetically: "datoms" < "prev".
  (canonical-json-utf8 {"prev" prev-cid "datoms" datoms}))

(defn tx-cid
  "Content address = sha256 over (prev-cid, datoms) → a commit-DAG."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid]
   (str "b" (*sha256-hex* (canonical datoms prev-cid)))))

(defn make-tx
  "Assemble one content-addressed transaction map (string :tx/* keys, mirrors Python)."
  [datoms & {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {":tx/id"     tx-id
   ":tx/as-of"  as-of
   ":tx/prev"   prev-cid
   ":tx/cid"    (tx-cid datoms prev-cid)
   ":tx/count"  (count datoms)
   ":tx/datoms" datoms})

;; ── EDN value rendering (mirrors _edn_val) ───────────────────────────────────
(defn- json-dumps-str
  "json.dumps(s, ensure_ascii=False) — a double-quoted, escaped JSON string."
  [^String s]
  (str "\"" (json-escape-utf8 s) "\""))

(defn- edn-val ^String [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (number? v)  (str v)
    (string? v)  (if (str/starts-with? v ":") v (json-dumps-str v))
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (json-dumps-str (str v))))

(defn- tx-to-edn ^String [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id")
         " :tx/as-of " (get tx ":tx/as-of")
         " :tx/prev " (json-dumps-str (get tx ":tx/prev"))
         " :tx/cid " (json-dumps-str (get tx ":tx/cid"))
         " :tx/count " (get tx ":tx/count")
         " :tx/datoms [" datoms "]}")))

(def ^:private log-header
  (str ";; watari kotoba Datom log — append-only EAVT transactions "
       "(content-addressed DAG). Situational-awareness, never surveillance / "
       "no person-tracking (G2/G4). DO NOT hand-edit. ADR-2606041827.\n"))

#?(:clj
   (defn append-tx
     "Append ONE transaction to the append-only log (never rewrites). Returns the tx CID."
     [tx log-path]
     (let [f (clojure.java.io/file (str log-path))]
       (when-let [parent (.getParentFile f)] (.mkdirs parent))
       (when-not (.exists f) (spit f log-header))
       (spit f (str (tx-to-edn tx) "\n") :append true)
       (get tx ":tx/cid"))))

;; ── minimal EDN reader (subset) for read-back, consistent with the actor family ──
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse. Tokenizes [, ], {, }, "strings",
;; and bare atoms; skips whitespace/commas and ; comments.

(defn- tokenize
  "Split an EDN line into significant tokens (mirrors the Python _TOK regex semantics)."
  [^String s]
  (let [n (count s)]
    (loop [i 0, out []]
      (if (>= i n)
        out
        (let [c (nth s i)]
          (cond
            (or (= c \space) (= c \tab) (= c \newline) (= c \return) (= c \,))
            (recur (inc i) out)
            (= c \;)                       ; comment to end of line
            (let [j (loop [j i] (if (and (< j n) (not= (nth s j) \newline)) (recur (inc j)) j))]
              (recur j out))
            (or (= c \[) (= c \]) (= c \{) (= c \}))
            (recur (inc i) (conj out (str c)))
            (= c \")                       ; "..." with \\ escapes
            (let [j (loop [j (inc i)]
                      (cond
                        (>= j n) j
                        (= (nth s j) \\) (recur (+ j 2))
                        (= (nth s j) \") (inc j)
                        :else (recur (inc j))))]
              (recur j (conj out (subs s i j))))
            :else                          ; bare atom up to whitespace/comma/bracket
            (let [j (loop [j i]
                      (if (and (< j n)
                               (not (contains? #{\space \tab \newline \return \, \[ \] \{ \}} (nth s j))))
                        (recur (inc j))
                        j))]
              (recur j (conj out (subs s i j))))))))))

(defn- atom-val
  "Token → value (mirrors _atom): quoted→string, true/false/nil, keyword string, int, float, else string."
  [^String t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true")  true
    (= t "false") false
    (= t "nil")   nil
    (str/starts-with? t ":") t
    :else
    (let [int? (re-matches #"[-+]?\d+" t)]
      (if int?
        #?(:clj (Long/parseLong t) :cljs (js/parseInt t 10))
        (let [flt (try #?(:clj (Double/parseDouble t) :cljs (js/parseFloat t))
                       (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (= flt ::nan) t flt))))))

(def ^:private end-marker ::end)

(defn- parse-tokens
  "Recursive-descent parse of a token vector → [value rest-tokens]. Mirrors _parse."
  [tokens]
  (let [t (first tokens), rst (rest tokens)]
    (cond
      (= t "[")
      (loop [ts rst, out []]
        (let [[x ts2] (parse-tokens ts)]
          (if (= x end-marker) [out ts2] (recur ts2 (conj out x)))))
      (= t "{")
      (loop [ts rst, out {}, korder []]
        (let [[k ts2] (parse-tokens ts)]
          (if (= k end-marker)
            [(with-meta out {::korder korder}) ts2]
            (let [[v ts3] (parse-tokens ts2)] (recur ts3 (assoc out k v) (conj korder k))))))
      (or (= t "]") (= t "}")) [end-marker rst]
      :else [(atom-val t) rst])))

(defn read-edn
  "Parse the first top-level form from EDN text, preserving each record map's key insertion order
  in ::korder metadata (so graph-datoms reproduces Python dict order byte-for-byte). Used for the
  craft graph the autorun loop persists."
  [text]
  (first (parse-tokens (tokenize text))))

#?(:clj
   (defn load-edn
     "Read + parse a craft-graph EDN file → vector of forms (order-preserving). File I/O at this edge."
     [path]
     (read-edn (slurp (clojure.java.io/file (str path))))))

#?(:clj
   (defn read-log
     "Read the append-only log → vector of tx maps. Skips blank + ;-comment lines."
     [log-path]
     (let [f (clojure.java.io/file (str log-path))]
       (if-not (.exists f)
         []
         (->> (str/split-lines (slurp f))
              (map str/trim)
              (remove (fn [l] (or (str/blank? l) (str/starts-with? l ";"))))
              (mapv (fn [l] (first (parse-tokens (tokenize l))))))))))

#?(:clj
   (defn head-cid
     "The CID of the last tx in the log (\"\" if empty)."
     [log-path]
     (let [txs (read-log log-path)]
       (if (seq txs) (get (last txs) ":tx/cid") ""))))

#?(:clj
   (defn verify-chain
     "Recompute every CID from its datoms + prev; verify the DAG is intact. {ok length broken-at}."
     [log-path]
     (let [txs (read-log log-path)]
       (loop [i 0, prev "", ts txs]
         (if (empty? ts)
           {"ok" true "length" (count txs) "broken_at" -1}
           (let [tx (first ts)
                 expect (tx-cid (get tx ":tx/datoms" []) prev)]
             (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
               {"ok" false "length" (count txs) "broken_at" i}
               (recur (inc i) (get tx ":tx/cid") (rest ts)))))))))
