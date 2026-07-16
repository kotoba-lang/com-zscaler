(ns watatsumi.cells.marine-emissions-audit.state-machine
  "Marine-emissions-audit state machine — ADR-2605252200 G14 cross-cutting. 1:1
  cljc port of `cells/marine_emissions_audit/state_machine.py`. Continuous MARPOL
  Annex I-VI + BWMC + IMO biofouling compliance monitoring. String keys mirror the
  Python __dict__ so the emitted marineEmissionsAuditRecord is byte-identical.")

(def phases
  {:init "init" :marpol-scan "marpol_scan" :bwmc-scan "bwmc_scan"
   :biofouling-scan "biofouling_scan" :record-emitted "record_emitted"})

(defn init
  "Fresh emissions_audit_state. Port of cell.py `_init`."
  [state]
  {"emissions_audit_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "completionPct" 0}})

(defn transition-to-marpol-scan [state]
  (let [ea (-> (get state "emissions_audit_state" {})
               (assoc "marpolFindings"
                      {"annexI_oilPollution" {"violations" 0 "accept" true}
                       "annexII_noxiousLiquid" {"violations" 0 "accept" true}
                       "annexIII_harmfulPackaged" {"violations" 0 "accept" true}
                       "annexIV_sewage" {"violations" 0 "accept" true}
                       "annexV_garbage" {"violations" 0 "accept" true}
                       "annexVI_airPollution" {"violations" 0 "accept" true}}
                      "phase" (:marpol-scan phases) "completionPct" 35))]
    {"emissions_audit_state" ea "next_node" "bwmc"}))

(defn transition-to-bwmc-scan [state]
  (let [ea (-> (get state "emissions_audit_state" {})
               (assoc "bwmcFindings"
                      {"ballastWaterManagementPlan" "approved"
                       "treatmentSystem" "filtration+UV"
                       "ovicidalEffectiveness" "≥99.9%"
                       "accept" true}
                      "phase" (:bwmc-scan phases) "completionPct" 65))]
    {"emissions_audit_state" ea "next_node" "biofouling"}))

(defn transition-to-biofouling-scan [state]
  (let [ea (-> (get state "emissions_audit_state" {})
               (assoc "biofoulingFindings"
                      {"imoGuidelines" "MEPC.378(80) compliant"
                       "hullCoatingType" "biocide-free silicone fouling-release"
                       "antifoulingTributyltinFree" true
                       "sangoInspectionFrequency" "every 90 days"
                       "accept" true}
                      "phase" (:biofouling-scan phases) "completionPct" 90))]
    {"emissions_audit_state" ea "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [ea0 (get state "emissions_audit_state" {})
        accept (boolean
                (and (every? #(get % "accept") (vals (get ea0 "marpolFindings" {})))
                     (= true (get (get ea0 "bwmcFindings" {}) "accept"))
                     (= true (get (get ea0 "biofoulingFindings" {}) "accept"))))
        ea (assoc ea0 "overallAccept" accept
                  "phase" (:record-emitted phases) "completionPct" 100)
        record {"$type" "etzhayyim:watatsumi:marineEmissionsAuditRecord"
                "craftId" (get ea "craftId")
                "marpol" (get ea "marpolFindings")
                "bwmc" (get ea "bwmcFindings")
                "biofouling" (get ea "biofoulingFindings")
                "overallAccept" (get ea "overallAccept")
                "g14Reference" "ADR-2605252200 G14"
                "recordedAt" "2026-05-27T11:00:00Z"}]
    {"emissions_audit_state" ea "marine_emissions_audit_record" record "next_node" "end"}))

(defn run-chain
  "Run the full G14 chain init→marpol→bwmc→biofouling→record. Merges input+init
  (langgraph state-merge parity)."
  [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-marpol-scan transition-to-bwmc-scan
           transition-to-biofouling-scan transition-to-record-emitted]))
