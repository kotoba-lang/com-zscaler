(ns kaname.methods.datom-emit
  "kaname 要 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345 / ADR-2606172100).

  Projects the multilayer SoS graph into append-only kotoba Datoms [e a v tx op]:

    GROUND (durable, op :add) — one datom per (entity, attribute, value): the node + :en/* 縁.
      This IS the log.

    DERIVED (transient, op :derived) — the edge-primary leverage integrals (concentration /
      versatility / bridge / leverage / reach). Per N1/G4 these are computed on READ, NOT stored
      as ground; emitted in a clearly-flagged transient block.

  Reuses kaname.methods.sos. Node walk order = first-touch EDN read order (deterministic)."
  (:require [clojure.string :as str]
            [kaname.methods.sos :as sos]
            #?(:clj [clojure.java.io :as io])))

(def node-attrs
  [":organism/kind" ":organism/label" ":organism/sourcing" ":sos/open?" ":sos/role"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/domain" ":en/grasping-load" ":en/basis" ":en/sourcing"])

(defn- fmt-g
  "Mirror Python f-string {v:g}: integral doubles render without a point (1.0 → \"1\")."
  [v]
  (let [d (double v)]
    (if (and (not (Double/isInfinite d)) (not (Double/isNaN d))
             (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn fmt
  "bool → true/false; nil → nil; \":…\" kept literal; vector → [a b…]; string → quoted; double → {v:g}."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (vector? v) (str "[" (str/join " " (map fmt v)) "]")
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(defn- ranked-ids [d]
  (->> d
       (filter (fn [[_ v]] (> (double v) 0.0)))
       (sort-by (fn [[nid v]] [(- (double v)) nid]))
       (map first)))

(defn emit
  "Returns the kotoba Datom-log EDN text (trailing newline)."
  ([nodes node-order edges res] (emit nodes node-order edges res 1))
  ([nodes node-order edges res tx]
   (let [L (transient [])]
     (conj! L ";; kaname 要 — GENERATED kotoba Datom log (ADR-2606172100). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED op :derived = leverage integrals, computed on read (N1/G4).")
     (conj! L "[")
     ;; GROUND: node datoms
     (doseq [nid (or (seq node-order) (keys nodes))]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (let [v (get n a)]
             (when (and (contains? n a) (not (nil? v)))
               (conj! L (str "[" (fmt nid) " " a " " (fmt v) " " tx " :add]")))))
         ;; multi-valued source-actors → one datom per value
         (doseq [sa (get n ":sos/source-actors")]
           (conj! L (str "[" (fmt nid) " :sos/source-actor " (fmt sa) " " tx " :add]")))))
     ;; GROUND: edge datoms (content-stable id: en.<from>.<kind>.<domain>.<to>)
     (doseq [e edges]
       (let [k  (let [kk (get e ":en/kind")] (if (str/starts-with? kk ":") (subs kk 1) kk))
             dm (let [dd (get e ":en/domain")] (if (and dd (str/starts-with? dd ":")) (subs dd 1) dd))
             eid (str "en." (get e ":en/from") "." k "." dm "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (let [v (get e a)]
             (when (and (contains? e a) (not (nil? v)))
               (conj! L (str "[" (fmt eid) " " a " " (fmt v) " " tx " :add]")))))))
     ;; DERIVED (transient — NOT persisted; N1/G4)
     (conj! L ";; ── DERIVED leverage readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [nid (ranked-ids (:leverage res))]
       (conj! L (str "[" (fmt nid) " :bond/leverage " (fmt-g (get-in res [:leverage nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [nid (ranked-ids (:C res))]
       (conj! L (str "[" (fmt nid) " :bond/concentration " (fmt-g (get-in res [:C nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [[nid v] (sort-by key (:V res))]
       (when (pos? v)
         (conj! L (str "[" (fmt nid) " :bond/versatility " v " " tx " :derived] ;; :bond/is-transient true"))))
     (doseq [nid (ranked-ids (:bridge res))]
       (conj! L (str "[" (fmt nid) " :bond/bridge-load " (fmt-g (get-in res [:bridge nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [nid (ranked-ids (:reach res))]
       (conj! L (str "[" (fmt nid) " :bond/reach-imposed " (fmt-g (get-in res [:reach nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-sos.kotoba.edn"))
           outdir (io/file here "out")
           tx (if (some #{"--tx"} argv) (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [nodes node-order edges]} (sos/load-file* seed)
           res (sos/leverage nodes edges)
           out (io/file outdir "sos-leverage-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit nodes node-order edges res tx))
       (println (str "kaname datom log → " out " (" (count nodes) " nodes + " (count edges)
                     " 縁, tx=" tx ")"))
       0)))
