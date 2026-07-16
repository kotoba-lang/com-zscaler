(ns nyusatsu.methods.edn
  "Minimal EDN reader (subset: [] {} :kw \"str\" num bool nil). ADR-2606271700.
  Clojure port of the kosatsu/keizu/ake family `_edn.py` (same byte-for-byte reader).

  Fidelity invariant: keywords are kept as their \":ns/name\" STRINGS, NOT as Clojure
  keywords — the seed (`data/seed-procurement-graph.kotoba.edn`) keys every record on
  string keys (\":bid/ocid\", \":bid/status\", …) per the root CLAUDE.md convention, so
  the loader must yield the same string shape the Python `load_edn` does. Stdlib only."
  (:require [clojure.string :as str]))

(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (keep (fn [match] (when (vector? match) (second match)))
        (re-seq token-re s)))

(defn- unescape-string [t]
  (-> (subs t 1 (dec (count t)))
      (str/replace "\\\"" "\"")
      (str/replace "\\\\" "\\")))

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

(declare parse-form)
(def ^:private END ::end)

(defn- next-tok! [state]
  (let [ts @state]
    (when (empty? ts)
      (throw (ex-info "nyusatsu.methods.edn: unexpected end of input" {})))
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
     "Read + parse an EDN file at `path`. Keywords kept as \":ns/name\" strings."
     [path]
     (parse-edn (slurp (str path)))))
