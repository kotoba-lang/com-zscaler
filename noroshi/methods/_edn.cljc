(ns noroshi.methods._edn
  "Minimal EDN reader (subset: [] {} :kw \"str\" num bool nil) — 1:1 Clojure port of
  20-actors/noroshi/methods/_edn.py (itself ported from watatsuna/tsumugi).

  Keeps keywords as \":ns/name\" STRINGS (Python parity) so the lexicon / manifest /
  ontology / seed maps are byte-for-byte the shapes `_edn.py` produced. Stdlib only;
  file I/O only behind #?(:clj …)."
  (:require [clojure.string :as str]))

;; ── tokenizer ─────────────────────────────────────────────────────────────────
;; Python: re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
;; Whitespace/commas and comments are separators (group 1 nil → skipped); the captured
;; group is a real token.
(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (->> (re-seq token-re s)
       (keep (fn [m] (when (vector? m) (second m))))))

(defn- atom* [^String t]
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
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs js/Error) _ ::nope))]
      (if (not= as-long ::nope)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs js/Error) _ ::nope))]
          (if (not= as-dbl ::nope) as-dbl t))))))

(def ^:private END ::end)

(defn- parse-one
  "Parse one form from the mutable token cursor (an atom holding a seq). Returns the
  parsed value or END for a closing bracket. Mirrors `_parse(it)`."
  [cur]
  (let [t (first @cur)]
    (swap! cur rest)
    (cond
      (= t "[")
      (loop [out []]
        (let [x (parse-one cur)]
          (if (= x END) out (recur (conj out x)))))
      (= t "{")
      (loop [out {}]
        (let [k (parse-one cur)]
          (if (= k END)
            out
            (let [v (parse-one cur)]
              (recur (assoc out k v))))))
      (or (= t "]") (= t "}")) END
      :else (atom* t))))

(defn parse-edn
  "Parse the first EDN form in `s` → Clojure data (keywords kept as strings)."
  [s]
  (parse-one (atom (tokens s))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file (file I/O only at this edge)."
     [path]
     (parse-edn (slurp (str path)))))
