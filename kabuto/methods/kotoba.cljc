(ns kabuto.methods.kotoba
  "kotoba.py — kabuto kotoba Datom-log writer (local, content-addressed). ADR-2606022000
  + ADR-2605262130 + ADR-2605312345. 1:1 Clojure port of `methods/kotoba.py`.

  The local, autonomous-loop write path: a self-driving heartbeat appends content-addressed
  transactions to a local append-only EDN log with NO external I/O. Canonical state = the kotoba
  Datom log (content-addressed EAVT assertions, append-only — 非終末論).

    - graph-datoms(rows)            → EAVT assertions for every entity (company / address / contact /
                                      supply-edge / process). E = the entity's id; lists fan out.
    - derived-datoms(a)            → EAVT assertions for the analyzer's derived :supply/* signals,
                                      flagged :supply/derived (mirrors analyze.render_datoms).
    - make-tx / append-tx / read-log / head-cid / verify-chain — content-addressed commit-DAG.

  EAVT = [op entity attribute value]; op is :db/add only (append-only — no :db/retract).
  Deterministic: the caller supplies tx-id + as-of (no wall clock) → resume-safe.

  CONSTITUTIONAL (kabuto G1/G2/G4): public listed-company public-record data only; every derived
  signal is a RESILIENCE + accountability map (concentration / single-source / tier-depth /
  cross-bloc corridors) — never a 'who to hit' / raid / takeover target-list.

  House style (mirrors kabuto.methods.kabuto-edn / analyze): map keys stay verbatim string keys,
  Python ':ns/name' keyword strings stay literal strings; pure fns; file I/O only behind #?(:clj …).
  SELF-CONTAINED: own sha-256 + canonical JSON + EDN reader (consistent with the sibling readers).
  The tx CID reproduces Python `'b' + hashlib.sha256(json.dumps({'prev':…,'datoms':…},
  ensure_ascii=False, sort_keys=True, separators=(',',':')).encode('utf-8')).hexdigest()`
  byte-for-byte. (The Python `__main__` heartbeat printer is omitted — autorun.cljc's concern.)"
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
     :default (fn [_] (throw (ex-info "bind kabuto.methods.kotoba/*sha256-hex* on this host" {})))))

;; ── analyze omap insertion-order meta key ─────────────────────────────────────
;; analyze.cljc tags its insertion-ordered maps with ::order under its OWN namespace; read it by
;; the exact namespaced keyword so `sorted(dict.items(), key=-value)` ties in dict insertion order.
(def ^:private analyze-order-key :kabuto.methods.analyze/order)

(defn- omap-items
  "Items of an analyze omap in first-touch insertion order (falls back to seq order)."
  [m]
  (if-let [order (get (meta m) analyze-order-key)]
    (map (fn [k] [k (get m k)]) order)
    (seq m)))

(defn- neg [x] (- x))

(defn- rank-desc
  "sorted(items, key=lambda kv: -kv[1]) over insertion-ordered items (stable)."
  [m]
  (sort-by (fn [[_ v]] (neg v)) (omap-items m)))

(defn- lstrip-colon [^String s]
  (loop [i 0] (if (and (< i (count s)) (= \: (nth s i))) (recur (inc i)) (subs s i))))

;; ── Python round(x, n): HALF_EVEN over the exact binary value (mirrors analyze/pyround) ───
(defn pyround
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :default (let [f (Math/pow 10 n)] (/ (Math/round (* (double x) f)) f))))

;; ── EAVT assertion ────────────────────────────────────────────────────────────
(def ^:private id-keys
  [":company/id" ":company.address/id" ":company.contact/id"
   ":supply.edge/id" ":company.process/id"])

(defn add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(defn graph-datoms
  "Flatten the public-company supply-chain graph into append-only EAVT assertions. E = the entity's
  id; cardinality-many list values fan out. Listed-company public-record facts only; the seed
  carries no personal PII (G1). Key iteration follows the EDN parse (insertion) order."
  [rows]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (some #(get r %) id-keys)]
         (if (nil? e)
           out
           (reduce (fn [out k]
                     (if (some #{k} id-keys)
                       out
                       (let [v (get r k)
                             items (if (sequential? v) v [v])]
                         (reduce (fn [out item] (conj out (add e k item))) out items))))
                   out
                   (keys r))))))
   []
   rows))

