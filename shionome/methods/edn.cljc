(ns shionome.methods.edn
  "Minimal EDN reader (subset: [] {} :kw \"str\" num bool nil). ADR-2606072201.
  Clojure port of `methods/_edn.py` (itself ported from ake/noroshi/watatsuna).

  The fidelity invariant this preserves: keywords are kept as their \":ns/name\"
  STRINGS, NOT as Clojure keywords. weave.py / social.py / analyze.py / the charter-
  invariant tests key every seed record on string keys (\":bucket/scope\", \":flow/kind\",
  \":snap/metric\", …) and the Python `:`-strings stay strings (root CLAUDE.md
  convention) — so the loader must yield the same string shape the Python `load_edn`
  does, byte-for-byte, or the offline analyzer would key on the wrong thing.

  Distinct from the keizu/ake reader in ONE place: shionome serializes strings with
  `json.dumps` (see kotoba.py), so `_edn._atom` decodes quoted tokens with `json.loads`
  — the EXACT inverse, correctly reversing \\n / \\t / \\\" / \\\\ / \\uXXXX. The earlier
  manual `replace` (\\\" and \\\\ only) missed \\n / \\t and broke the content-addressed-log
  roundtrip; this port replicates the full JSON string-unescape so a seed string with a
  newline/tab parses identically in cljc and Python (else the commit-DAG CID diverges).

  Stdlib only (regex tokenizer); file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]))

;; ── tokenizer (mirror of the Python _TOK regex) ───────────────────────────
;; Matches: whitespace/commas | ; comment | one of [ ] { } | "string" | bare atom.

(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens
  "Lazy seq of significant tokens (capture group 1; whitespace/comments dropped)."
  [s]
  (let [m (re-seq token-re s)]
    (keep (fn [match]
            (cond
              (vector? match) (second match)   ;; capture group present
              :else nil))
          m)))

(defn- hex->char
  "Decode 4 hex digits to the corresponding character (\\uXXXX)."
  [hex]
  #?(:clj  (char (Integer/parseInt hex 16))
     :cljs (js/String.fromCharCode (js/parseInt hex 16))))

(defn- json-unescape
  "Replicate `json.loads` of a JSON string body (the chars between the quotes):
  reverse \\\" \\\\ \\/ \\n \\t \\r \\b \\f and \\uXXXX. Faithful to the Python
  `_json.loads(t)` atom path so the content-addressed roundtrip is byte-identical.
  cljc-safe: accumulates chars in a vector (no host StringBuilder)."
  [body]
  (let [v (vec body), n (count v)]
    (loop [i 0, acc []]
      (if (>= i n)
        (apply str acc)
        (let [c (nth v i)]
          (if (and (= c \\) (< (inc i) n))
            (let [e (nth v (inc i))]
              (case e
                \" (recur (+ i 2) (conj acc \"))
                \\ (recur (+ i 2) (conj acc \\))
                \/ (recur (+ i 2) (conj acc \/))
                \n (recur (+ i 2) (conj acc \newline))
                \t (recur (+ i 2) (conj acc \tab))
                \r (recur (+ i 2) (conj acc \return))
                \b (recur (+ i 2) (conj acc \backspace))
                \f (recur (+ i 2) (conj acc \formfeed))
                \u (recur (+ i 6) (conj acc (hex->char (apply str (subvec v (+ i 2) (+ i 6))))))
                ;; unknown escape: keep the escaped char verbatim
                (recur (+ i 2) (conj acc e))))
            (recur (inc i) (conj acc c))))))))

(defn- unescape-string
  "Strip the surrounding quotes, then JSON-unescape the body (mirror of the Python
  `json.loads(t)` atom path — \\n / \\t / \\uXXXX faithful, not just \\\" / \\\\)."
  [t]
  (json-unescape (subs t 1 (dec (count t)))))

(defn- parse-long* [^String t]
  #?(:clj (try (Long/parseLong t) (catch Exception _ nil))
     :cljs (let [n (js/parseInt t 10)] (when (and (not (js/isNaN n)) (re-matches #"[-+]?\d+" t)) n))))

(defn- parse-double* [^String t]
  #?(:clj (try (Double/parseDouble t) (catch Exception _ nil))
     :cljs (let [n (js/parseFloat t)] (when (and (not (js/isNaN n)) (re-matches #"[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?" t)) n))))

(defn- atom* [t]
  (cond
    (str/starts-with? t "\"") (unescape-string t)
    (= t "true")  true
    (= t "false") false
    (= t "nil")   nil
    (str/starts-with? t ":") t           ;; keyword kept as ":ns/name" STRING
    :else (or (parse-long* t) (parse-double* t) t)))

;; ── recursive-descent parser over a mutable token cursor ──────────────────
;; `state` is an atom holding the remaining token seq; matches the Python `next(it)`.

(declare parse-form)

(def ^:private END ::end)

(defn- next-tok! [state]
  (let [ts @state]
    (when (empty? ts)
      (throw (ex-info "shionome.methods.edn: unexpected end of input" {})))
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

(defn parse-edn
  "Parse a full EDN string into nested vectors/maps/atoms (keywords as \":…\" strings)."
  [s]
  (parse-form (atom (tokens s))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file at `path` (string or java.io.File). The Clojure
     equivalent of `_edn.load_edn` — keywords kept as \":ns/name\" strings."
     [path]
     (parse-edn (slurp path))))
