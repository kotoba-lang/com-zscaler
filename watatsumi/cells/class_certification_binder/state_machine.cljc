(ns watatsumi.cells.class-certification-binder.state-machine
  "Class-certification-binder state machine — ADR-2605252200 terminal cell. 1:1
  cljc port of `cells/class_certification_binder/state_machine.py`. Aggregates
  L1–L5c + marine_emissions_audit into a kotoba-datomic-anchored
  classCertificationRecord (DNV-RU-UWT / ABS Underwater Vehicles / NK 同等; G2
  audit-log enforcement). String keys mirror the Python __dict__.")

(def phases
  {:init "init" :records-collected "records_collected" :surveyor-review "surveyor_review"
   :kotoba-datomic-anchored "kotoba-datomic_anchored" :record-emitted "record_emitted"})

(defn init
  "Fresh certification_state. Port of cell.py `_init`."
  [state]
  {"certification_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "completionPct" 0}})

(defn transition-to-records-collected
  "Collect upstream record CIDs + classRegime (read from the top-level state)."
  [state]
  (let [cs (-> (get state "certification_state" {})
               (assoc "classRegime" (get state "classRegime" "DNV-RU-UWT")
                      "upstreamRecords"
                      {"pressureHullAttestation" "bafkreihullatt..."
                       "sectionAssemblyAttestation" "bafkreisectatt..."
                       "weldInspectionRecord" "bafkreiweld..."
                       "systemIntegrationAttestation" "bafkreisysint..."
                       "sectionJoiningAttestation" "bafkreisectjoin..."
                       "pressureTestRecord" "bafkreipress..."
                       "seaTrialRecord" "bafkreitrial..."
                       "marineEmissionsAuditRecord" "bafkreiemis..."}
                      "phase" (:records-collected phases) "completionPct" 30))]
    {"certification_state" cs "next_node" "surveyor"}))

(defn transition-to-surveyor-review [state]
  (let [cs0 (get state "certification_state" {})
        cs (assoc cs0
                  "surveyorReview"
                  {"surveyorDid" "did:web:etzhayyim.com:surveyor:dnv-uwt-007"
                   "surveyorSbtId" "did:web:etzhayyim.com:adherent:surveyor-007#sbt"
                   "regimeReference" (get cs0 "classRegime")
                   "findings" []
                   "recommend" "ISSUE_CLASS_CERTIFICATE"
                   "timestamp" "2026-05-27T13:00:00Z"}
                  "phase" (:surveyor-review phases) "completionPct" 65)]
    {"certification_state" cs "next_node" "anchor"}))

(defn transition-to-kotoba-datomic-anchored [state]
  (let [cs (-> (get state "certification_state" {})
               (assoc "kotoba_datomicAnchor"
                      {"membraneNamespace" "com.etzhayyim.watatsumi"
                       "anchorTxHash" "0xWATATSUMICERT..."
                       "l2Chain" "Base Sepolia (R0 dry-run)"
                       "anchorBlockNumber" 0
                       "g2Compliant" true}
                      "phase" (:kotoba-datomic-anchored phases) "completionPct" 90))]
    {"certification_state" cs "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [cs (-> (get state "certification_state" {})
               (assoc "phase" (:record-emitted phases) "completionPct" 100))
        record {"$type" "etzhayyim:watatsumi:classCertificationRecord"
                "craftId" (get cs "craftId")
                "classRegime" (get cs "classRegime")
                "upstreamRecords" (get cs "upstreamRecords")
                "surveyorReview" (get cs "surveyorReview")
                "kotoba-datomicAnchor" (get cs "kotoba_datomicAnchor")
                "g2Compliant" true
                "recordedAt" "2026-05-27T13:30:00Z"}]
    {"certification_state" cs "class_certification_record" record "next_node" "end"}))

(defn run-chain
  "Run the full terminal chain init→collect→surveyor→anchor→record. Merges
  input+init so classRegime/craftId thread through (langgraph state-merge parity)."
  [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-records-collected transition-to-surveyor-review
           transition-to-kotoba-datomic-anchored transition-to-record-emitted]))
