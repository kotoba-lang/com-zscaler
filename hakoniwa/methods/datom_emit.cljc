(ns hakoniwa.methods.datom-emit
  "hakoniwa 箱庭 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606111500).

  Projects the 箱庭 world graph into append-only kotoba Datoms [e a v tx op] — first-class
  canonical state (NOT a projection cache). Two strata:

    GROUND (durable, op :add) — one datom per (entity, attribute, value): the persona / entity /
      signal / outcome nodes and the :en/* 縁. This IS the world. Every persona ground datom
      carries :persona/synthetic true (G1 — the box holds no real people).

    DERIVED (transient, :bond/is-transient true) — the outcome DISTRIBUTION (quantiles). Per
      N1/G2 the distribution is computed on READ from the ensemble and is NOT a ground fact; it
      is emitted in a clearly-flagged transient block. There is NO :forecast/point datom (G2).

  House style: Python ':…' keyword strings stay strings; node/edge walk in EDN read order
  (byte-parity); float formatting mirrors Python's {v:g} (fmt-g). Reuses world (load/selectors)
  + distribution (fmt-g). Pure fns; file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [hakoniwa.methods.world :as world]
            [hakoniwa.methods.simulate :as simulate]
            [hakoniwa.methods.distribution :as distribution]
            #?(:clj [clojure.java.io :as io])))

(def node-attrs
  [":sim/kind" ":sim/label" ":sim/sourcing" ":entity/public-ref"
   ":persona/synthetic" ":persona/cohort" ":persona/susceptibility"
   ":persona/initial-stance" ":persona/weight"
   ":signal/push" ":signal/at-step"
   ":outcome/measures" ":outcome/statistic" ":outcome/use"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/weight" ":en/sourcing"])

(defn fmt
  "Port of datom_emit._fmt: bool → true/false; nil → nil; \":…\" kept literal; other string →
  quoted with \\ and \" escaped; float (double) → {v:g}; else str()."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (distribution/fmt-g v)
    (float? v) (distribution/fmt-g v)
    :else (str v)))

(defn emit
  "Faithful 1:1 of datom_emit.emit. Returns the kotoba Datom-log EDN text (trailing newline)."
  ([nodes edges dist meta] (emit nodes edges dist meta 1))
  ([nodes edges dist meta tx]
   (let [L (transient [])]
     (conj! L ";; hakoniwa 箱庭 — GENERATED kotoba Datom log (ADR-2606111500). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable world. DERIVED :bond/is-transient = distribution on read (N1/G2).")
     (conj! L ";; G1: every :persona is SYNTHETIC (:persona/synthetic true) — the box holds no real people.")
     (conj! L "[")

     ;; ── GROUND: node datoms (EDN insertion order → deterministic)
     (doseq [nid (world/node-order nodes)]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (let [v (get n a)]
             (when (and (contains? n a) (not (nil? v)))
               (conj! L (str "[" (fmt nid) " " a " " (fmt v) " " tx " :add]")))))))

     ;; ── GROUND: edge datoms (content-stable edge id en.<from>.<kind>.<to>)
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "."
                      (let [k (get e ":en/kind")] (if (str/starts-with? k ":") (subs k 1) k))
                      "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (let [v (get e a)]
             (when (and (contains? e a) (not (nil? v)))
               (conj! L (str "[" (fmt eid) " " a " " (fmt v) " " tx " :add]")))))))

     ;; ── GROUND: the simulation run configuration (reproducibility provenance)
     (let [run "run.hakoniwa"]
       (doseq [[a v] [[":run/steps" (get meta "steps")] [":run/replicas" (get meta "replicas")]
                      [":run/seed" (get meta "seed")] [":run/jitter" (get meta "jitter")]
                      [":run/kernel" ":friedkin-johnsen"]]]
         (conj! L (str "[" (fmt run) " " a " " (fmt v) " " tx " :add]"))))

     ;; ── DERIVED (transient — the DISTRIBUTION; N1/G2). NO point datom exists.
     (conj! L ";; ── DERIVED outcome distribution (transient; computed on read from the ensemble) ──")
     (let [q (get dist "quantiles")]
       (doseq [qk (world/ordered-keys q)]
         (conj! L (str "[outcome.adoption :bond/distribution-"
                       (if (str/starts-with? qk ":") (subs qk 1) qk) " "
                       (distribution/fmt-g (get q qk)) " " tx " :derived] "
                       ";; :bond/is-transient true"))))
     (conj! L (str "[outcome.adoption :bond/distribution-mean " (distribution/fmt-g (get dist "mean"))
                   " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[outcome.adoption :bond/point-asserted false " tx " :derived] "
                   ";; G2: distribution-only — never a point"))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI entry (file I/O at the edge): scenario-datoms.kotoba.edn (EAVT)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           scenario (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                      (io/file (first argv))
                      (io/file here "data" "seed-scenario.kotoba.edn"))
           opt (fn [flag dflt cast]
                 (if (some #{flag} argv) (cast (nth argv (inc (.indexOf argv flag)))) dflt))
           outdir (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           tx (opt "--tx" 1 #(Long/parseLong %))
           steps (opt "--steps" simulate/default-steps #(Long/parseLong %))
           replicas (opt "--replicas" simulate/default-replicas #(Long/parseLong %))
           seed (opt "--seed" simulate/default-seed #(Long/parseLong %))
           {:keys [nodes edges]} (world/load-file* scenario)
           [results meta] (simulate/ensemble nodes edges {:steps steps :replicas replicas :seed seed})
           dist (distribution/distribution results)
           out (io/file outdir "scenario-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit nodes edges dist meta tx))
       (println (str "hakoniwa datom log → " out " (" (count nodes) " nodes + " (count edges)
                     " 縁, tx=" tx ")"))
       0)))
