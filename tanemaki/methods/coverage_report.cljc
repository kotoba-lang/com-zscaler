(ns tanemaki.methods.coverage-report
  "tanemaki 種蒔き — DD-evidence COVERAGE report (ADR-2606122001). 1:1 Clojure port of
  `methods/coverage_report.py`.

  Honest coverage of the stewardship graph: org / screen / criterion / source spread, plus the
  integrity checks that make the steward trustworthy —
    (1) the G1 invariant: no screen-conflicting org routes to :propose;
    (2) the G2 invariant: every instrument node is on the give-only allowlist;
    (3) the G4 rubric: disclosed criterion weights sum to 1.0 and every criterion has a source;
    (4) the G5 honesty: every :meets edge names its public evidence source; every :propose-routed
        org clears ALL screens with full evidence coverage;
    (5) the G6 seed: every seed org is synthetic (a real org in the seed is a violation).
  Coverage of the world's organizations is bounded by design (G5).

  Pure fns; reuses tanemaki.methods.analyze for the loader/analysis. Portable .cljc."
  (:require [clojure.string :as str]
            [tanemaki.methods.analyze :as analyze]))

;; G2 — the disbursement allowlist (mirrors propose.ALLOWED_INSTRUMENTS + :instrument/allowlist
;; in the schema + fuchi G1). propose.py is a separate unported unit; this allowlist is inlined
;; here verbatim (the only symbol coverage_report.py imports from it).
(def ALLOWED-INSTRUMENTS [":grant" ":milestone-escrow" ":in-kind"])

(def ORG-FORMS [":nonprofit" ":cooperative" ":foundation" ":oss-project" ":company" ":unincorporated"])
(def ROUTE-BUCKETS [":propose" ":insufficient-evidence" ":excluded"])
(def THIN 2)

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- counter
  "Counter(seq) → map value->count, mirroring collections.Counter (nil keys allowed)."
  [coll]
  (reduce (fn [m v] (update m v (fnil inc 0))) {} coll))

(defn- ->float
  [v]
  (if (or (nil? v) (false? v)) 0.0 (double v)))

(defn- fmt2 [v] (format "%.2f" (double v)))

(defn- fmt-pct0
  "Python f'{x:.0%}'."
  [x]
  (str (long (Math/rint (* (double x) 100.0))) "%"))

(defn report
  "Render the DD coverage-report markdown (1:1 with coverage_report.report)."
  [nodes edges]
  (let [res (analyze/analyze nodes edges)
        vals* (vals nodes)
        orgs (filter #(= ":org" (get % ":fs/kind")) vals*)
        screens (filter #(= ":screen" (get % ":fs/kind")) vals*)
        crit (filter #(= ":criterion" (get % ":fs/kind")) vals*)
        sources (filter #(= ":source" (get % ":fs/kind")) vals*)
        instruments (filter #(= ":instrument" (get % ":fs/kind")) vals*)
        orgs-map (get res "orgs")

        form-c (counter (map #(get % ":org/form") orgs))
        route-c (counter (map #(get % "route") (vals orgs-map)))

        ;; integrity (org iteration order = res["orgs"] dict order = sorted org id)
        org-keys (or (:tanemaki.methods.analyze/order (meta orgs-map)) (keys orgs-map))
        g1-violations (vec (for [oid org-keys
                                 :let [r (get orgs-map oid)]
                                 :when (and (= ":propose" (get r "route"))
                                            (seq (get r "conflicts")))]
                             oid))
        bad-instruments (vec (for [i instruments
                                   :when (not (some #{(get i ":instrument/kind")} ALLOWED-INSTRUMENTS))]
                               (get i ":fs/id")))
        weights-sum (reduce + 0.0 (map #(->float (get % ":criterion/weight")) crit))
        sourced (set (for [e edges :when (= ":sourced-from" (get e ":en/kind"))]
                       (get e ":en/from")))
        unsourced-criteria (vec (sort (for [c crit :when (not (contains? sourced (get c ":fs/id")))]
                                        (get c ":fs/id"))))
        anonymous-evidence (vec (for [e edges
                                      :when (and (= ":meets" (get e ":en/kind"))
                                                 (not (get e ":en/evidence")))]
                                  (str (get e ":en/from") "→" (get e ":en/to"))))
        nonsynthetic (vec (sort (for [o orgs :when (not (true? (get o ":org/synthetic")))]
                                  (get o ":fs/id"))))

        L (transient [])]
    (conj! L "# tanemaki 種蒔き — DD coverage report\n")
    (conj! L (str "> Honest denominator: coverage of the world's organizations is bounded by design "
                  "(G5). Every org in this seed is FICTIONAL (G6) — real-org DD is a G7-gated live "
                  "leg. tanemaki is a steward, never a sovereign (G1): the vote decides.\n"))
    (conj! L (str "**Seed**: " (count orgs) " orgs · " (count screens) " screens · "
                  (count crit) " criteria · " (count sources) " sources · "
                  (count instruments) " instruments · " (count edges) " 縁 · "
                  "evidence floor " (fmt-pct0 analyze/COVERAGE-FLOOR) "\n"))

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
      (bucket "Org-form coverage" ORG-FORMS form-c)
      (bucket "Route exercise (all three lanes must be exercised by the seed)" ROUTE-BUCKETS route-c))

    (conj! L "\n## Integrity — the steward's own invariants\n")
    (conj! L (str "- G1 (no conflicted org proposable): "
                  (if (empty? g1-violations)
                    "**holds for all orgs** ✓"
                    (str "**VIOLATED** by " (str/join ", " g1-violations) " ✗"))))
    (conj! L (str "- G2 (give-only instruments): "
                  (if (empty? bad-instruments)
                    "**all instruments on the allowlist** ✓"
                    (str "**VIOLATED** by " (str/join ", " bad-instruments) " ✗"))))
    (conj! L (str "- G4 (rubric weights Σ = 1.0): **" (fmt2 weights-sum) "** "
                  (if (< (Math/abs (- weights-sum 1.0)) 1e-9) "✓" "✗")))
    (conj! L (str "- G4 (every criterion has a disclosed source): "
                  "**" (- (count crit) (count unsourced-criteria)) "/" (count crit) "**"
                  (if (empty? unsourced-criteria) "" (str " (unsourced: " (str/join ", " unsourced-criteria) ")"))))
    (conj! L (str "- G5 (every evidence edge names its source): "
                  (if (empty? anonymous-evidence)
                    "**all named** ✓"
                    (str "**anonymous**: " (str/join ", " anonymous-evidence) " ✗"))))
    (conj! L (str "- G6 (seed orgs all synthetic): "
                  (if (empty? nonsynthetic)
                    "**all fictional** ✓"
                    (str "**REAL ORG IN SEED**: " (str/join ", " nonsynthetic) " ✗"))))

    (let [miss-form (vec (for [f ORG-FORMS :when (= 0 (get form-c f 0))] (lstrip-colon f)))]
      (conj! L "\n## Gap map — next-wave targets\n")
      (conj! L (if (seq miss-form)
                 (str "Missing org-form buckets: " (str/join ", " miss-form) ".")
                 "No fully-missing org-form buckets (thin buckets still listed above).")))
    (conj! L "\n---\n_tanemaki 種蒔き · ADR-2606122001 · coverage honesty (G5)._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: render coverage-report.md from a seed EDN graph."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-stewardship-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (analyze/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage-report.md") (report nodes edges))
       (println (str "tanemaki coverage → " (clojure.java.io/file outdir "coverage-report.md")))
       0)))
