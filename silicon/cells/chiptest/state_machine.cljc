(ns silicon.cells.chiptest.state-machine
  "Chip testing state machine — 1:1 port of cells/chiptest/state_machine.py (ADR-2605242500).
  Deterministic R0 mock-data phase machine (INIT → … → CHIP_GRADED, completionPct 0→100).
  .solve() raises until Council Lv6+ activation."
  (:require [clojure.string]))

(def state-defaults {"phase" "init" "dieId" "DIE-DEMO-0001" "completionPct" 0})
(defn- cs [state] (merge state-defaults (get state "chiptest_state" {})))

(defn transition-to-contact-probe-engaged [state]
  {"chiptest_state" (assoc (cs state) "phase" "contact_probe_engaged" "completionPct" 20
                           "probeData" {"contact_resistance_ohm" 0.8 "probe_temperature_c" 28
                                        "contact_force_grams" 450 "probe_count_active" 486
                                        "probe_card_calibration" "pass"})
   "next_node" "parametric_test"})

(defn transition-to-parametric-test-complete [state]
  {"chiptest_state" (assoc (cs state) "phase" "parametric_test_complete" "completionPct" 50
                           "parametricResults" {"vdd_nominal_v" 0.85 "leakage_current_ua" 45
                                                "ring_oscillator_freq_ghz" 2.8 "threshold_voltage_v" 0.42
                                                "gain_v_v" 85 "parameters_pass_rate_pct" 99.2})
   "next_node" "functional_test"})

(defn transition-to-functional-test-complete [state]
  {"chiptest_state" (assoc (cs state) "phase" "functional_test_complete" "completionPct" 75
                           "functionalResults" {"test_pattern" "LFSR_3500_vectors" "test_duration_minutes" 8.5
                                                "failure_count" 0 "functional_pass_rate_pct" 100.0
                                                "speed_grade" "A" "power_dissipation_mw" 125})
   "next_node" "grade_chip"})

(def ^:private mock-sigs
  [{"robotDid" "did:web:etzhayyim.com:mimi-unit-3" "role" "test_equipment_controller"
    "timestamp" "2026-05-26T16:20:15Z" "signature" "jJ1kK2lL3mM4nN5o..."}
   {"robotDid" "did:web:etzhayyim.com:otete-unit-4" "role" "test_handler"
    "timestamp" "2026-05-26T16:20:20Z" "signature" "pP6qQ7rR8sS9tT0u..."}])

(defn transition-to-chip-graded [state]
  (let [c (assoc (cs state) "phase" "chip_graded" "yieldGrade" "A" "robotSignatures" mock-sigs "completionPct" 100)]
    {"chiptest_state" c
     "chiptest_record" {"dieId" (get c "dieId") "parametricResults" (get c "parametricResults")
                        "functionalResults" (get c "functionalResults") "yieldGrade" (get c "yieldGrade")
                        "attestingRobots" mock-sigs}
     "next_node" "end"}))

(defn solve [_input-state]
  (throw (ex-info "silicon R0 scaffold: activate chiptest via Council ADR (post-2605242500 ratification)" {:scaffold true})))
