#!/usr/bin/env bb
;; tsuchifumi 土踏み — the earthing-EMF Wellbecoming relief GATE (clj-native, pure stdlib).
(ns tsuchifumi.methods.analyze
  "tsuchifumi 土踏み — bioelectric-Wellbecoming relief gate (ADR-2606212000).

  Scores, ON READ, two well-MEASURED quantities per region (no stored score):

    exposure-load    = 0.40·ambient-emf + 0.35·device-hours + 0.25·indoor-fraction
    earthing-deficit = 1 − (0.30·barefoot-access + 0.25·greenspace-access
                            + 0.15·grounded-building + 0.15·(1−footwear-insulation)
                            + 0.15·grounding-policy)

  exposure-load and earthing-deficit are EXPOSURE/ACCESS facts (:established/:emerging).
  The hypothesized HEALTH burden = exposure-load · earthing-deficit is reported ONLY
  with an explicit :evidence-tier + confidence drawn from the resting causal pathway:

    dominant pathway of the burden → its evidence tier (G2, the honesty invariant):
      ambient-emf dominant → :contested  (non-thermal EMF harm is NOT established)
      device-hours dominant → :emerging  (evening-light/circadian → sleep; routes suimin)
      access-deficit dominant → :established (greenspace/outdoor-time wellbeing)
    ties prefer the BETTER-evidenced pathway (established > emerging > contested).

  A :contested/:anecdotal burden is NEVER asserted as established harm — it routes to
  :await-evidence. The institutional earthing-deficit, by contrast, ALWAYS rests on
  established greenspace/outdoor-time evidence, so a high deficit is actionable as a
  no-regret access proposal regardless of the EMF debate.

  verdict ∈ {:await-consent :relief-priority :infrastructure-gap :await-evidence :monitor}
  Order:
    1. consent absent                                              → :await-consent     (G4)
    2. exposure ≥ exp-thr AND deficit ≥ def-thr AND tier ≥ :emerging → :relief-priority
    3. deficit ≥ def-thr                                           → :infrastructure-gap
    4. burden ≥ burden-thr AND tier ∈ {:contested :anecdotal}      → :await-evidence    (G2)
    5. else                                                        → :monitor

  OBSERVATORY + MODEL + NUDGE only — NON-DIAGNOSTIC, NON-THERAPEUTIC, sells NOTHING.
  No :tsuchifumi/diagnose / :treat / :product / :person.* datom is ever emitted."
  (:require [clojure.string :as str]
            [tsuchifumi.methods.tsuchifumi-edn :as te]))

;; ── thresholds + weights ─────────────────────────────────────────────────────
(def ^:private exp-thr 0.55)
(def ^:private def-thr 0.6)
(def ^:private burden-thr 0.3)

(def tier-weights {:established 1.0 :emerging 0.6 :contested 0.35 :anecdotal 0.15})
(def ^:private tier-rank {:established 4 :emerging 3 :contested 2 :anecdotal 1 nil 0})

(defn- num [x] (double (or x 0)))
(defn- clamp01 [x] (max 0.0 (min 1.0 (double x))))
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

;; ── pure scoring (on read) ───────────────────────────────────────────────────
(defn exposure-load
  "Composite ambient-EMF + device-hours + indoor-fraction ∈ 0..1 (a MEASURED
  exposure fact, :established/:emerging)."
  [r]
  (clamp01 (+ (* 0.40 (num (:ambient-emf r)))
              (* 0.35 (num (:device-hours r)))
              (* 0.25 (num (:indoor-fraction r))))))

(defn earthing-deficit
  "1 − weighted earthing access/institution ∈ 0..1 — the INSTITUTIONAL gap (a MEASURED
  access fact resting on established greenspace/outdoor-time wellbeing evidence)."
  [r]
  (clamp01 (- 1.0 (+ (* 0.30 (num (:barefoot-access r)))
                     (* 0.25 (num (:greenspace-access r)))
                     (* 0.15 (num (:grounded-building r)))
                     (* 0.15 (- 1.0 (num (:footwear-insulation r))))
                     (* 0.15 (num (:grounding-policy r)))))))

(defn health-burden
  "The HYPOTHESIZED bioelectric health burden = exposure-load · earthing-deficit ∈ 0..1.
  Reported ONLY alongside its resting evidence tier (see `resting-tier`) — never as an
  established harm (G2)."
  [r]
  (clamp01 (* (exposure-load r) (earthing-deficit r))))

