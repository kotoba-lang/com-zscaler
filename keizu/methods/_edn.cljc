(ns keizu.methods._edn
  "Minimal EDN reader (subset: [] {} :kw \"str\" num bool nil).

   Keeps keywords as \":kw\" strings. Stdlib only. Used by weave/social/analyze/ingest
   to read the ontology + lexicons + seed without a dependency, mirroring the other actors'
   parsers for parity."
  (:require [clojure.string :as str]))

;; ── tokenisation ─────────────────────────────────────────────────────────────
(def ^:private token-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(def ^:private sentinel ::sentinel)

(defn- tokens [^String s]
  (keep second (re-seq token-re s)))

;; ── atoms ────────────────────────────────────────────────────────────────────
(defn- atom' [t]
  (if (str/starts-with? t "\"")
    (-> t
        (subs 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (condp = t
      "true"  true
      "false" false
      "nil"   nil
      (if (str/starts-with? t ":")
        t
        (try (Long/parseLong t)
             (catch Exception _
               (try (Double/parseDouble t)
                    (catch Exception _ t))))))))

;; ── recursive descent ────────────────────────────────────────────────────────
(declare parse-form)

(defn- parse-forms [it build add]
  (loop [it it, out (build)]
    (let [[v it'] (parse-form it)]
      (if (identical? v sentinel)
        [out it']
        (recur it' (add out v))))))

(defn- parse-map [it]
  (loop [it it, out {}]
    (let [[k it] (parse-form it)]
      (if (identical? k sentinel)
        [out it]
        (let [[v it] (parse-form it)]
          (recur it (assoc out k v)))))))

(defn- parse-form [it]
  (let [t (first it)]
    (cond
      (nil? t)  (throw (ex-info "Unexpected end of tokens" {}))
      (= t "[") (parse-forms (next it) vector conj)
      (= t "{") (parse-map (next it))
      (= t "]") [sentinel (next it)]
      (= t "}") [sentinel (next it)]
      :else     [(atom' t) (next it)])))

;; ── public ───────────────────────────────────────────────────────────────────
(defn parse-edn
  "Parse EDN text → Clojure data (keywords kept as \":kw\" strings)."
  [text]
  (first (parse-form (seq (tokens text)))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file (file I/O only at this edge)."
     [path]
     (parse-edn (slurp (str path)))))
