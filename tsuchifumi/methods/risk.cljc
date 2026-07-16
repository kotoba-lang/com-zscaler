#!/usr/bin/env bb
;; tsuchifumi 土踏み — risk register + Meadows leverage points (clj-native, pure stdlib).
(ns tsuchifumi.methods.risk
  "tsuchifumi 土踏み — risk analysis of the earthing-EMF gap (ADR-2606212000).

  Turns the seed's risk DRIVERS into a register + a Meadows leverage-point ranking.

  Per driver:
    raw-severity   = likelihood · impact                          (exposure, pre-evidence)
    risk-score     = likelihood · impact · tier-weight            (evidence-DISCOUNTED, G2)
    leverage-str   = (13 − meadows-level) / 12 ∈ (0,1]            (lower level = stronger)
    leverage-prio  = leverage-str · impact · tier-weight          (where to intervene first)

  Honesty (G2): the risk-score DISCOUNTS by evidence tier, so a :contested driver
  cannot dominate the register on assertion alone; the raw-severity is shown beside
  it so nothing is hidden. The leverage ranking deliberately surfaces HIGH-leverage,
  WELL-EVIDENCED, NO-REGRET interventions (institutional grounding/greenspace
  standards) over fear-based ones — Meadows' point that the strongest leverage is in
  goals/rules/paradigm, not in tuning parameters.

  Severity band (raw): ≥0.5 :critical · ≥0.3 :high · ≥0.15 :medium · else :low.

  This is OBSERVATION + leverage analysis, never a directive to act on any person (G1)."
  (:require [clojure.string :as str]
            [tsuchifumi.methods.tsuchifumi-edn :as te]))

(def tier-weights {:established 1.0 :emerging 0.6 :contested 0.35 :anecdotal 0.15})

(defn- num [x] (double (or x 0)))
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))
(defn- tw [t] (get tier-weights t 0.0))

(defn raw-severity [d] (* (num (:likelihood d)) (num (:impact d))))
(defn risk-score [d] (* (raw-severity d) (tw (:evidence-tier d))))

(defn severity-band [d]
  (let [s (raw-severity d)]
    (cond (>= s 0.5) :critical (>= s 0.3) :high (>= s 0.15) :medium :else :low)))

(defn leverage-strength
  "Meadows leverage strength ∈ (0,1] — lower intervention level = stronger leverage."
  [d]
  (let [lvl (int (or (:leverage d) 12))]
    (round3 (/ (- 13.0 (max 1 (min 12 lvl))) 12.0))))

(defn leverage-band [d]
  (let [lvl (int (or (:leverage d) 12))]
    (cond (<= lvl 3) :paradigm-goal (<= lvl 6) :structure-rules (<= lvl 9) :feedback :else :parameter)))

(defn leverage-priority [d]
  (round3 (* (leverage-strength d) (num (:impact d)) (tw (:evidence-tier d)))))

(defn assess-driver [d]
  {"id" (:id d) "name" (:name d)
   "likelihood" (num (:likelihood d)) "impact" (num (:impact d))
   "evidence_tier" (:evidence-tier d)
   "raw_severity" (round3 (raw-severity d))
   "risk_score" (round3 (risk-score d))
   "severity_band" (severity-band d)
   "meadows_level" (int (or (:leverage d) 12))
   "leverage_band" (leverage-band d)
   "leverage_strength" (leverage-strength d)
   "leverage_priority" (leverage-priority d)
   "reversibility" (:reversibility d)})

(defn assess [drivers]
  (let [rows (mapv assess-driver drivers)]
    {"register" (vec (sort-by #(- (get % "risk_score")) rows))
     "leverage_points" (vec (sort-by #(- (get % "leverage_priority")) rows))
     "severity_tally" (frequencies (map #(get % "severity_band") rows))
     "leverage_tally" (frequencies (map #(get % "leverage_band") rows))}))

;; ── datom emission (append-only EAVT; flagged) ───────────────────────────────
(defn- add [e a v] [":db/add" e a v])

(defn datoms [{:strs [register]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "tsuchifumi-risk:" (get r "id"))]
        [(add e ":tsuchifumi.risk/likelihood" (round3 (get r "likelihood")))
         (add e ":tsuchifumi.risk/impact" (round3 (get r "impact")))
         (add e ":tsuchifumi.risk/raw-severity" (get r "raw_severity"))
         (add e ":tsuchifumi.risk/risk-score" (get r "risk_score"))
         (add e ":tsuchifumi.risk/severity-band" (str (get r "severity_band")))
         (add e ":tsuchifumi.risk/evidence-tier" (str (get r "evidence_tier")))
         (add e ":tsuchifumi.risk/meadows-level" (get r "meadows_level"))
         (add e ":tsuchifumi.risk/leverage-band" (str (get r "leverage_band")))
         (add e ":tsuchifumi.risk/leverage-priority" (get r "leverage_priority"))
         (add e ":tsuchifumi/sourcing" ":synthetic")
         (add e ":tsuchifumi/derived" true)]))
    register)))

(defn render-datoms [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── markdown risk report ─────────────────────────────────────────────────────
(defn render-report [assessment]
  (str
   "# tsuchifumi 土踏み — earthing-EMF RISK register + leverage points\n\n"
   "Risk DRIVERS of the earthing/EMF Wellbecoming gap. **risk-score DISCOUNTS by "
   "evidence tier (G2)** — a :contested driver cannot dominate on assertion alone; the "
   "raw-severity is shown beside it. The leverage ranking (Meadows) surfaces HIGH-leverage, "
   "WELL-EVIDENCED, NO-REGRET interventions first. Observation only, never a directive on "
   "any person (G1). All :synthetic.\n\n"
   "**Severity:** " (pr-str (get assessment "severity_tally")) " · "
   "**Leverage bands:** " (pr-str (get assessment "leverage_tally")) "\n\n"
   "## Risk register (by evidence-discounted risk-score)\n\n"
   "| driver | L | I | tier | raw-sev | risk-score | band | reversibility |\n"
   "|---|---|---|---|---|---|---|---|\n"
   (str/join "\n"
             (for [r (get assessment "register")]
               (str "| " (get r "name")
                    " | " (get r "likelihood") " | " (get r "impact")
                    " | " (name (get r "evidence_tier"))
                    " | " (get r "raw_severity")
                    " | " (get r "risk_score")
                    " | " (name (get r "severity_band"))
                    " | " (name (get r "reversibility")) " |")))
   "\n\n## Leverage points (Meadows — where to intervene first)\n\n"
   "| driver | Meadows lvl | band | strength | leverage-priority |\n"
   "|---|---|---|---|---|\n"
   (str/join "\n"
             (for [r (get assessment "leverage_points")]
               (str "| " (get r "name")
                    " | " (get r "meadows_level")
                    " | " (name (get r "leverage_band"))
                    " | " (get r "leverage_strength")
                    " | " (get r "leverage_priority") " |")))
   "\n\n_The top leverage point is the INSTITUTIONAL one: grounding + greenspace access "
   "standards (Meadows structure/rules) — a no-regret, established-evidence intervention. "
   "Routed to ossekai (御節介) as a transparent proposal, never a fear appeal (G4)._\n"))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsuchifumi/kotoba/seed.edn")
           drivers (vec (filter #(= (:type %) :driver) (te/reconstitute-rows (clojure.edn/read-string (slurp seed)))))
           a (assess drivers)]
       (println (render-report a))
       (println (str "-- " (count drivers) " drivers assessed --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
