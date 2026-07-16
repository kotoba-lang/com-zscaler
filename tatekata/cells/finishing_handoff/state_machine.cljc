(ns tatekata.cells.finishing-handoff.state-machine
  "Finishing-handoff state machine — ADR-2605250715 §2 (Phase 4). 1:1 cljc port of
  `cells/finishing_handoff/state_machine.py`. Surface prep → drywall → paint →
  trim → ≥2-robot witness → finishing record. String keys mirror the Python
  dataclass __dict__ so the emitted record is byte-identical.")

(def phases
  {:init "init" :surfaces-prepped "surfaces_prepped" :drywall-complete "drywall_complete"
   :paint-complete "paint_complete" :trim-installed "trim_installed"
   :witness-wait "witness_wait" :complete "complete"})

(defn init [state]
  {"finishing_state" {"phase" (:init phases)
                      "projectId" (get state "projectId" "unknown")
                      "completionPct" 0}})

(defn transition-to-prep-complete [state]
  (let [fs (-> (get state "finishing_state" {})
               (assoc "prepSummary" {"floor_area_m2" 850 "drywall_sheets_installed" 120
                                     "tape_lengths_m" 1200 "mud_coats_applied" 3 "joint_smoothness_grit" 120}
                      "phase" (:surfaces-prepped phases) "completionPct" 20))]
    {"finishing_state" fs "next_node" "drywall"}))

(defn transition-to-drywall-complete [state]
  (let [fs (-> (get state "finishing_state" {})
               (assoc "drywallSummary" {"drywall_inspection_passed" true "sanding_dust_levels_mg_m3" 2.5
                                        "final_surface_smoothness" "ready_for_paint"}
                      "phase" (:drywall-complete phases) "completionPct" 40))]
    {"finishing_state" fs "next_node" "paint"}))

(defn transition-to-paint-complete [state]
  (let [fs (-> (get state "finishing_state" {})
               (assoc "paintSummary" {"primer_coverage_m2" 850 "finish_coats_applied" 2
                                      "paint_type" "low_voc_latex" "cure_time_hours" 24
                                      "surface_finish_gloss" "eggshell"}
                      "phase" (:paint-complete phases) "completionPct" 70))]
    {"finishing_state" fs "next_node" "trim"}))

(defn transition-to-trim-installed [state]
  (let [fs (-> (get state "finishing_state" {})
               (assoc "trimSummary" {"baseboard_length_m" 520 "door_casings_installed" 12
                                     "window_casings_installed" 8 "crown_molding_length_m" 180
                                     "nail_spacing_mm" 300 "caulk_application_complete" true}
                      "phase" (:trim-installed phases) "completionPct" 85))]
    {"finishing_state" fs "next_node" "witness"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:giemon-unit-1" "role" "prep_executor"
    "timestamp" "2026-05-26T17:45:00Z" "signature" "qM8nR3sT6uV9wX..."}
   {"robotDid" "did:web:etzhayyim.com:mimi-unit-3" "role" "finishing_inspector"
    "timestamp" "2026-05-26T17:45:05Z" "signature" "yL2aB5cD8eF1gH..."}])

(defn transition-to-witness-attestation [state]
  (let [fs (-> (get state "finishing_state" {})
               (assoc "robotSignatures" robot-sigs "phase" (:witness-wait phases) "completionPct" 95))]
    {"finishing_state" fs "next_node" "emit"}))

(defn emit-finishing-record [state]
  (let [fs0 (get state "finishing_state" {})
        record {"projectId" (get fs0 "projectId")
                "phase" "finishing_handoff"
                "completionPct" (get fs0 "completionPct")
                "recordedDate" "2026-05-26T17:45:30Z"
                "prepSummary" (get fs0 "prepSummary")
                "drywallSummary" (get fs0 "drywallSummary")
                "paintSummary" (get fs0 "paintSummary")
                "trimSummary" (get fs0 "trimSummary")
                "attestingRobots" (mapv #(select-keys % ["robotDid" "role" "timestamp" "signature"])
                                        (get fs0 "robotSignatures" []))}
        fs (assoc fs0 "phase" (:complete phases) "completionPct" 100)]
    {"finishing_state" fs "finishing_record" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-prep-complete transition-to-drywall-complete
           transition-to-paint-complete transition-to-trim-installed
           transition-to-witness-attestation emit-finishing-record]))
