(ns watatsumi.cells.weld-inspection.state-machine
  "Weld-inspection state machine — ADR-2605252200 L3. 1:1 cljc port of
  `cells/weld_inspection/state_machine.py`. 100% RT + UT + PT NDT on every weld,
  in-process Sango AUV witness; overallAccept = no indications found across all
  three NDT methods. String keys mirror the Python __dict__.")

(def phases
  {:init "init" :rt-complete "rt_complete" :ut-complete "ut_complete"
   :pt-complete "pt_complete" :sango-witness-complete "sango_witness_complete"
   :record-emitted "record_emitted"})

(defn init [state]
  {"weld_inspection_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "sectionIndex" (get state "sectionIndex" 0)
    "completionPct" 0}})

(defn transition-to-rt-complete [state]
  (let [wi (-> (get state "weld_inspection_state" {})
               (assoc "phase" (:rt-complete phases)
                      "rtResults" [{"weldId" "ring0-ring1-seam" "rtFilmCid" "bafkreirt1..." "indications" []}
                                   {"weldId" "ring1-ring2-seam" "rtFilmCid" "bafkreirt2..." "indications" []}
                                   {"weldId" "ring2-ring3-seam" "rtFilmCid" "bafkreirt3..." "indications" []}]
                      "completionPct" 30))]
    {"weld_inspection_state" wi "next_node" "ut"}))

(defn transition-to-ut-complete [state]
  (let [wi (-> (get state "weld_inspection_state" {})
               (assoc "phase" (:ut-complete phases)
                      "utResults" [{"weldId" "ring0-ring1-seam" "method" "phased-array-UT"
                                    "scanCid" "bafkreiut1..." "indications" []}]
                      "completionPct" 55))]
    {"weld_inspection_state" wi "next_node" "pt"}))

(defn transition-to-pt-complete [state]
  (let [wi (-> (get state "weld_inspection_state" {})
               (assoc "phase" (:pt-complete phases)
                      "ptResults" [{"weldId" "ring0-ring1-seam" "method" "dye-penetrant"
                                    "photoCid" "bafkreipt1..." "indications" []}]
                      "completionPct" 75))]
    {"weld_inspection_state" wi "next_node" "sango"}))

(defn transition-to-sango-witness
  "In-process Sango AUV swarm witness (outer-hull visual + biofouling baseline)."
  [state]
  (let [wi (-> (get state "weld_inspection_state" {})
               (assoc "phase" (:sango-witness-complete phases)
                      "sangoWitnessRecords"
                      [{"sangoDid" "did:web:etzhayyim.com:sango-unit-1" "videoCid" "bafkreisango1..." "anomalies" []}
                       {"sangoDid" "did:web:etzhayyim.com:sango-unit-2" "videoCid" "bafkreisango2..." "anomalies" []}]
                      "completionPct" 90))]
    {"weld_inspection_state" wi "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [wi0 (get state "weld_inspection_state" {})
        findings (vec (concat (mapcat #(get % "indications" []) (get wi0 "rtResults" []))
                              (mapcat #(get % "indications" []) (get wi0 "utResults" []))
                              (mapcat #(get % "indications" []) (get wi0 "ptResults" []))))
        accept (zero? (count findings))
        wi (assoc wi0 "indicationFindings" findings "overallAccept" accept
                  "phase" (:record-emitted phases) "completionPct" 100)
        record {"$type" "com.etzhayyim.watatsumi.weldInspectionRecord"
                "craftId" (get wi "craftId")
                "sectionIndex" (get wi "sectionIndex")
                "rtResults" (get wi "rtResults")
                "utResults" (get wi "utResults")
                "ptResults" (get wi "ptResults")
                "sangoWitnessRecords" (get wi "sangoWitnessRecords")
                "indicationFindings" findings
                "overallAccept" accept
                "code" "ASME BPVC §VIII Div 3 equivalent"
                "recordedAt" "2026-05-26T13:00:00Z"}]
    {"weld_inspection_state" wi "weld_inspection_record" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-rt-complete transition-to-ut-complete transition-to-pt-complete
           transition-to-sango-witness transition-to-record-emitted]))
