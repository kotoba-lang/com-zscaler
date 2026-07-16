(ns watatsuna.methods.kotoba
  "kotoba.py — watatsuna kotoba Datom-log writer (local, content-addressed). ADR-2606012600
  + ADR-2605262130 + ADR-2605312345. 1:1 Clojure port of `methods/kotoba.py`.

  The local, autonomous-loop write path: a self-driving heartbeat appends content-addressed
  transactions to a local append-only EDN log with NO external I/O. Canonical state = the kotoba
  Datom log (content-addressed EAVT assertions, append-only — 非終末論).

    - graph-datoms(rows)            → EAVT assertions for every entity (cable / station / link /
                                      segment / fault). E = the entity's id; list values fan out.
    - derived-datoms(cables, st, a) → EAVT assertions for the analyzer's derived :resilience/*
                                      signals (chokepoint-load, station degree/capacity,
                                      cable-diversity, redundancy-gap), flagged :resilience/derived.
    - make-tx / append-tx / read-log / head-cid / verify-chain — content-addressed commit-DAG.

  EAVT = [op entity attribute value]; op is :db/add only (append-only — no :db/retract). The derived
  signals are a RESILIENCE map (chokepoint-load, station-degree, cable-diversity, redundancy-gap),
  never a 'where to cut' / interdiction framing (G2). Deterministic: the caller supplies tx-id +
  as-of (no wall clock) → resume-safe.

  House style (mirrors watatsuna.methods.analyze): map keys stay verbatim string keys, Python
  ':ns/name' keyword strings stay literal strings; pure fns; file I/O only behind #?(:clj …).
  SELF-CONTAINED: own sha-256 + canonical JSON + EDN reader (the Python module imports analyze's
  _parse/_tokens; the cljc analyze keeps its reader in watatsuna.methods._edn, so this module
  inlines the equivalent reader to stay self-contained). The tx CID reproduces Python
  `'b' + hashlib.sha256(json.dumps({'prev':…,'datoms':…}, ensure_ascii=False, sort_keys=True,
  separators=(',',':')).encode('utf-8')).hexdigest()` byte-for-byte.
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
     :default (fn [_] (throw (ex-info "bind watatsuna.methods.kotoba/*sha256-hex* on this host" {})))))

;; ── insertion-order accessors (mirror Python dict iteration order) ────────────
;; analyze.cljc attaches its first-touch / insertion order vectors as metadata keyed in
;; the analyze namespace. Read them back here so derived-datoms iterates exactly as the
;; Python dicts do.
(def ^:private analyze-corder :watatsuna.methods.analyze/corder)
(def ^:private analyze-sorder :watatsuna.methods.analyze/sorder)
(def ^:private analyze-choke-order :watatsuna.methods.analyze/choke-order)

(defn- corder
  "First-insertion order of a cables/cable-diversity map (falls back to keys)."
  [m] (or (get (meta m) analyze-corder) (keys m)))

(defn- sorder
  "First-insertion order of a stations map (falls back to keys)."
  [m] (or (get (meta m) analyze-sorder) (keys m)))

(defn- choke-order
  "First-touch chokepoint order of a choke-* map (falls back to keys)."
  [m] (or (get (meta m) analyze-choke-order) (keys m)))

;; ── EAVT assertion ────────────────────────────────────────────────────────────
(defn add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(def ^:private id-keys
  [":cable/id" ":station/id" ":cable.link/id" ":cable.seg/id" ":cable.fault/id"])

(defn graph-datoms
  "Flatten the submarine-cable graph into append-only EAVT assertions. E = the entity's id;
  cardinality-many list values (e.g. :station/chokepoint, :cable.seg/traverses) fan out. Record
  key iteration follows the EDN parse (insertion) order — Python dict order."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some (fn [k] (when (contains? r k) (get r k))) id-keys)]
         (if (nil? e)
           out
           (reduce (fn [out k]
                     (if (some #{k} id-keys)
                       out
                       (let [v (get r k)
                             items (if (sequential? v) v [v])]
                         (reduce (fn [o item] (conj o (add e k item))) out items))))
                   out
                   (keys r))))))
   []
   rows))

