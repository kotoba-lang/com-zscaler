(ns wadachi.methods.test-agent
  "wadachi 轍 — agent gate tests (offline, no kotoba host, no network, no LLM).

  ADR-2605242000. Faithful Clojure/bb port of py/test_agent.py.
  Exercises the autonomous mobility constitutional gates: KPI caps (G11),
  operator SBT eligibility (G10), safety monitoring, USDC + tithe settlement
  (G13/G14), and trajectory determinism (G6).

  Expected values copied VERBATIM from py/test_agent.py."
  (:require [clojure.test :refer [deftest is testing]]
            [wadachi.methods.agent :as agent]))

;; ── KPI cap tests (G11/G12) ──────────────────────────────────────────────────

(deftest test-kpi-speed-cap
  (testing "speed over 1.0 m/s rejected (G11)"
    (is (false? (:ok (agent/kpi-caps-ok 1.5 50.0 30.0 2.0))))))

(deftest test-kpi-payload-cap
  (testing "payload over 100 kg rejected (G11)"
    (is (false? (:ok (agent/kpi-caps-ok 0.5 150.0 30.0 2.0))))))

(deftest test-kpi-duration-cap
  (testing "mission > 60 min rejected (G11)"
    (is (false? (:ok (agent/kpi-caps-ok 0.5 50.0 90.0 2.0))))))

(deftest test-kpi-energy-cap
  (testing "energy > 5 kWh rejected (G12)"
    (is (false? (:ok (agent/kpi-caps-ok 0.5 50.0 30.0 6.0))))))

(deftest test-kpi-within
  (testing "within KPI caps accepted (G11/G12)"
    (is (true? (:ok (agent/kpi-caps-ok 0.5 50.0 30.0 2.0))))))

;; ── operator SBT tests (G10) ──────────────────────────────────────────────────

(deftest test-operator-lacks-sbt
  (testing "operator without active SBT rejected (G10)"
    (is (false? (:ok (agent/operator-ok "did:unknown" {}))))))

(deftest test-operator-has-sbt
  (testing "operator with active SBT accepted (G10)"
    (let [sbt {"did:web:op.example" {"active" true}}]
      (is (true? (:ok (agent/operator-ok "did:web:op.example" sbt)))))))

;; ── trajectory determinism tests (G6) ────────────────────────────────────────

(deftest test-trajectory-determinism-ok
  (testing "determinism commit verified (G6)"
    (is (true? (:ok (agent/trajectory-determinism-ok "abc123def456"))))))

(deftest test-trajectory-determinism-missing
  (testing "missing determinism commit rejected (G6)"
    (is (false? (:ok (agent/trajectory-determinism-ok ""))))))

;; ── route planning tests ──────────────────────────────────────────────────────

(deftest test-route-planning-missing-dest
  (testing "route planning rejects missing destination"
    (let [out (agent/handle-route-planning {"route" {}})]
      (is (some? (get out "error"))))))

(deftest test-route-planning-ok
  (testing "route planning succeeds with valid input"
    (let [out (agent/handle-route-planning
               {"route" {"id"          "dr.0001"
                         "origin"      "35.6789,139.7006"
                         "destination" "35.6795,139.7015"
                         "payloadKg"   45.0}})]
      (is (some? (get-in out ["trajectory" "plan-id"]))))))

;; ── motion control tests ──────────────────────────────────────────────────────

(deftest test-motion-control-no-trajectory
  (testing "motion control rejects missing trajectory"
    (let [out (agent/handle-motion-control {"trajectory" {}})]
      (is (some? (get out "error"))))))

;; ── obstacle avoidance tests ──────────────────────────────────────────────────

(deftest test-obstacle-avoidance-no-obstacle
  (testing "obstacle avoidance detects clear (no obstacle)"
    (let [out (agent/handle-obstacle-avoidance
               {"steering-commands" {"command-id" "sc.001"}
                "sensors"           {"lidar" "clear"}})]
      (is (false? (get-in out ["avoidance" "obstacle-detected"]))))))

(deftest test-obstacle-avoidance-detected
  (testing "obstacle avoidance detects obstacle"
    (let [out (agent/handle-obstacle-avoidance
               {"steering-commands" {"command-id" "sc.001"}
                "sensors"           {"lidar" "obstacle at 5m"}})]
      (is (true? (get-in out ["avoidance" "obstacle-detected"]))))))

;; ── safety monitoring tests (G11/G12) ────────────────────────────────────────

(deftest test-safety-monitoring-speed-violation
  (testing "safety monitoring catches speed violation (G11)"
    (let [out (agent/handle-safety-monitoring
               {"telemetry" {"speed_mps"  1.5
                             "accel_mps2" 0.5
                             "tilt_deg"   2.0
                             "wheel_slip" 0.1
                             "energy_kwh" 2.0}})]
      (is (= "speed_violation" (get-in out ["safety" "anomaly"]))))))

(deftest test-safety-monitoring-energy-violation
  (testing "safety monitoring catches energy budget violation (G12)"
    (let [out (agent/handle-safety-monitoring
               {"telemetry" {"speed_mps"  0.5
                             "accel_mps2" 0.5
                             "tilt_deg"   2.0
                             "wheel_slip" 0.1
                             "energy_kwh" 6.5}})]
      (is (= "energy_budget" (get-in out ["safety" "anomaly"]))))))

(deftest test-safety-monitoring-pass
  (testing "safety monitoring passes nominal telemetry"
    (let [out (agent/handle-safety-monitoring
               {"telemetry" {"speed_mps"  0.5
                             "accel_mps2" 0.5
                             "tilt_deg"   2.0
                             "wheel_slip" 0.1
                             "energy_kwh" 2.0}})]
      (is (true? (get-in out ["safety" "pass"]))))))

;; ── telemetry log tests ───────────────────────────────────────────────────────

(deftest test-telemetry-log-complete
  (testing "telemetry log creates mission complete record"
    (let [out (agent/handle-telemetry-log
               {"route"        {"id"          "dr.0001"
                                "origin"      "35.6789,139.7006"
                                "destination" "35.6795,139.7015"}
                "trajectory"   {"id" "tp.0001"}
                "safety"       {"pass" true}
                "telemetry"    {"energy_kwh" 1.5}
                "operator_did" "did:web:op.example"})]
      (is (some? (get-in out ["mission-complete" "record-id"]))))))

;; ── settlement tests (G13/G14) ────────────────────────────────────────────────

(deftest test-settlement-tithe-split
  (testing "10% tithe + stops at intent (G13/G14)"
    ;; verbatim from py/test_agent.py: titheMinor == 1_000_000, state == "intent",
    ;; rail == "usdc-base-l2"  (gross 10_000_000)
    (let [s (agent/build-settlement-intent 10000000)]
      (is (= 1000000 (:titheMinor s)))
      (is (= "intent" (:state s)))
      (is (= "usdc-base-l2" (:rail s))))))

(deftest test-settlement-executed-with-sig
  (testing "settlement executes only with operator signature (G14)"
    ;; verbatim from py/test_agent.py: state == "executed"  (sig "0xsig")
    (let [s (agent/build-settlement-intent 1000000 "0xsig")]
      (is (= "executed" (:state s))))))
