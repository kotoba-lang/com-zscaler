(ns mimamori.methods.-edn
  "Minimal EDN reader (subset: [] {} :kw \"str\" num bool nil).
  Clojure port of `methods/_edn.py` (itself ported from ake/noroshi/watatsuna).

  Keeps keywords as \":ns/name\" STRINGS (NOT clojure keywords) and maps as
  string-keyed maps -- byte-for-byte the same shapes the Python reader produces,
  so the ontology-validator parity test compares identical string surfaces on
  both sides. Stdlib only; pure.

  Faithful to the Python module: tokens / atom / parse / load-edn."
  (:require [clojure.string :as str]))

;; The Python regex: r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)'
;; group(1) is nil for whitespace/comment runs; the captured token otherwise.
(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of the scanner), mirroring _tokens."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (some? t)
              (cons t (step))
              (step)))))))))

(defn- parse-long-strict [t]
  ;; Python int(t): base-10 integer or ValueError. Reject any non-integer.
  (when (re-matches #"[+-]?\d+" t)
    #?(:clj (Long/parseLong t) :cljs (js/parseInt t 10))))

(defn- parse-double-strict [t]
  ;; Python float(t): a numeric literal or ValueError.
  (when (re-matches #"[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?" t)
    #?(:clj (Double/parseDouble t) :cljs (js/parseFloat t))))

(defn- json-unescape
  "Reverse a JSON string body's escapes (json.loads inverse): backslash forms
  and \\uXXXX. Char-by-char so no escape is double-handled."
  [^String inner]
  (let [n (count inner)]
    (loop [i 0 sb (StringBuilder.)]
      (if (>= i n)
        (.toString sb)
        (let [c (.charAt inner i)]
          (if (and (= c \\) (< (inc i) n))
            (let [e (.charAt inner (inc i))]
              (case e
                \" (recur (+ i 2) (.append sb \"))
                \\ (recur (+ i 2) (.append sb \\))
                \/ (recur (+ i 2) (.append sb \/))
                \b (recur (+ i 2) (.append sb \backspace))
                \f (recur (+ i 2) (.append sb \formfeed))
                \n (recur (+ i 2) (.append sb \newline))
                \r (recur (+ i 2) (.append sb \return))
                \t (recur (+ i 2) (.append sb \tab))
                \u (recur (+ i 6)
                          (.append sb (char #?(:clj (Integer/parseInt (subs inner (+ i 2) (+ i 6)) 16)
                                               :cljs (js/parseInt (subs inner (+ i 2) (+ i 6)) 16)))))
                (recur (+ i 2) (.append sb e))))
            (recur (inc i) (.append sb c))))))))

(defn atom*
  "Token -> value. Strings via JSON-style unescape (json.loads inverse), with the
  manual fallback the Python keeps; keywords as \":...\" strings; bool/nil; int; float."
  [^String t]
  (cond
    (str/starts-with? t "\"")
    (try
      (json-unescape (subs t 1 (dec (count t))))
      (catch #?(:clj Exception :cljs :default) _
        (-> (subs t 1 (dec (count t)))
            (str/replace "\\\"" "\"")
            (str/replace "\\\\" "\\"))))
    (= t "true")  true
    (= t "false") false
    (= t "nil")   nil
    (str/starts-with? t ":") t
    :else (or (parse-long-strict t)
              (parse-double-strict t)
              t)))

(def ^:private END ::end)

(defn- parse-step
  "Consume one value from the token vector starting at index i.
  Returns [value next-index]; value is ::end for a closing delimiter."
  [toks i]
  (let [t (nth toks i)]
    (cond
      (= t "[")
      (loop [i (inc i) out []]
        (let [[x j] (parse-step toks i)]
          (if (= x END) [out j] (recur j (conj out x)))))
      (= t "{")
      (loop [i (inc i) out {}]
        (let [[k j] (parse-step toks i)]
          (if (= k END)
            [out j]
            (let [[v j2] (parse-step toks j)]
              (recur j2 (assoc out k v))))))
      (or (= t "]") (= t "}")) [END (inc i)]
      :else [(atom* t) (inc i)])))

(defn parse
  "Parse the first value from a token sequence (mirrors _parse)."
  [toks]
  (first (parse-step (vec toks) 0)))

(defn load-edn
  "Read + parse an EDN file path (host file I/O at this edge)."
  [path]
  #?(:clj (parse (tokens (slurp (str path))))
     :default (throw (ex-info "load-edn: bind a slurp on this host" {:path path}))))
