(ns shomei.methods.edn
  "edn.cljc — 証明 (shomei) seed reader. ADR-2606072100.

  shomei's seed (`data/seed-claims.json`) is JSON, loaded by `analyze.py` via `json.loads`
  (shomei has no `_edn.py` — unlike the EDN-seeded siblings). So this 'edn' namespace is the
  shomei-family local reader: a minimal recursive-descent JSON parser yielding nested
  Clojure maps (STRING keys) / vectors / strings / longs / doubles / booleans / nil — the
  same shape `json.loads` produces, so the offline analyzer keys on identical strings.

  Pure (regexless char scanner); file I/O only at the #?(:clj) edge."
  (:require [clojure.string :as str]))

(declare parse-value)

(defn- skip-ws [^String s ^long i]
  (let [n (count s)]
    (loop [i i]
      (if (and (< i n) (#{\space \tab \newline \return} (.charAt s i)))
        (recur (inc i))
        i))))

(defn- parse-string [^String s ^long i]
  ;; i points at the opening quote
  (let [n (count s)
        sb (StringBuilder.)]
    (loop [i (inc i)]
      (when (>= i n) (throw (ex-info "shomei.edn: unterminated string" {})))
      (let [c (.charAt s i)]
        (cond
          (= c \") [(.toString sb) (inc i)]
          (= c \\) (let [e (.charAt s (inc i))]
                     (case e
                       \" (do (.append sb \") (recur (+ i 2)))
                       \\ (do (.append sb \\) (recur (+ i 2)))
                       \/ (do (.append sb \/) (recur (+ i 2)))
                       \n (do (.append sb \newline) (recur (+ i 2)))
                       \t (do (.append sb \tab) (recur (+ i 2)))
                       \r (do (.append sb \return) (recur (+ i 2)))
                       \b (do (.append sb \backspace) (recur (+ i 2)))
                       \f (do (.append sb \formfeed) (recur (+ i 2)))
                       \u (let [hex (subs s (+ i 2) (+ i 6))
                                cp #?(:clj (Integer/parseInt hex 16)
                                      :cljs (js/parseInt hex 16))]
                            (.append sb (char cp)) (recur (+ i 6)))
                       (do (.append sb e) (recur (+ i 2)))))
          :else (do (.append sb c) (recur (inc i))))))))

(defn- parse-number [^String s ^long i]
  (let [n (count s)]
    (loop [j i]
      (if (and (< j n) (#{\- \+ \. \e \E \0 \1 \2 \3 \4 \5 \6 \7 \8 \9} (.charAt s j)))
        (recur (inc j))
        (let [tok (subs s i j)
              v (if (re-matches #"[-+]?\d+" tok)
                  #?(:clj (Long/parseLong tok) :cljs (js/parseInt tok 10))
                  #?(:clj (Double/parseDouble tok) :cljs (js/parseFloat tok)))]
          [v j])))))

(defn- parse-array [^String s ^long i]
  (let [n (count s)]
    (loop [i (skip-ws s (inc i)) out []]
      (cond
        (>= i n) (throw (ex-info "shomei.edn: unterminated array" {}))
        (= (.charAt s i) \]) [out (inc i)]
        :else (let [[v i2] (parse-value s i)
                    i3 (skip-ws s i2)
                    i4 (if (and (< i3 n) (= (.charAt s i3) \,)) (skip-ws s (inc i3)) i3)]
                (recur i4 (conj out v)))))))

(defn- parse-object [^String s ^long i]
  (let [n (count s)]
    (loop [i (skip-ws s (inc i)) out {}]
      (cond
        (>= i n) (throw (ex-info "shomei.edn: unterminated object" {}))
        (= (.charAt s i) \}) [out (inc i)]
        :else (let [[k i2] (parse-string s i)
                    i3 (skip-ws s i2)
                    _ (when-not (= (.charAt s i3) \:) (throw (ex-info "shomei.edn: expected ':'" {})))
                    [v i4] (parse-value s (skip-ws s (inc i3)))
                    i5 (skip-ws s i4)
                    i6 (if (and (< i5 n) (= (.charAt s i5) \,)) (skip-ws s (inc i5)) i5)]
                (recur i6 (assoc out k v)))))))

(defn- parse-value [^String s ^long i]
  (let [c (.charAt s i)]
    (cond
      (= c \{) (parse-object s i)
      (= c \[) (parse-array s i)
      (= c \") (parse-string s i)
      (= c \t) [true (+ i 4)]
      (= c \f) [false (+ i 5)]
      (= c \n) [nil (+ i 4)]
      :else (parse-number s i))))

(defn parse-json
  "Parse a JSON string into nested maps (string keys) / vectors / scalars."
  [^String s]
  (first (parse-value s (skip-ws s 0))))

#?(:clj
   (defn load-json
     "Read + parse the JSON file at `path` (the Clojure equivalent of `json.loads(read_text)`)."
     [path]
     (parse-json (slurp path))))