(defn derived-datoms
  "Flatten the analyzer's derived :resilience/* signals into EAVT assertions, each flagged
  :resilience/derived true (a RESILIENCE map recomputed on read, never re-ingested as fact, and
  never an interdiction target-list — G2). Mirrors analyze.render_datoms. `a` is analyze.analyze().

  Iteration order mirrors the Python sorts over insertion-ordered dicts:
    chokepoints  sorted by -choke_load           (stable over first-touch chokepoint order)
    stations     sorted by -station_degree, skip 0 (stable over stations-insertion order)
    cables       sorted by  cable_diversity       (stable over cables-insertion order)
    redundancy   in a['redundancy_gap'] order (already sorted by analyze)."
  [cables stations a]
  (let [choke-load    (get a "choke_load")
        choke-count   (get a "choke_count")
        station-deg   (get a "station_degree")
        station-cap   (get a "station_capacity")
        cable-div     (get a "cable_diversity")
        red-gap       (get a "redundancy_gap")
        choke-ks      (sort-by (fn [cp] (- (double (get choke-load cp)))) (choke-order choke-load))
        ;; iterate stations in stations-insertion (sorder) order — mirrors Python sorted(stations, …)
        station-ks    (sort-by (fn [s] (- (get station-deg s))) (sorder stations))
        cable-ks      (sort-by (fn [c] (get cable-div c)) (corder cable-div))]
    (-> (transient [])
        (as-> out
              (reduce (fn [o cp]
                        (let [e (str "resilience-choke-" cp)]
                          (-> o
                              (conj! (add e ":resilience/chokepoint" cp))
                              (conj! (add e ":resilience/chokepoint-load" (get choke-load cp)))
                              (conj! (add e ":resilience/cable-count" (get choke-count cp)))
                              (conj! (add e ":resilience/derived" true)))))
                      out choke-ks)
              (reduce (fn [o s]
                        (if (= 0 (get station-deg s))
                          o
                          (let [e (str "resilience-station-" s)]
                            (-> o
                                (conj! (add e ":resilience/station" s))
                                (conj! (add e ":resilience/station-degree" (get station-deg s)))
                                (conj! (add e ":resilience/station-capacity-tbps" (get station-cap s)))
                                (conj! (add e ":resilience/derived" true))))))
                      out station-ks)
              (reduce (fn [o c]
                        (let [e (str "resilience-cable-" c)]
                          (-> o
                              (conj! (add e ":resilience/cable" c))
                              (conj! (add e ":resilience/cable-diversity" (get cable-div c)))
                              (conj! (add e ":resilience/derived" true)))))
                      out cable-ks)
              ;; redundancy-gap: landing stations served by a single cable (routed to watatsumi 敷設)
              (reduce (fn [o s]
                        (let [e (str "resilience-gap-" s)]
                          (-> o
                              (conj! (add e ":resilience/redundancy-gap-station" s))
                              (conj! (add e ":resilience/station-degree" (get station-deg s)))
                              (conj! (add e ":resilience/derived" true)))))
                      out red-gap))
        (persistent!))))

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
  (str ";; watatsuna kotoba Datom log — append-only EAVT transactions "
       "(content-addressed DAG). Resilience map, never interdiction. "
       "DO NOT hand-edit. ADR-2606012600.\n"))

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
;; Mirrors _TOK / _tokens / _atom / _parse. Tokenizes [, ], {, }, "strings", and bare atoms;
;; skips whitespace/commas and ; comments.

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
      (loop [ts rst, out {}]
        (let [[k ts2] (parse-tokens ts)]
          (if (= k end-marker)
            [out ts2]
            (let [[v ts3] (parse-tokens ts2)] (recur ts3 (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker rst]
      :else [(atom-val t) rst])))

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
