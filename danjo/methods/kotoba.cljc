(ns danjo.methods.kotoba
  "kotoba.py — danjo kotoba Datom-log writer (local, content-addressed). ADR-2605301600
  + ADR-2605262130 + ADR-2605312345. 1:1 Clojure port of `methods/kotoba.py`.

  The local, autonomous-loop write path: a self-driving heartbeat appends content-addressed
  transactions to a local append-only EDN log with NO external I/O. Canonical state = the kotoba
  Datom log (content-addressed EAVT assertions, append-only).

    - graph-datoms(records)        → EAVT assertions for every public procurement record. E = the
                                     record CID; attrs :gov.procurement/*. (G3 passive-only)
    - derived-datoms(observations) → EAVT assertions for each danjo.discrepancyObservation, flagged
                                     :danjo.obs/non-adjudicating true (G4) + :representative. RAISES
                                     if a verdict token ever creeps into an attr (G4 structural).
    - make-tx / append-tx / read-log / head-cid / verify-chain — content-addressed commit-DAG.

  EAVT = [op entity attribute value]; op is :db/add only (append-only — no :db/retract).
  Deterministic: the caller supplies tx-id + as-of (no wall clock) → resume-safe.

  House style (mirrors danjo.methods.budget-ledger / analyze): map keys stay verbatim string keys,
  Python ':ns/name' keyword strings stay literal strings; pure fns; file I/O only behind #?(:clj …).
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
     :default (fn [_] (throw (ex-info "bind danjo.methods.kotoba/*sha256-hex* on this host" {})))))

;; ── forbidden verdict tokens (G4, structural) ─────────────────────────────────
(def forbidden-verdict-tokens
  "Tokens that would make a persisted observation a VERDICT — must NEVER appear (G4)."
  ["verdict" "guilt" "wrongdoing" "finding" "culprit"
   "illegal" "crime" "violation" "unlawful" "fraud" "sanction"])

;; ── EAVT assertion ────────────────────────────────────────────────────────────
(defn add
  "One append-only EAVT assertion: [:db/add <entity> <attr> <value>]."
  [entity attr value]
  [":db/add" entity attr value])

(defn graph-datoms
  "Flatten the public procurement corpus into append-only EAVT assertions. E = the record's
  public-record CID; attrs namespaced :gov.procurement/*. Public pre-published record only (G3).
  Record key iteration follows the JSON parse (insertion) order — Python dict order."
  [records]
  (reduce
   (fn [out r]
     (if-not (map? r)
       out
       (let [e (get r "cid")]
         (if-not e
           out
           (reduce (fn [out k]
                     (if (= k "cid")
                       out
                       (conj out (add e (str ":gov.procurement/" k) (get r k)))))
                   out
                   (keys r))))))
   []
   records))

(defn obs-id
  "A stable, deterministic entity id for an observation (category + first source CID)."
  [o]
  (let [cid0 (first (or (seq (get o "sourceRecordCids")) ["?"]))]
    (str "danjo-obs:" (get o "category" "?") ":" cid0)))

(defn- lstrip-colon [^String s]
  (loop [i 0] (if (and (< i (count s)) (= \: (nth s i))) (recur (inc i)) (subs s i))))

(defn derived-datoms
  "Flatten danjo.discrepancyObservation records into append-only EAVT assertions, each carrying
  :danjo.obs/non-adjudicating true (G4 — a FACT, never a verdict), ≥2 source CIDs (G5), and the
  open method-note CID (G6). RAISES if a verdict token ever creeps into an attr (G4 structural)."
  [observations]
  (let [out (reduce
             (fn [out o]
               (let [e (obs-id o)]
                 (into out
                       [(add e ":danjo.obs/category" (str ":" (lstrip-colon (str (get o "category" "?")))))
                        (add e ":danjo.obs/non-adjudicating" true)
                        (add e ":danjo.obs/pattern" (get o "observedPattern" ""))
                        (add e ":danjo.obs/source-record-cids" (vec (get o "sourceRecordCids" [])))
                        (add e ":danjo.obs/method-note-cid" (get o "methodNoteCid" ""))
                        (add e ":danjo.obs/known-false-positive-modes" (vec (get o "knownFalsePositiveModes" [])))
                        (add e ":danjo.obs/sourcing" ":representative")])))
             []
             observations)]
    ;; G4 structural self-check: no verdict token may appear in any attribute we persist.
    (doseq [d out]
      (let [attr (str/lower-case (str (nth d 2)))]
        (when (some #(str/includes? attr %) forbidden-verdict-tokens)
          (throw (ex-info (str "G4: verdict attr " (pr-str (nth d 2))
                               " is unrepresentable in a danjo observation")
                          {:gate "G4" :attr (nth d 2)})))))
    out))

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
  (str ";; danjo kotoba Datom log — append-only EAVT transactions "
       "(content-addressed DAG). The censor's EYE, never the SWORD: "
       "non-adjudicating observations only (G4). DO NOT hand-edit. ADR-2605301600.\n"))

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
