(ns sarutahiko.cells.quality-road-test.state-machine
  "Quality-road-test state machine — ADR-2605252500 L5c. 1:1 cljc port of
  `cells/quality_road_test/state_machine.py`. Roller dyno + 50 km public-road test;
  Norimichi SAE-L3 driver-in-seat; G12 KPI (≤90 km/h civilian / autonomous ≤L4).
  String keys mirror the Python __dict__.")

(def phases
  {:init "init" :dyno-run-complete "dyno_run_complete" :g12-kpi-verified "g12_kpi_verified"
   :public-road-test-complete "public_road_test_complete"
   :norimichi-attestation "norimichi_attestation" :record-emitted "record_emitted"})

(defn init [state]
  {"road_test_state" {"phase" (:init phases)
                      "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                      "completionPct" 0}})

(defn transition-to-dyno-run-complete [state]
  (let [s (-> (get state "road_test_state" {})
              (assoc "dynoResult" {"maxWheelPowerKw" 320 "maxWheelTorqueNm" 2100
                                   "fuelConsumption_l_per_100km" 22.5 "brakeStoppingDistanceM" 38}
                     "phase" (:dyno-run-complete phases) "completionPct" 35))]
    {"road_test_state" s "next_node" "g12"}))

(defn transition-to-g12-kpi-verified [state]
  (let [s (-> (get state "road_test_state" {})
              (assoc "g12KpiCheck" {"maxSpeedKmh" 85 "maxSpeedLimitKmh" 90 "autonomyLevel" "L0-manual-R1"
                                    "autonomyMaxLevel" 4 "rangeKm" 850 "rangeMinKm" 800
                                    "gvwrT" 36 "gvwrMaxT" 40 "accept" true}
                     "phase" (:g12-kpi-verified phases) "completionPct" 55))]
    {"road_test_state" s "next_node" "road"}))

(defn transition-to-public-road-test-complete [state]
  (let [s (-> (get state "road_test_state" {})
              (assoc "publicRoadResult" {"routeDistanceKm" 50 "averageSpeedKmh" 65
                                         "incidents" [] "videoCid" "bafkreiroadtest..."}
                     "phase" (:public-road-test-complete phases) "completionPct" 80))]
    {"road_test_state" s "next_node" "norimichi"}))

(defn transition-to-norimichi-attestation [state]
  (let [s (-> (get state "road_test_state" {})
              (assoc "norimichiAttestation" {"norimichiDid" "did:web:etzhayyim.com:norimichi-unit-1"
                                             "humanDriverSbtDid" "did:web:etzhayyim.com:adherent:test-driver-001#sbt"
                                             "saeLevel" 3 "saeMaxLevel" 4
                                             "timestamp" "2026-05-26T18:30:00Z" "signature" "..."}
                     "phase" (:norimichi-attestation phases) "completionPct" 92))]
    {"road_test_state" s "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [s (-> (get state "road_test_state" {})
              (assoc "phase" (:record-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.sarutahiko.roadTestRecord"
                "chassisId" (get s "chassisId")
                "dynoResult" (get s "dynoResult")
                "g12KpiCheck" (get s "g12KpiCheck")
                "publicRoadResult" (get s "publicRoadResult")
                "norimichiAttestation" (get s "norimichiAttestation")
                "overallAccept" true
                "recordedAt" "2026-05-26T18:35:00Z"}]
    {"road_test_state" s "road_test_record" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-dyno-run-complete transition-to-g12-kpi-verified
           transition-to-public-road-test-complete transition-to-norimichi-attestation
           transition-to-record-emitted]))
