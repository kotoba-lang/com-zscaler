(ns kasa.methods.kasa-edn
  "kasa 嵩 — minimal EDN-subset reader (1:1 Clojure port of methods/kasa_edn.py).

  Parses the subset of EDN the kasa seed / output files use: a top-level vector of
  maps whose values are strings, doubles (incl. scientific 5.0e25), longs, keywords,
  nil/true/false, and nested vectors. No tagged literals, no sets, no symbols.

  Returns Clojure data: maps→map (keyword keys kept as \":foo\" strings), keywords→\":foo\"
  strings, vectors→vector. Mirrors kabuto/kanjo_edn shape so the actor family reads its own
  substrate without a third-party EDN dependency. ADR-2606072000.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at the
  #?(:clj) edge. Maps preserve insertion order (array-map ≤8 keys; ordered-map otherwise)
  so byte-parity with Python dict order is exact."
  (:require [clojure.string :as str]))

;; ── reader (port of the _R char-cursor parser) ──────────────────────────────
;; The Python _R walks character-by-character. We mirror that exactly with a mutable
;; index inside a single function, returning [value next-index] pairs. Keywords keep the
;; leading ':' (a string, not a Clojure keyword). Maps preserve insertion order.

(def ^:private eof ::eof)

(defn- ws? [c]
  (or (= c \space) (= c \tab) (= c \return) (= c \newline) (= c \,)))

(defn- delim? [c]
  ;; the set " \t\r\n,[]{}\"" used by _kw / _atom to terminate a token
  (or (ws? c) (= c \[) (= c \]) (= c \{) (= c \}) (= c \")))

(defn- ordered-assoc
  "assoc that preserves first-touch insertion order for >8 keys (array-map silently
  becomes an unordered hash-map past 8 entries; we track order in ::order metadata)."
  [m k v]
  (if (and (instance? clojure.lang.PersistentArrayMap m) (< (count m) 8) (not (contains? m k)))
    (assoc m k v)
    (let [had? (contains? m k)
          base (if (::order (meta m)) m (with-meta (into (array-map) m) {::order (vec (keys m))}))
          m' (assoc base k v)]
      (if had? (with-meta m' (meta base))
          (with-meta m' (update (meta base) ::order (fnil conj []) k))))))

(defn- skip-ws
  "Advance i past whitespace, commas, and ; line comments (1:1 with _R.skip_ws). Returns new i."
  [^String s n i]
  (loop [i i]
    (if (>= i n)
      i
      (let [c (.charAt s i)]
        (cond
          ;; ; line comment — consume to (but not past) newline, then keep skipping
          (= c \;) (recur (loop [j i] (if (and (< j n) (not= (.charAt s j) \newline)) (recur (inc j)) j)))
          (ws? c) (recur (inc i))
          :else i)))))

(declare read-form)

(defn- read-vec [^String s n i]
  ;; i points just past '['
  (loop [i i, out []]
    (let [i (skip-ws s n i)]
      (cond
        (>= i n) (throw (ex-info "unterminated vector" {}))
        (= (.charAt s i) \]) [out (inc i)]
        :else (let [[v i] (read-form s n i)]
                (recur i (conj out v)))))))

(defn- read-map [^String s n i]
  ;; i points just past '{'
  (loop [i i, out (array-map)]
    (let [i (skip-ws s n i)]
      (cond
        (>= i n) (throw (ex-info "unterminated map" {}))
        (= (.charAt s i) \}) [out (inc i)]
        :else (let [[k i] (read-form s n i)
                    [v i] (read-form s n i)]
                (recur i (ordered-assoc out k v)))))))

(defn- read-str [^String s n i]
  ;; i points just past opening quote
  (let [sb (StringBuilder.)]
    (loop [i i]
      (if (>= i n)
        (throw (ex-info "unterminated string" {}))
        (let [c (.charAt s i)]
          (cond
            (= c \\) (let [nxt (if (< (inc i) n) (.charAt s (inc i)) \space)
                           rep (case nxt \n \newline \t \tab \r \return nxt)]
                       (.append sb rep)
                       (recur (+ i 2)))
            (= c \") [(.toString sb) (inc i)]
            :else (do (.append sb c) (recur (inc i)))))))))

(defn- read-kw [^String s n i]
  ;; i points at ':'; keep leading colon, read to first delimiter
  (let [j i]
    (loop [i (inc i)]
      (if (and (< i n) (not (delim? (.charAt s i))))
        (recur (inc i))
        [(subs s j i) i]))))

(defn- numeric-token?
  "Mirror of analyze.py kasa_edn._atom number test: any of .eE present AND the token,
  with leading sign chars and . e E - + stripped, is all digits."
  [^String tok]
  (and (some #(or (= % \.) (= % \e) (= % \E)) tok)
       (let [stripped (-> tok
                          (str/replace #"^[-+]+" "")
                          (str/replace "." "")
                          (str/replace "e" "")
                          (str/replace "E" "")
                          (str/replace "-" "")
                          (str/replace "+" ""))]
         (and (seq stripped) (every? #(Character/isDigit ^char %) stripped)))))

(defn- read-atom [^String s n i]
  (let [j i]
    (loop [i i]
      (if (and (< i n) (not (delim? (.charAt s i))))
        (recur (inc i))
        (let [tok (subs s j i)]
          [(cond
             (= tok "nil") nil
             (= tok "true") true
             (= tok "false") false
             (numeric-token? tok)
             (try (Double/parseDouble tok) (catch #?(:clj Exception :cljs :default) _ tok))
             :else
             (let [as-long (try (Long/parseLong tok) (catch #?(:clj Exception :cljs :default) _ ::nan))]
               (if (not= as-long ::nan) as-long tok)))
           i])))))

(defn- read-form
  "Read one form at index i; return [value next-i] or [eof i] at EOF."
  [^String s n i]
  (let [i (skip-ws s n i)]
    (if (>= i n)
      [eof i]
      (let [c (.charAt s i)]
        (cond
          (= c \[) (read-vec s n (inc i))
          (= c \{) (read-map s n (inc i))
          (= c \") (read-str s n (inc i))
          (= c \:) (read-kw s n i)
          :else (read-atom s n i))))))

(defn read-all
  "Read every top-level form; return the first vector found (the dataset), else first form, else []."
  [text]
  (let [s (str text), n (count s)]
    (loop [i 0, forms []]
      (let [[v i] (read-form s n i)]
        (if (= v eof)
          (or (first (filter vector? forms))
              (first forms)
              [])
          (recur i (conj forms v)))))))

#?(:clj
   (defn read-file
     "Read + parse an EDN file → the top-level vector. File I/O only at this edge."
     [path]
     (read-all (slurp (str path)))))
