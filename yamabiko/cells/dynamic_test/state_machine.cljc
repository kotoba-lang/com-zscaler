(ns yamabiko.cells.dynamic-test.state-machine
  "1:1 port of cells/dynamic_test/state_machine.py — ADR-2605252600 L5b.

  ≥100 km test track. G12 KPI enforcement: max speed ≤320 km/h Wave 1.")

(defn- dynamic-state
  "DynamicState defaults merged with any existing \"dynamic_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "dynamic_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "completionPct" 0
            "staticTestResult" nil
            "g12KpiCheck" nil
            "dynamicRunResult" nil}
           existing)))

(defn transition-to-static-test-passed
  "Run static tests (weight distribution, pneumatics, doors, brakes, HVAC)."
  [state]
  (let [s (dynamic-state state)]
    {"dynamic_state" (assoc s
                            "staticTestResult" {"weightDistribution" "PASS"
                                                "pneumaticPressure" "PASS"
                                                "doorOperation" "PASS"
                                                "emergencyBrake" "PASS"
                                                "hvacCalibration" "PASS"}
                            "phase" "static_test_passed"
                            "completionPct" 25)
     "next_node" "g12"}))

(defn transition-to-g12-kpi-verified
  "Verify G12 KPIs (max speed ≤320 km/h, trainset ≤450 m, GoA ≤3)."
  [state]
  (let [s (dynamic-state state)]
    {"dynamic_state" (assoc s
                            "g12KpiCheck" {"designSpeedKmh" 320
                                           "maxSpeedLimitKmh" 320
                                           "trainsetLengthM" 100
                                           "maxTrainsetLengthM" 450
                                           "atoLevel" "GoA-3"
                                           "atoMaxLevel" 3
                                           "accept" true}
                            "phase" "g12_kpi_verified"
                            "completionPct" 50)
     "next_node" "run"}))

(defn transition-to-dynamic-run-complete
  "Complete dynamic test run (high-speed, acceleration, deceleration, ride quality)."
  [state]
  (let [s (dynamic-state state)]
    {"dynamic_state" (assoc s
                            "dynamicRunResult" {"testTrackLengthKm" 105
                                                "totalDistanceKm" 1240
                                                "maxAchievedSpeedKmh" 318
                                                "averageSpeedKmh" 220
                                                "accelerationMsps" 0.72
                                                "decelerationMsps" 1.10
                                                "rideQualityRMSM" 0.18
                                                "rideQualitySpecMaxRMSM" 0.25
                                                "videoCid" "bafkreidyntest..."}
                            "phase" "dynamic_run_complete"
                            "completionPct" 92)
     "next_node" "record"}))

(defn transition-to-record-emitted
  "Emit final dynamic test record."
  [state]
  (let [s (dynamic-state state)]
    {"dynamic_state" (assoc s
                            "phase" "record_emitted"
                            "completionPct" 100)
     "dynamic_test_record" {"$type" "com.etzhayyim.yamabiko.dynamicTestRecord"
                            "trainsetId" (get s "trainsetId")
                            "staticTestResult" (get s "staticTestResult")
                            "g12KpiCheck" (get s "g12KpiCheck")
                            "dynamicRunResult" (get s "dynamicRunResult")
                            "overallAccept" true
                            "recordedAt" "2026-05-27T10:00:00Z"}
     "next_node" "end"}))
