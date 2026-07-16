(ns tatekata.cells.commissioning.state-machine
  "Commissioning state machine — ADR-2605250715 §2 (Phase 5). 1:1 cljc port of
  `cells/commissioning/state_machine.py`. Final systems test → defect walkdown →
  waste inventory → sign-off (≥2 robot + human) → project closure. String keys
  mirror the Python dataclass __dict__ so the emitted record is byte-identical.")

(def phases
  {:init "init" :systems-tested "systems_tested" :defects-identified "defects_identified"
   :waste-logged "waste_logged" :signed-off "signed_off" :complete "complete"})

(defn init [state]
  {"commissioning_state" {"phase" (:init phases)
                          "projectId" (get state "projectId" "unknown")
                          "completionPct" 0}})

(defn transition-to-systems-tested [state]
  (let [cs (-> (get state "commissioning_state" {})
               (assoc "systemsTestResults"
                      {"hvac_airflow_cfm" 4450 "hvac_spec_cfm" 4500 "hvac_test_passed" true
                       "electrical_load_amps" 145 "electrical_spec_amps" 200 "electrical_test_passed" true
                       "water_pressure_bar" 2.4 "water_spec_bar" 2.5 "water_test_passed" true
                       "gas_odor_detection" false "gas_test_passed" true}
                      "phase" (:systems-tested phases) "completionPct" 25))]
    {"commissioning_state" cs "next_node" "walkdown"}))

(defn transition-to-defect-walkdown [state]
  (let [cs (-> (get state "commissioning_state" {})
               (assoc "defectWalkdown" {"cracks_foundation" 0 "cracks_drywall" 2 "paint_blemishes" 1
                                        "trim_gaps_mm" 3 "door_operation" "smooth" "window_operation" "smooth"
                                        "hardware_finish" "acceptable"}
                      "punchList" ["Fill 2 small drywall cracks with spackle and sand"
                                   "Touch up 1 paint blemish in bedroom"]
                      "phase" (:defects-identified phases) "completionPct" 50))]
    {"commissioning_state" cs "next_node" "waste"}))

(defn transition-to-waste-inventory [state]
  (let [cs (-> (get state "commissioning_state" {})
               (assoc "wasteInventory" {"drywall_scrap_kg" 240 "drywall_reused_pct" 85 "drywall_landfill_pct" 15
                                        "metal_scrap_kg" 85 "metal_recycled_pct" 95 "metal_landfill_pct" 5
                                        "wood_scrap_kg" 120 "wood_reused_pct" 60 "wood_chipped_pct" 30 "wood_landfill_pct" 10
                                        "hazardous_waste_kg" 5 "hazardous_disposal_compliant" true}
                      "phase" (:waste-logged phases) "completionPct" 75))]
    {"commissioning_state" cs "next_node" "signoff"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:mimi-unit-3" "role" "final_inspector"
    "timestamp" "2026-05-26T18:30:00Z" "signature" "zI4jK7lM0nO3pQ..."}
   {"robotDid" "did:web:etzhayyim.com:giemon-unit-1" "role" "site_supervisor"
    "timestamp" "2026-05-26T18:30:05Z" "signature" "rS6tU9vW2xY5zA..."}])

(defn transition-to-project-signoff [state]
  (let [cs (-> (get state "commissioning_state" {})
               (assoc "robotSignatures" robot-sigs "phase" (:signed-off phases) "completionPct" 95))]
    {"commissioning_state" cs "next_node" "emit"}))

(defn emit-project-closure-record [state]
  (let [cs0 (get state "commissioning_state" {})
        record {"projectId" (get cs0 "projectId")
                "phase" "commissioning"
                "completionPct" 100
                "closureDate" "2026-05-26T18:30:30Z"
                "systemsTestResults" (get cs0 "systemsTestResults")
                "punchList" (get cs0 "punchList")
                "wasteInventory" (get cs0 "wasteInventory")
                "attestingRobots" (mapv #(select-keys % ["robotDid" "role" "timestamp" "signature"])
                                        (get cs0 "robotSignatures" []))}
        cs (assoc cs0 "phase" (:complete phases) "completionPct" 100)]
    {"commissioning_state" cs "project_closure_record" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-systems-tested transition-to-defect-walkdown
           transition-to-waste-inventory transition-to-project-signoff
           emit-project-closure-record]))