(defn derived-datoms
  "Flatten the analyzer's derived :supply/* concentration signals into EAVT assertions, each flagged
  :supply/derived true (a RESILIENCE + accountability map recomputed on read, never re-ingested as
  fact, never a target-list — G2/G4). Mirrors analyze.render_datoms. `a` is analyze.analyze()."
  [a]
  (let [out (transient [])
        emit (fn [e attr v] (conj! out (add e attr v)))]
    ;; single_source
    (doseq [[i [c commodity sup crit]] (map-indexed vector (get a "single_source"))]
      (let [e (str "supply-single-" i)]
        (emit e ":supply/single-source-customer" c)
        (emit e ":supply/commodity" commodity)
        (emit e ":supply/sole-supplier" sup)
        (emit e ":supply/criticality" crit)
        (emit e ":supply/derived" true)))
    ;; jurisdiction_load
    (doseq [[country load] (rank-desc (get a "jurisdiction_load"))]
      (let [e (str "supply-juris-" country)]
        (emit e ":supply/jurisdiction" country)
        (emit e ":supply/jurisdiction-load" (pyround load 2))
        (emit e ":supply/derived" true)))
    ;; systemic
    (doseq [[sup load] (rank-desc (get a "systemic"))]
      (let [e (str "supply-systemic-" sup)]
        (emit e ":supply/systemic-supplier" sup)
        (emit e ":supply/out-degree" (get (get a "out_deg") sup 0))
        (emit e ":supply/outward-criticality" (pyround load 2))
        (emit e ":supply/derived" true)))
    ;; diversification
    (doseq [[c secs load idx] (get a "diversification")]
      (let [e (str "supply-divers-" c)]
        (emit e ":supply/diversification-customer" c)
        (emit e ":supply/supplier-sectors" secs)
        (emit e ":supply/inbound-criticality" load)
        (emit e ":supply/diversification-index" idx)
        (emit e ":supply/derived" true)))
    ;; intermediaries
    (doseq [[n ind outd score] (get a "intermediaries")]
      (let [e (str "supply-inter-" n)]
        (emit e ":supply/intermediary" n)
        (emit e ":supply/in-degree" ind)
        (emit e ":supply/out-degree" outd)
        (emit e ":supply/betweenness" score)
        (emit e ":supply/derived" true)))
    ;; tier_depth
    (doseq [[n d] (get a "tier_depth")]
      (let [e (str "supply-tier-" n)]
        (emit e ":supply/tier-depth-node" n)
        (emit e ":supply/tier-depth" d)
        (emit e ":supply/derived" true)))
    ;; bloc_load
    (doseq [[bloc load] (rank-desc (get a "bloc_load"))]
      (let [e (str "supply-bloc-" bloc)]
        (emit e ":supply/region-bloc" bloc)
        (emit e ":supply/bloc-load" (pyround load 2))
        (emit e ":supply/derived" true)))
    ;; commodity_hhi
    (doseq [[commodity nsup hhi] (get a "commodity_hhi")]
      (let [e (str "supply-commodity-" (lstrip-colon (str commodity)))]
        (emit e ":supply/commodity" (lstrip-colon (str commodity)))
        (emit e ":supply/commodity-suppliers" nsup)
        (emit e ":supply/commodity-hhi" hhi)
        (emit e ":supply/derived" true)))
    ;; cross_corridors
    (doseq [[corridor load] (get a "cross_corridors")]
      (let [e (str "supply-corridor-" corridor)]
        (emit e ":supply/cross-bloc-corridor" corridor)
        (emit e ":supply/cross-bloc-load" (pyround load 2))
        (emit e ":supply/derived" true)))
    ;; resilience
    (doseq [[c score ss _secs _load _cross] (get a "resilience")]
      (let [e (str "supply-resil-" c)]
        (emit e ":supply/resilience-customer" c)
        (emit e ":supply/resilience-score" score)
        (emit e ":supply/single-source-count" ss)
        (emit e ":supply/derived" true)))
    ;; sector_cap_rank
    (doseq [[sec cap] (get a "sector_cap_rank" [])]
      (let [e (str "supply-capsec-" (lstrip-colon (str sec)))]
        (emit e ":supply/cap-sector" (lstrip-colon (str sec)))
        (emit e ":supply/cap-sector-busd" (pyround cap 1))
        (emit e ":supply/derived" true)))
    ;; cap-hhi (only if cap_count truthy / non-zero)
    (when (let [cc (get a "cap_count")] (and cc (not (zero? cc))))
      (let [e "supply-cap-hhi"]
        (emit e ":supply/cap-hhi" (get a "cap_hhi"))
        (emit e ":supply/cap-total-busd" (get a "total_cap"))
        (emit e ":supply/cap-covered" (get a "cap_count"))
        (emit e ":supply/cap-coverage-pct" (get a "cap_coverage"))
        (emit e ":supply/derived" true)))
    (persistent! out)))

