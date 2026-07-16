(ns hoshimori.methods.coverage-report
  "hoshimori 星守 — orbital COVERAGE report (ADR-2606073600). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the orbital graph: by orbital regime, by operator kind, by hazard kind,
  by service kind — with a gap map naming thin/missing buckets. Coverage of all catalogued
  objects is ~0 by design (a bounded :representative seed at shell-aggregate granularity); this
  makes the covered regime/hazard backbone measurable and names the next wave.

  Pure fns; reuses hoshimori.methods.analyze for the loader. Portable .cljc."
  (:require [clojure.string :as str]
            [hoshimori.methods.analyze :as analyze]))

;; honest external denominators for the OBJECT count (we model shells, not objects — by design)
(def denominators
  [["Active satellites (~)" 10000]
   ["Tracked debris >10cm (~)" 36000]
   ["Estimated debris >1cm (~)" 1000000]])

(def regimes [":leo-low" ":leo-high" ":sso" ":meo" ":geo" ":heo"])
(def op-kinds [":constellation" ":single-asset" ":station" ":agency"])
(def hazards [":debris-density" ":conjunction" ":congestion" ":asat-debris-event"
              ":space-weather" ":deorbit-shortfall"])
(def services [":pnt" ":broadband" ":earth-observation" ":weather" ":science"])
(def THIN 1) ;; at shell granularity a single shell per regime is expected; flag only zero

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- comma
  "Python f'{n:,}' — group integer digits with commas (no fraction here)."
  [n]
  (let [s (str (long n))
        neg (str/starts-with? s "-")
        digits (if neg (subs s 1) s)
        rev (reverse (vec digits))
        grouped (->> rev
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn report
  "Render the orbital coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [vals* (vals nodes)
        shells (filter #(= ":shell" (get % ":organism/kind")) vals*)
        ops (filter #(= ":operator" (get % ":organism/kind")) vals*)
        hazs (filter #(= ":hazard" (get % ":organism/kind")) vals*)
        svcs (filter #(= ":service" (get % ":organism/kind")) vals*)
        reg-c (counter (map #(get % ":shell/regime") shells))
        op-c (counter (map #(get % ":op/kind") ops))
        hz-c (counter (map #(get % ":hazard/kind") hazs))
        sv-c (counter (map #(get % ":service/kind") svcs))
        L (transient [])]
    (conj! L "# hoshimori 星守 — orbital coverage report\n")
    (conj! L (str "> Honest denominator: hoshimori models orbital SHELLS (regime-aggregate), not "
                  "per-object ephemeris — coverage of all catalogued objects is ~0 BY DESIGN (G1). "
                  "This names the regime/hazard backbone covered and the next-wave gaps.\n"))
    (conj! L (str "**Seed**: " (count shells) " shells · " (count ops) " operators · "
                  (count hazs) " hazards · " (count svcs) " services · " (count edges) " 縁\n"))

    (conj! L "\n## Object-population context (modelled as shells, not objects — by design)\n")
    (conj! L "| denominator | count |")
    (conj! L "|---|---:|")
    (doseq [[name denom] denominators]
      (conj! L (str "| " name " | " (comma denom) " |")))

    (letfn [(bucket [title ks cnt]
              (conj! L (str "\n## " title "\n"))
              (conj! L "| bucket | count | status |")
              (conj! L "|---|---:|:--|")
              (doseq [k ks]
                (let [c (get cnt k 0)
                      status (cond (= c 0) "— **MISSING**"
                                   (< c THIN) "⚠ thin"
                                   :else "ok")]
                  (conj! L (str "| " (lstrip-colon k) " | " c " | " status " |")))))]
      (bucket "Orbital-regime coverage" regimes reg-c)
      (bucket "Operator-kind coverage" op-kinds op-c)
      (bucket "Hazard-kind coverage" hazards hz-c)
      (bucket "Service-kind coverage" services sv-c))

    (let [missing (concat
                   (for [r regimes :when (= 0 (get reg-c r 0))] (lstrip-colon r))
                   (for [o op-kinds :when (= 0 (get op-c o 0))] (lstrip-colon o))
                   (for [h hazards :when (= 0 (get hz-c h 0))] (lstrip-colon h))
                   (for [s services :when (= 0 (get sv-c s 0))] (lstrip-colon s)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (if (seq missing)
        (conj! L (str "Missing buckets: " (str/join ", " missing) "."))
        (conj! L "No fully-missing buckets in the tracked spines.")))
    (conj! L "\n---\n_hoshimori 星守 · ADR-2606073600 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-orbit-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "hoshimori coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
