(ns sarutahiko.cells.cab-body-forming.state-machine
  "Cab-body-forming state machine — ADR-2605252500 L3. 1:1 cljc port of
  `cells/cab_body_forming/state_machine.py`. Steel sheet hot-stamping → robotic
  spot welding → leak test. String keys mirror the Python __dict__.")

(def phases
  {:init "init" :sheet-lot-verified "sheet_lot_verified" :hot-stamping-complete "hot_stamping_complete"
   :spot-welding-complete "spot_welding_complete" :leak-test-passed "leak_test_passed"
   :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"cab_state" {"phase" (:init phases)
                "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                "completionPct" 0}})

(defn transition-to-sheet-lot-verified [state]
  (let [s (-> (get state "cab_state" {})
              (assoc "sheetLot" {"source" "external-commodity-R1"
                                 "note" "R2+ source from kanayama Wave 2 steel coil"
                                 "lotId" "STEEL-SHEET-2026-05-0021" "thicknessMm" 0.8}
                     "phase" (:sheet-lot-verified phases) "completionPct" 15))]
    {"cab_state" s "next_node" "stamp"}))

(defn transition-to-hot-stamping-complete [state]
  (let [s (-> (get state "cab_state" {})
              (assoc "stampedPanels" [{"panel" "roof" "stampingTempC" 900 "ipfsCid" "bafkreiroof..."}
                                      {"panel" "left_side" "stampingTempC" 900 "ipfsCid" "bafkreilside..."}
                                      {"panel" "right_side" "stampingTempC" 900 "ipfsCid" "bafkreirside..."}
                                      {"panel" "rear" "stampingTempC" 900 "ipfsCid" "bafkreirear..."}
                                      {"panel" "floor" "stampingTempC" 900 "ipfsCid" "bafkreifloor..."}]
                     "phase" (:hot-stamping-complete phases) "completionPct" 45))]
    {"cab_state" s "next_node" "weld"}))

(defn transition-to-spot-welding-complete [state]
  (let [s (-> (get state "cab_state" {})
              (assoc "spotWelds" {"totalSpots" 2400 "robotPasses" 4 "videoCid" "bafkreispotweld..."}
                     "phase" (:spot-welding-complete phases) "completionPct" 75))]
    {"cab_state" s "next_node" "leak"}))

(defn transition-to-leak-test-passed [state]
  (let [s (-> (get state "cab_state" {})
              (assoc "leakTestResult" {"method" "pressure-decay" "leakRatePaPerS" 1.2
                                       "limitPaPerS" 5.0 "accept" true}
                     "phase" (:leak-test-passed phases) "completionPct" 92))]
    {"cab_state" s "next_node" "attestation"}))

(defn transition-to-attestation-emitted [state]
  (let [s (-> (get state "cab_state" {})
              (assoc "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.sarutahiko.cabBodyAttestation"
                "chassisId" (get s "chassisId")
                "sheetLot" (get s "sheetLot")
                "stampedPanels" (get s "stampedPanels")
                "spotWelds" (get s "spotWelds")
                "leakTestResult" (get s "leakTestResult")
                "recordedAt" "2026-05-26T11:00:00Z"}]
    {"cab_state" s "cab_body_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-sheet-lot-verified transition-to-hot-stamping-complete
           transition-to-spot-welding-complete transition-to-leak-test-passed
           transition-to-attestation-emitted]))
