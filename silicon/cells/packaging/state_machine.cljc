(ns silicon.cells.packaging.state-machine
  "Packaging state machine — 1:1 port of cells/packaging/state_machine.py (ADR-2605242500).
  Deterministic R0 mock-data phase machine (INIT → … → PACKAGE_TESTED, completionPct 0→100)."
  (:require [clojure.string]))

(def state-defaults {"phase" "init" "packageId" "PKG-DEMO-0001" "completionPct" 0})
(defn- cs [state] (merge state-defaults (get state "packaging_state" {})))

(defn transition-to-die-attached [state]
  {"packaging_state" (assoc (cs state) "phase" "die_attached" "completionPct" 25
                            "dieAttachData" {"die_size_mm" 5.2 "substrate_material" "FR4" "adhesive_type" "epoxy"
                                             "cure_temperature_c" 150 "cure_time_hours" 2 "die_placement_accuracy_um" 50})
   "next_node" "wire_bond"})

(defn transition-to-wire-bonding-complete [state]
  {"packaging_state" (assoc (cs state) "phase" "wire_bonding_complete" "completionPct" 50
                            "wireBondData" {"wire_material" "gold" "wire_diameter_um" 25 "bond_count" 256
                                            "bond_pull_force_grams" 8.5 "bond_shear_force_grams" 6.2 "bond_quality_pass_rate_pct" 99.8})
   "next_node" "encapsulate"})

(defn transition-to-encapsulation-complete [state]
  {"packaging_state" (assoc (cs state) "phase" "encapsulation_complete" "completionPct" 75
                            "encapsulationData" {"encapsulant_material" "epoxy_mold_compound" "mold_temperature_c" 175
                                                 "mold_pressure_bar" 85 "mold_time_seconds" 120 "encapsulant_thickness_mm" 1.8
                                                 "voids_detected_pct" 0.2 "moisture_absorption_pct" 0.15})
   "next_node" "final_test"})

(def ^:private mock-sigs
  [{"robotDid" "did:web:etzhayyim.com:otete-unit-5" "role" "packaging_executor"
    "timestamp" "2026-05-26T17:15:45Z" "signature" "vV1wW2xX3yY4zZ5a..."}
   {"robotDid" "did:web:etzhayyim.com:mimi-unit-4" "role" "package_inspector"
    "timestamp" "2026-05-26T17:15:50Z" "signature" "bB6cC7dD8eE9fF0g..."}])

(defn transition-to-package-tested [state]
  (let [final {"visual_inspection" "pass" "dimensional_check" "pass" "electrical_continuity" "pass"
               "temperature_cycling" "pass" "humidity_stress" "pass" "package_quality_grade" "A"}
        c (assoc (cs state) "phase" "package_tested" "finalTestData" final "robotSignatures" mock-sigs "completionPct" 100)]
    {"packaging_state" c
     "packaging_record" {"packageId" (get c "packageId") "dieAttach" (get c "dieAttachData")
                         "wireBond" (get c "wireBondData") "encapsulation" (get c "encapsulationData")
                         "finalTest" (get c "finalTestData") "attestingRobots" mock-sigs}
     "next_node" "end"}))

(defn solve [_input-state]
  (throw (ex-info "silicon R0 scaffold: activate packaging via Council ADR (post-2605242500 ratification)" {:scaffold true})))
