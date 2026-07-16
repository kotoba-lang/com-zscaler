(ns tate.methods.edn
  "tate 盾 — minimal EDN reader (subset: [] {} :kw \"str\" num bool nil). ADR-2606112301.
  1:1 Clojure port of the `_TOK`/`_tokens`/`_atom`/`_parse`/`read_edn` reader embedded
  in `methods/terms_scan.py` (shared by respond_plan / coverage_report / datom_emit).

  Fidelity invariant (root CLAUDE.md convention): keywords are kept as their \":ns/name\"
  STRINGS, NOT Clojure keywords. The whole tate pipeline keys every record on string keys
  (\":clause/id\", \":proc/id\", \":notice/jurisdiction\", …) and the Python `:`-tokens
  stay strings — so the loader must yield the same string shape the Python `read_edn` does,
  byte-for-byte, or the offline analyzer would key on the wrong thing.

  Note: tate's data EDN files use Clojure-style BARE keyword tokens (`:clause/id`,
  `:kaiyaku`), exactly like the Python `_atom` path which keeps any `:`-prefixed token as a
  string verbatim.

  Stdlib only (regex tokenizer); file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]))

;; ── tokenizer (mirror of the Python _TOK regex) ───────────────────────────
;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens
  "Lazy seq of significant tokens (capture group 1; whitespace/comments dropped)."
  [s]
  (let [m (re-matcher token-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn- unescape-string
  "Strip surrounding quotes and unescape \\\" and \\\\ (mirrors the Python atom path)."
  [t]
  (-> (subs t 1 (dec (count t)))
      (str/replace "\\\"" "\"")
      (str/replace "\\\\" "\\")))

(defn- parse-long* [^String t]
  #?(:clj (try (Long/parseLong t) (catch Exception _ nil))
     :cljs (when (re-matches #"[-+]?\d+" t)
             (let [n (js/parseInt t 10)] (when-not (js/isNaN n) n)))))

(defn- parse-double* [^String t]
  #?(:clj (try (Double/parseDouble t) (catch Exception _ nil))
     :cljs (when (re-matches #"[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?" t)
             (let [n (js/parseFloat t)] (when-not (js/isNaN n) n)))))

(defn- atom* [t]
  (cond
    (str/starts-with? t "\"") (unescape-string t)
    (= t "true")  true
    (= t "false") false
    (= t "nil")   nil
    (str/starts-with? t ":") t           ;; keyword kept as ":ns/name" STRING
    :else (or (parse-long* t) (parse-double* t) t)))

;; ── recursive-descent parser over a mutable token cursor ──────────────────
(def ^:private END ::end)

(defn- next-tok! [state]
  (let [ts @state]
    (when (empty? ts)
      (throw (ex-info "tate.methods.edn: unexpected end of input" {})))
    (reset! state (rest ts))
    (first ts)))

(defn- parse-form [state]
  (let [t (next-tok! state)]
    (cond
      (= t "[") (loop [out []]
                  (let [x (parse-form state)]
                    (if (= x END) out (recur (conj out x)))))
      (= t "{") (loop [out {}]
                  (let [k (parse-form state)]
                    (if (= k END)
                      out
                      (let [v (parse-form state)]
                        (recur (assoc out k v))))))
      (or (= t "]") (= t "}")) END
      :else (atom* t))))

(defn read-edn
  "Parse a full EDN string into nested vectors/maps/atoms (keywords as \":…\" strings).
  1:1 with Python `read_edn`."
  [s]
  (parse-form (atom (tokens s))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file at `path` (string or java.io.File)."
     [path]
     (read-edn (slurp path))))
