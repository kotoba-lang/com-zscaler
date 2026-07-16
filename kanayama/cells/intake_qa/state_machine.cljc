(ns kanayama.cells.intake-qa.state-machine
  "1:1 port of cells/intake_qa/state_machine.py (ADR-2605252400 L1). UBC bale weighing + Cl residue +
  moisture + magnetic-impurity detection; the contamination thresholds gate downstream commitment.
  The IntakeState dataclass is modelled as a string-keyed map under \"intake_state\" (mirroring the
  Python __dict__), phase as its enum value-string."
  (:require [clojure.string]))

;; IntakePhase enum values: init / bale_weighed / contamination_scanned / accept_or_reject_decided / record_emitted
(defn- s* [state] (get state "intake_state" {}))

(defn transition-to-bale-weighed [state]
  {"intake_state" (assoc (s* state) "baleWeightKg" 480.5 "phase" "bale_weighed" "completionPct" 25)
   "next_node" "scan"})

(defn transition-to-contamination-scanned [state]
  {"intake_state" (assoc (s* state)
                         "chlorideResidualPpm" 12.0 "moisturePct" 1.8
                         "magneticImpurityPct" 0.3 "nonAlNonMagneticImpurityPct" 0.7
                         "phase" "contamination_scanned" "completionPct" 65)
   "next_node" "decide"})

(defn transition-to-accept-or-reject-decided [state]
  (let [s (s* state)
        accept (and (< (or (get s "chlorideResidualPpm") 0) 50)
                    (< (or (get s "moisturePct") 0) 5)
                    (< (or (get s "magneticImpurityPct") 0) 1.0)
                    (< (or (get s "nonAlNonMagneticImpurityPct") 0) 2.0))]
    {"intake_state" (assoc s "accept" accept "phase" "accept_or_reject_decided" "completionPct" 90)
     "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"intake_state" s
     "intake_record" {"$type" "com.etzhayyim.kanayama.intakeRecord"
                      "lotId" (get s "lotId")
                      "baleWeightKg" (get s "baleWeightKg")
                      "chlorideResidualPpm" (get s "chlorideResidualPpm")
                      "moisturePct" (get s "moisturePct")
                      "magneticImpurityPct" (get s "magneticImpurityPct")
                      "nonAlNonMagneticImpurityPct" (get s "nonAlNonMagneticImpurityPct")
                      "accept" (get s "accept")
                      "recordedAt" "2026-05-26T08:00:00Z"}
     "next_node" "end"}))