;; ── canonical JSON for the CID preimage ──────────────────────────────────────
;; Mirrors _canonical's json.dumps({"prev":…,"datoms":…}, ensure_ascii=False, sort_keys=True,
;; separators=(",",":")). ensure_ascii=FALSE → non-ASCII emitted RAW, not \uXXXX.
(defn- json-escape-utf8 ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn- num-json
  "json.dumps(number) — int prints without decimal; float via shortest round-trip repr
  (Double/toString agrees with Python repr for the rounded magnitudes in play)."
  [v]
  (if (integer? v) (str v) (str (double v))))

(defn- canonical-json-utf8 ^String [v]
  (cond
    (string? v)     (str "\"" (json-escape-utf8 v) "\"")
    (boolean? v)    (if v "true" "false")
    (nil? v)        "null"
    (number? v)     (num-json v)
    (map? v)        (str "{" (str/join "," (map (fn [k] (str "\"" (json-escape-utf8 (str k)) "\":"
                                                             (canonical-json-utf8 (get v k))))
                                                (sort (keys v)))) "}")
    (sequential? v) (str "[" (str/join "," (map canonical-json-utf8 v)) "]")
    :else (throw (ex-info "canonical-json-utf8: unsupported value" {:value v}))))

(defn datom-key
  "json.dumps(datom, ensure_ascii=False, sort_keys=True) — the canonical-order sort key for one
  EAVT assertion (a list, so sort_keys is a no-op but the serialization must match byte-for-byte)."
  [d]
  (canonical-json-utf8 d))

(defn canonical-order
  "Sort datoms by datom-key so the tx-cid hash is independent of set/seq iteration
  order — any permutation of the same datoms yields the same canonical vector
  (and therefore the same CID). Idempotent: sorting an already-sorted vector is
  a no-op."
  [datoms]
  (vec (sort-by datom-key datoms)))

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
    (boolean? v)    (if v "true" "false")
    (number? v)     (num-json v)
    (string? v)     (if (str/starts-with? v ":") v (json-dumps-str v))
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
  (str ";; kabuto kotoba Datom log — append-only EAVT transactions "
       "(content-addressed DAG). Resilience + accountability map, never a "
       "target-list. DO NOT hand-edit. ADR-2606022000.\n"))

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
            (= c \;)
            (let [j (loop [j i] (if (and (< j n) (not= (nth s j) \newline)) (recur (inc j)) j))]
              (recur j out))
            (or (= c \[) (= c \]) (= c \{) (= c \}))
            (recur (inc i) (conj out (str c)))
            (= c \")
            (let [j (loop [j (inc i)]
                      (cond
                        (>= j n) j
                        (= (nth s j) \\) (recur (+ j 2))
                        (= (nth s j) \") (inc j)
                        :else (recur (inc j))))]
              (recur j (conj out (subs s i j))))
            :else
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
