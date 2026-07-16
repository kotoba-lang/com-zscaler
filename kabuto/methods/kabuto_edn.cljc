(ns kabuto.methods.kabuto-edn
  "kabuto 兜 — shared minimal EDN reader + datom classifier (ADR-2606022000).
  1:1 Clojure port of `methods/kabuto_edn.py` (itself ported from the
  watatsuna/tsumugi readers).

  The fidelity invariant this preserves: keywords are kept as their
  \":ns/name\" STRINGS, NOT as Clojure keywords. analyze.cljc keys every seed
  record on string keys (\":company/id\", \":supply.edge/from\", …) and the
  Python `:`-strings stay strings (root CLAUDE.md convention) — so the loader
  must yield the same string shape the Python `load_edn` does, byte-for-byte,
  or the offline analyzer would key on the wrong thing.

  Subset: vectors [], maps {}, :keyword, \"string\", number, bool, nil.
  Stdlib only (regex tokenizer); file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset) ──────────────────────────────────────────────
;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')

(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (capture group 1 of each tok-re match)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil;
  \":…\" kept as string; int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out (array-map)]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file at `path` (string or java.io.File). The Clojure
     equivalent of `kabuto_edn.load_edn` — keywords kept as \":ns/name\" strings."
     [path]
     (read-edn (slurp (str path)))))

;; ── classify the flat datom vector into entity buckets ───────────────────────
;; classify(rows) → (companies, addresses, contacts, edges, processes).
;; companies is an insertion-ordered map keyed on :company/id (Python dict order).

(defn classify
  "Return {:companies (ordered map) :addresses :contacts :edges :processes}
  keyed/listed exactly as Python `classify`. The companies map carries ::order
  metadata = insertion order of :company/id (Python dict order; array-map would
  degrade to an unordered hash-map past 8 entries, so order is tracked explicitly
  and read via co-keys/co-vals)."
  [rows]
  (reduce
   (fn [acc r]
     (cond
       (not (map? r)) acc
       (contains? r ":company/id")
       (let [cid (get r ":company/id")
             cs (:companies acc)
             had? (contains? cs cid)
             cs' (assoc cs cid r)]
         (assoc acc :companies
                (if had?
                  (with-meta cs' (meta cs))
                  (with-meta cs' (update (meta cs) ::order (fnil conj []) cid)))))
       (contains? r ":company.address/id") (update acc :addresses conj r)
       (contains? r ":company.contact/id") (update acc :contacts conj r)
       (contains? r ":supply.edge/id") (update acc :edges conj r)
       (contains? r ":company.process/id") (update acc :processes conj r)
       :else acc))
   {:companies (with-meta {} {::order []}) :addresses [] :contacts [] :edges [] :processes []}
   rows))

(defn co-keys
  "Company ids in insertion order (::order metadata; falls back to keys)."
  [companies]
  (or (::order (meta companies)) (keys companies)))

(defn co-vals
  "Company records in insertion order."
  [companies]
  (map #(get companies %) (co-keys companies)))

(defn edn-str
  "EDN-escape a string into a quoted EDN string literal (mirrors `edn_str`)."
  [s]
  (str "\"" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
