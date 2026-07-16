(ns watatsumi.methods.test-agent
  "watatsumi 綿津見 — agent cell tests (no kotoba host, no network, no LLM).

  ADR-2605252200 Phase 4. Exercises the 9 handlers + settlement + gates with injected
  functions so the suite runs offline (Murakumo-only invariant untouched; G15). Tests
  enforce civilian-only constraints (depth ≤6500m, no military, max 3 crew, max 72h submerged).

  1:1 faithful port of py/test_agent.py — expected values copied verbatim from the Python
  oracle. Deviations in expected values are forbidden; only the Clojure test form wrapping
  changes."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [watatsumi.methods.agent :as agent]))

;; ── L1: hull ring fabrication ──

(deftest test-hull-ring-fabrication-pass
  ;; test_hull_ring_fabrication_pass: "L1-pass-HSLA-80-LOT-001"
  (let [out (agent/handle-hull-ring-fabrication
              {"material_lot" "HSLA-80-LOT-001"
               "ndt_pass"     true
               "roundness_pct" 0.3})]
    (is (= "L1-pass-HSLA-80-LOT-001" (get out "attestation"))
        "L1: hull ring fabrication passes with NDT + roundness <0.5%")))

(deftest test-hull-ring-roundness-fail
  ;; test_hull_ring_roundness_fail: "FAIL_roundness_exceeded"
  (let [out (agent/handle-hull-ring-fabrication
              {"material_lot" "HSLA-80-LOT-001"
               "ndt_pass"     true
               "roundness_pct" 0.6})]
    (is (= "FAIL_roundness_exceeded" (get out "attestation"))
        "L1: hull ring rejects roundness >0.5%")))

(deftest test-hull-ring-ndt-fail
  ;; test_hull_ring_ndt_fail: "FAIL_ndt_not_passed"
  (let [out (agent/handle-hull-ring-fabrication
              {"material_lot" "HSLA-80-LOT-001"
               "ndt_pass"     false
               "roundness_pct" 0.3})]
    (is (= "FAIL_ndt_not_passed" (get out "attestation"))
        "L1: hull ring rejects failed NDT")))

;; ── L2: section assembly ──

(deftest test-section-assembly-pass
  ;; test_section_assembly_pass: "L2-pass-2-rings"
  (let [out (agent/handle-section-assembly
              {"ring_ids"     ["ring.001" "ring.002"]
               "penetrators"  ["power-conduit" "data-fiber"]})]
    (is (= "L2-pass-2-rings" (get out "attestation"))
        "L2: section assembly passes with power/data/fluid penetrators")))

(deftest test-section-assembly-no-weapon-mounts
  ;; test_section_assembly_no_weapon_mounts: "FAIL_weapon_mount_detected"
  (let [out (agent/handle-section-assembly
              {"ring_ids"    ["ring.001"]
               "penetrators" ["weapon-mount" "torpedo-tube"]})]
    (is (= "FAIL_weapon_mount_detected" (get out "attestation"))
        "L2: section assembly rejects weapon mounts (civilian-only G12)")))

;; ── L3: weld inspection ──

(deftest test-weld-inspection-pass
  ;; test_weld_inspection_pass: "L3-pass-100pct-ndt"
  (let [out (agent/handle-weld-inspection
              {"ndt_results" [true true true]})]
    (is (= "L3-pass-100pct-ndt" (get out "result"))
        "L3: weld inspection passes 100% NDT")))

;; ── L4: system integration ──

(deftest test-system-integration-clean-fuel
  ;; test_system_integration_clean_fuel: "L4-pass-lfp-propulsion"
  (let [out (agent/handle-system-integration
              {"propulsion_type" "LFP"
               "sonar_db"        170})]
    (is (= "L4-pass-lfp-propulsion" (get out "result"))
        "L4: system integration passes LFP fuel-cell (G13)")))

(deftest test-system-integration-no-nuclear
  ;; test_system_integration_no_nuclear: "FAIL_forbidden_propulsion"
  (let [out (agent/handle-system-integration
              {"propulsion_type" "nuclear"
               "sonar_db"        170})]
    (is (= "FAIL_forbidden_propulsion" (get out "result"))
        "L4: system integration rejects nuclear propulsion (N2/Charter §2(g))")))

(deftest test-system-integration-sonar-cetacean-gate
  ;; test_system_integration_sonar_cetacean_gate: "FAIL_sonar_exceeds_180db"
  (let [out (agent/handle-system-integration
              {"propulsion_type" "H2"
               "sonar_db"        190})]
    (is (= "FAIL_sonar_exceeds_180db" (get out "result"))
        "L4: system integration rejects sonar >180dB (G8 cetacean protection)")))

;; ── L5a: section joining ──

(deftest test-section-joining-pass
  ;; test_section_joining_pass: "L5a-pass-6passes-pwht600c"
  (let [out (agent/handle-section-joining
              {"weld_passes"       6
               "rt_100pct"         true
               "pwht_temperature_c" 600})]
    (is (= "L5a-pass-6passes-pwht600c" (get out "result"))
        "L5a: section joining passes multi-pass TIG + 100% RT + PWHT")))

;; ── L5b: pressure test ──

(deftest test-pressure-test-depth-cap-6500m
  ;; test_pressure_test_depth_cap_6500m:
  ;;   out["test_depth_m"] == 8125.0
  ;;   out["result"] == "L5b-pass-8125.0m-6h"
  (let [out (agent/handle-pressure-test
              {"design_depth_m" 6500
               "duration_hours"  6})]
    (is (= 8125.0 (get out "test_depth_m"))
        "L5b: pressure test depth is 1.25 × 6500 = 8125.0 m")
    (is (= "L5b-pass-8125.0m-6h" (get out "result"))
        "L5b: pressure test passes at 6500m civilian depth cap")))

(deftest test-pressure-test-exceeds-depth-cap
  ;; test_pressure_test_exceeds_depth_cap: "FAIL_depth_exceeds" in out["result"]
  (let [out (agent/handle-pressure-test
              {"design_depth_m" 7000
               "duration_hours"  6})]
    (is (str/includes? (get out "result") "FAIL_depth_exceeds")
        "L5b: pressure test rejects depth >6500m (civilian-only G12)")))

;; ── L5c: sea trial ──

(deftest test-sea-trial-all-stages-pass
  ;; test_sea_trial_all_stages_pass: "L5c-pass-all-stages"
  (let [out (agent/handle-sea-trial
              {"dock_pass"          true
               "harbor_pass"        true
               "deep_water_pass"    true
               "crew_count"         3
               "submerged_duration_h" 72})]
    (is (= "L5c-pass-all-stages" (get out "result"))
        "L5c: sea trial passes all stages + crew ≤3 + submerged ≤72h")))

(deftest test-sea-trial-crew-cap
  ;; test_sea_trial_crew_cap: "FAIL_crew" in out["result"]
  (let [out (agent/handle-sea-trial
              {"dock_pass"          true
               "harbor_pass"        true
               "deep_water_pass"    true
               "crew_count"         5
               "submerged_duration_h" 72})]
    (is (str/includes? (get out "result") "FAIL_crew")
        "L5c: sea trial rejects crew >3 (civilian-only G12)")))

(deftest test-sea-trial-submerged-duration-cap
  ;; test_sea_trial_submerged_duration_cap: "FAIL_submerged" in out["result"]
  (let [out (agent/handle-sea-trial
              {"dock_pass"          true
               "harbor_pass"        true
               "deep_water_pass"    true
               "crew_count"         3
               "submerged_duration_h" 100})]
    (is (str/includes? (get out "result") "FAIL_submerged")
        "L5c: sea trial rejects submerged >72h (civilian-only G12)")))

;; ── cross-cutting: marine emissions audit ──

(deftest test-marine-emissions-audit-pass
  ;; test_marine_emissions_audit_pass: "audit-pass-all-annexes"
  (let [out (agent/handle-marine-emissions-audit
              {"marpol_compliant" true
               "bwmc_compliant"   true
               "biofouling_clean" true})]
    (is (= "audit-pass-all-annexes" (get out "result"))
        "Cross: marine emissions audit passes MARPOL + BWMC + biofouling (G14)")))

;; ── settlement ──

(deftest test-settlement-tithe-split
  ;; test_settlement_tithe_split:
  ;;   s["titheMinor"] == 1_000_000_000
  ;;   s["payoutMinor"] == 9_000_000_000
  ;;   s["state"] == "intent"
  ;;   s["rail"] == "usdc-base-l2"
  (let [s (agent/build-settlement-intent 10000000000)]
    (is (= 1000000000  (get s "titheMinor"))
        "Settlement: 10% tithe = 1,000,000,000")
    (is (= 9000000000  (get s "payoutMinor"))
        "Settlement: payout = 9,000,000,000")
    (is (= "intent"    (get s "state"))
        "Settlement: stops at intent without buyer sig (G19)")
    (is (= "usdc-base-l2" (get s "rail"))
        "Settlement: rail is USDC Base L2 (G17)")))

(deftest test-settlement-executed-only-with-member-sig
  ;; test_settlement_executed_only_with_member_sig: s["state"] == "executed"
  (let [s (agent/build-settlement-intent 10000000000 "0xmembersig")]
    (is (= "executed" (get s "state"))
        "Settlement: executes only with member signature (G18)")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'watatsumi.methods.test-agent)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