(defn resting-tier
  "The evidence TIER of the burden's DOMINANT causal pathway (G2). The pathway with the
  largest contribution decides the tier; ties prefer the better-evidenced pathway."
  [r]
  (let [paths [[(num (:ambient-emf r))  :contested  1]   ; non-thermal EMF harm — NOT established
               [(num (:device-hours r)) :emerging   2]   ; evening light → circadian/sleep
               [(earthing-deficit r)    :established 3]]] ; greenspace/outdoor-time wellbeing
    (->> paths
         ;; sort by contribution desc, then by tier-rank desc (better evidence wins ties)
         (sort-by (fn [[c t _]] [(- c) (- (tier-rank t))]))
         first second)))

(defn confidence
  "Confidence ∈ 0..1 in the health-burden claim = the weight of its resting tier (G2)."
  [r]
  (get tier-weights (resting-tier r) 0.0))

(defn verdict
  "The relief verdict for a region. Returns {:verdict … :reason … :route … :tier …}."
  [r]
  (let [el (exposure-load r) df (earthing-deficit r) b (health-burden r)
        tier (resting-tier r)
        strong? (>= (tier-rank tier) (tier-rank :emerging))]
    (cond
      (not (:consent r))
      {:verdict :await-consent :tier tier :route :consent-first}

      (and (>= el exp-thr) (>= df def-thr) strong?)
      {:verdict :relief-priority :tier tier
       :route (case tier
                :established :greenspace-grounded-design
                :emerging   :evening-light-outdoor      ; → suimin
                :greenspace-grounded-design)}

      (>= df def-thr)
      {:verdict :infrastructure-gap :tier tier :route :greenspace-grounded-design}

      (and (>= b burden-thr) (#{:contested :anecdotal} tier))
      {:verdict :await-evidence :reason :contested-pathway :tier tier :route :evidence-synthesis}

      :else
      {:verdict :monitor :tier tier :route :observe})))

;; ── per-region assessment ────────────────────────────────────────────────────
(defn assess-region [r]
  (let [vd (verdict r)]
    (merge {"id" (:id r) "name" (:name r) "kind" (:kind r)
            "population_weight" (num (:population-weight r))
            "exposure_load" (exposure-load r)
            "earthing_deficit" (earthing-deficit r)
            "health_burden" (health-burden r)
            "confidence" (confidence r)
            "evidence_tier" (:tier vd)
            "verdict" (:verdict vd)
            "route" (:route vd)}
           (when (:reason vd) {"reason" (:reason vd)}))))

(defn relief-gap
  "Population-weighted institutional access gap, ranked — the actionable RELIEF worklist
  (greenspace / grounded-design / outdoor-time). The no-regret target, NOT a harm list."
  [rows]
  (->> rows
       (map (fn [r] {"id" (get r "id") "name" (get r "name")
                     "gap" (round3 (* (get r "earthing_deficit") (get r "population_weight")))
                     "earthing_deficit" (round3 (get r "earthing_deficit"))}))
       (sort-by #(- (get % "gap")))
       vec))

(defn assess
  "Assess all regions. evidence-rows (optional) feed the honesty summary."
  ([regions] (assess regions []))
  ([regions evidence-rows]
   (let [rows (mapv assess-region regions)
         tally (frequencies (map #(get % "verdict") rows))]
     {"regions" rows
      "tally" tally
      "relief_gap" (relief-gap rows)
      "relief_priority"    (count (filter #(= :relief-priority (get % "verdict")) rows))
      "infrastructure_gap" (count (filter #(= :infrastructure-gap (get % "verdict")) rows))
      "await_evidence"     (count (filter #(= :await-evidence (get % "verdict")) rows))
      "await_consent"      (count (filter #(= :await-consent (get % "verdict")) rows))
      "monitored"          (count (filter #(= :monitor (get % "verdict")) rows))
      "evidence_summary"   (frequencies (map :tier evidence-rows))})))

;; ── datom emission (append-only EAVT; flagged) ───────────────────────────────
(defn- add [e a v] [":db/add" e a v])

(defn datoms
  "Append-only EAVT datom VECTORS for the relief verdicts. Every datom flagged
  :tsuchifumi/derived + :tsuchifumi/sourcing :synthetic. NO :tsuchifumi/diagnose /
  :treat / :product / :tsuchifumi.person.* attribute is ever emitted (G1/G3/G5).
  The health-burden datom is ALWAYS paired with :tsuchifumi.rel/evidence-tier +
  :confidence so it can never be read as an established harm (G2)."
  [{:strs [regions]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "tsuchifumi-region:" (get r "id"))]
        (concat
         [(add e ":tsuchifumi.region/kind" (str (get r "kind")))
          (add e ":tsuchifumi.rel/exposure-load" (round3 (get r "exposure_load")))
          (add e ":tsuchifumi.rel/earthing-deficit" (round3 (get r "earthing_deficit")))
          (add e ":tsuchifumi.rel/health-burden" (round3 (get r "health_burden")))
          (add e ":tsuchifumi.rel/evidence-tier" (str (get r "evidence_tier")))
          (add e ":tsuchifumi.rel/confidence" (round3 (get r "confidence")))
          (add e ":tsuchifumi.rel/verdict" (str (get r "verdict")))
          (add e ":tsuchifumi.rel/route" (str (get r "route")))]
         (when (get r "reason") [(add e ":tsuchifumi.rel/reason" (str (get r "reason")))])
         [(add e ":tsuchifumi/sourcing" ":synthetic")
          (add e ":tsuchifumi/derived" true)])))
    regions)))

(defn render-datoms [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── markdown relief map (a relief worklist, never a harm/target list) ────────
(defn render-report [assessment]
  (let [rows (->> (get assessment "regions") (sort-by #(- (get % "earthing_deficit"))))]
    (str
     "# tsuchifumi 土踏み — earthing-EMF Wellbecoming RELIEF map\n\n"
     "アーシング (earthing) under-institutionalization × ambient-EMF exposure, scored "
     "ON READ and routed to RELIEF (拡充). **OBSERVATORY + MODEL + NUDGE — NON-DIAGNOSTIC, "
     "NON-THERAPEUTIC, sells nothing (ADR-2606212000).** A relief worklist, NEVER a harm "
     "claim / target list. All regions are :synthetic.\n\n"
     "**Evidence honesty (G2):** exposure-load + earthing-deficit are MEASURED facts; the "
     "health-burden is a HYPOTHESIS reported only with its resting evidence tier. A "
     ":contested/:anecdotal burden is never asserted as harm (→ :await-evidence). The "
     "institutional access gap rests on ESTABLISHED greenspace/outdoor-time evidence, so it "
     "is always a valid no-regret target.\n\n"
     "Routes: **" (get assessment "relief_priority") "** relief-priority · **"
     (get assessment "infrastructure_gap") "** infrastructure-gap · **"
     (get assessment "await_evidence") "** await-evidence · **"
     (get assessment "await_consent") "** await-consent · **"
     (get assessment "monitored") "** monitor.\n\n"
     "| region | kind | exposure | deficit | burden(hyp) | tier | conf | verdict | route |\n"
     "|---|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (name (get r "kind"))
                      " | " (round3 (get r "exposure_load"))
                      " | " (round3 (get r "earthing_deficit"))
                      " | " (round3 (get r "health_burden"))
                      " | " (name (get r "evidence_tier"))
                      " | " (round3 (get r "confidence"))
                      " | " (name (get r "verdict"))
                      " | " (name (get r "route")) " |")))
     "\n\n_Routed to: ossekai (御節介, carries the consent-bound nudge) · suimin (睡眠, "
     "evening-light/circadian evidence) · mitooshi (見通し, forecasting) · inochi/iyashi/"
     "mitate (care, never tsuchifumi)._\n"
     "\n_Even :relief-priority is a PROPOSAL — the nudge is carried by ossekai with member "
     "consent + on-chain log; tsuchifumi never acts on a person (G1/G4)._\n")))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsuchifumi/kotoba/seed.edn")
           rows (te/reconstitute-rows (clojure.edn/read-string (slurp seed)))
           regions (vec (filter #(= (:type %) :region) rows))
           evidence (vec (filter #(= (:type %) :evidence) rows))
           a (assess regions evidence)]
       (println (render-report a))
       (println (str "-- " (count regions) " regions assessed; evidence "
                     (get a "evidence_summary") " --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
