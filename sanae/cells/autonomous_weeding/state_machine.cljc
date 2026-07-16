(ns sanae.cells.autonomous-weeding.state-machine
  "Phase state machine for the sanae 早苗 autonomous_weeding (草薙) cell.
  1:1 port of cells/autonomous_weeding/state_machine.py (ADR-2606032100).

  Herbicide-free: the only weed-clearing methods are :mechanical and :laser (G9). A pass record
  carries the witness signatures (>=2 robot + >=1 human, G3) and an explicit herbicide-free assertion.
  Pure, unit-tested transitions; the cell's .solve() raises until Council activation.

  Conventions: dataclass WeedingState → a plain map with the SAME string field keys the Python
  `cs.__dict__` round-trips; phase enum value identities stay strings; ValueError → ex-info."
  (:require [clojure.string :as str]))

(def allowed-methods #{"mechanical" "laser"})   ; G9: NO chemical herbicide

;; ── WeedingPhase (enum — Python value identities preserved) ──
(def weeding-phases
  {:init        "init"
   :scanned     "scanned"
   :classified  "classified"
   :weed-cleared "weed_cleared"
   :pass-logged "pass_logged"})

(def phase-init        (:init weeding-phases))
(def phase-scanned     (:scanned weeding-phases))
(def phase-classified  (:classified weeding-phases))
(def phase-weed-cleared (:weed-cleared weeding-phases))
(def phase-pass-logged (:pass-logged weeding-phases))

;; ── WeedingState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"             phase-init
   "parcel_id"         "did:web:sanae.etzhayyim.com/parcel/demo-0001"
   "rows_scanned"      0
   "weeds_detected"    0
   "weeds_cleared"     0
   "method"            "mechanical"
   "herbicide_free"    true
   "robot_sigs"        []
   "human_attestation" ""
   "displaced_cohort_id" ""
   "dividend_attested" false
   "payload"           {}})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- to-int [v] (long (or v 0)))

(defn transition-to-scanned [state]
  (let [cs (cell-state state)
        cs (assoc cs "phase" phase-scanned "rows_scanned" (to-int (get state "rows" 0)))]
    {"cell_state" cs "next_node" "classify"}))

(defn transition-to-classified [state]
  (let [cs (cell-state state)
        cs (assoc cs "phase" phase-classified "weeds_detected" (to-int (get state "weeds_detected" 0)))]
    {"cell_state" cs "next_node" "weed_cleared"}))

(defn transition-to-weed-cleared [state]
  (let [cs (cell-state state)
        method (get state "method" (get cs "method"))]
    (when-not (contains? allowed-methods method)
      ;; G9 enforcement: a non-mechanical/laser method is a constitutional violation
      (throw (ex-info (str "G9 violation: weeding method " (pr-str method) " not in "
                           (pr-str (vec (sort allowed-methods))) " (no herbicide)") {:gate "G9"})))
    (let [detected (get cs "weeds_detected")
          cs (assoc cs
                    "phase" phase-weed-cleared
                    "method" method
                    "herbicide_free" true
                    "weeds_cleared" (min (to-int (get state "weeds_cleared" detected)) detected))]
      {"cell_state" cs "next_node" "pass_logged"})))

(defn transition-to-pass-logged [state]
  (let [cs (cell-state state)
        robot-sigs (vec (get state "robot_sigs" []))
        human (get state "human_attestation" "")
        displaced-cohort-id (get state "displaced_cohort_id" (get cs "displaced_cohort_id"))
        dividend-attested (boolean (get state "dividend_attested" (get cs "dividend_attested")))
        ;; G3 witness quorum: >=2 robot sigs + >=1 human attestation to finalize a pass record
        quorum-ok (and (>= (count robot-sigs) 2) (boolean (seq human)))]
    ;; G2 displacement-dividend coupling (ADR-2606032130): no live displacement without the
    ;; displaced cohort registered for the tenure-weighted dividend. Mirrors hataori's
    ;; finishing_packing/state_machine.cljc transition-to-lot-attested, which enforces the
    ;; same G2 wording from the same ADR pair as a hard refusal on its terminal cell.
    (when (or (not dividend-attested) (empty? displaced-cohort-id))
      (throw (ex-info "G2 violation: displaced cohort not registered for the Displacement Dividend (ADR-2606032130)"
                      {:gate "G2"})))
    (let [cs (assoc cs
                    "phase" phase-pass-logged
                    "robot_sigs" robot-sigs
                    "human_attestation" human
                    "displaced_cohort_id" displaced-cohort-id
                    "dividend_attested" dividend-attested
                    "payload" {"weeding_pass_record"
                               {"parcelId" (get cs "parcel_id")
                                "rowsScanned" (get cs "rows_scanned")
                                "weedsDetected" (get cs "weeds_detected")
                                "weedsCleared" (get cs "weeds_cleared")
                                "method" (get cs "method")
                                "herbicideFree" (get cs "herbicide_free")
                                "witnessQuorumMet" quorum-ok
                                "displacedCohortId" displaced-cohort-id
                                "dividendAttested" dividend-attested}})]
      {"cell_state" cs "next_node" "end"})))

(defn solve
  "R0 scaffold: .solve() raises until Council activation (ADR-2606032100 §Decision)."
  [_input-state]
  (throw (ex-info "sanae R0 scaffold: activate autonomous_weeding via Council ADR (post-2606032100 ratification)"
                  {:scaffold true})))
