(ns sarutahiko.cells.emissions-audit.state-machine
  "Emissions-audit state machine — ADR-2605252500 G8 cross-cutting. 1:1 cljc port
  of `cells/emissions_audit/state_machine.py`. Euro 7 + 日本ポスト新長期 + Bharat
  Stage VI continuous compliance; overallAccept = all three scans accept. String
  keys mirror the Python __dict__.")

(def phases
  {:init "init" :euro7-scanned "euro7_scanned" :japan-pnlt-scanned "japan_pnlt_scanned"
   :bharat-vi-scanned "bharat_vi_scanned" :record-emitted "record_emitted"})

(defn init [state]
  {"emissions_state" {"phase" (:init phases)
                      "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                      "completionPct" 0}})

(defn transition-to-euro7-scanned [state]
  (let [s (-> (get state "emissions_state" {})
              (assoc "euro7Findings" {"nox_mg_per_km" 90 "nox_limit_mg_per_km" 200
                                      "particulate_mg_per_km" 4.5 "particulate_limit_mg_per_km" 10
                                      "co_mg_per_km" 750 "co_limit_mg_per_km" 1500 "accept" true}
                     "phase" (:euro7-scanned phases) "completionPct" 30))]
    {"emissions_state" s "next_node" "japan"}))

(defn transition-to-japan-pnlt-scanned [state]
  (let [s (-> (get state "emissions_state" {})
              (assoc "japanPostNLTFindings" {"nox_g_per_kWh" 0.30 "nox_limit_g_per_kWh" 0.40
                                             "particulate_g_per_kWh" 0.008 "particulate_limit_g_per_kWh" 0.010
                                             "accept" true}
                     "phase" (:japan-pnlt-scanned phases) "completionPct" 60))]
    {"emissions_state" s "next_node" "bharat"}))

(defn transition-to-bharat-vi-scanned [state]
  (let [s (-> (get state "emissions_state" {})
              (assoc "bharatViFindings" {"nox_g_per_kWh" 0.42 "nox_limit_g_per_kWh" 0.46
                                         "particulate_g_per_kWh" 0.009 "particulate_limit_g_per_kWh" 0.010
                                         "accept" true}
                     "phase" (:bharat-vi-scanned phases) "completionPct" 90))]
    {"emissions_state" s "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [s0 (get state "emissions_state" {})
        accept (boolean (and (= true (get (get s0 "euro7Findings" {}) "accept"))
                             (= true (get (get s0 "japanPostNLTFindings" {}) "accept"))
                             (= true (get (get s0 "bharatViFindings" {}) "accept"))))
        s (assoc s0 "overallAccept" accept "phase" (:record-emitted phases) "completionPct" 100)
        record {"$type" "com.etzhayyim.sarutahiko.emissionsAuditRecord"
                "chassisId" (get s "chassisId")
                "euro7Findings" (get s "euro7Findings")
                "japanPostNLTFindings" (get s "japanPostNLTFindings")
                "bharatViFindings" (get s "bharatViFindings")
                "overallAccept" (get s "overallAccept")
                "regulatoryBasis" ["EU Regulation (EU) 2024/1257 — Euro 7"
                                   "日本 ポスト新長期排出ガス規制"
                                   "Bharat Stage VI"]
                "phaseGate" "R0-R1 tailpipe permitted under G7 transition; R2+ requires zero tailpipe"
                "recordedAt" "2026-05-26T19:00:00Z"}]
    {"emissions_state" s "emissions_audit_record" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-euro7-scanned transition-to-japan-pnlt-scanned
           transition-to-bharat-vi-scanned transition-to-record-emitted]))
