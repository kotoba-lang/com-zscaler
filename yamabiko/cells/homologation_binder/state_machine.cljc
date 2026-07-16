(ns yamabiko.cells.homologation-binder.state-machine
  "1:1 port of cells/homologation_binder/state_machine.py — ADR-2605252600 L5c terminal.

  EN 50126/50128/50129 (RAMS) / 日本鉄道事業法 / FRA Tier I-III. Aggregates all
  upstream attestations + issues per-trainset DID. G2 + G13 enforcement.")

(defn- homologation-state
  "HomologationState defaults merged with any existing \"homologation_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "homologation_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "completionPct" 0
            "upstreamRecords" nil
            "serial" nil
            "trainsetDid" nil
            "authorityReview" nil
            "kotoba_datomicAnchor" nil}
           existing)))

(defn transition-to-records-collected
  "Collect all upstream attestations (carbody, bogie, interior, traction, final, dynamic, acoustic)."
  [state]
  (let [s (homologation-state state)]
    {"homologation_state" (assoc s
                                  "upstreamRecords" {"carbodyAttestations" "bafkreicarbodybundle..."
                                                     "bogieAttestations" "bafkreibogibundle..."
                                                     "interiorAttestations" "bafkreiintbundle..."
                                                     "tractionElectricalAttestation" "bafkreitr..."
                                                     "finalAssemblyAttestation" "bafkreifinal..."
                                                     "dynamicTestRecord" "bafkreidyn..."
                                                     "acousticEmissionsAuditRecord" "bafkreiac..."}
                                  "phase" "records_collected"
                                  "completionPct" 20)
     "next_node" "serial"}))

(defn transition-to-serial-assigned
  "Assign serial number to trainset."
  [state]
  (let [s (homologation-state state)]
    {"homologation_state" (assoc s
                                  "serial" "ETZYAMABIKO-2026-05-0001"
                                  "phase" "serial_assigned"
                                  "completionPct" 40)
     "next_node" "did"}))

(defn transition-to-trainset-did-issued
  "Issue per-trainset DID (G13: did:web:etzhayyim.com:yamabiko:trainset:<serial>)."
  [state]
  (let [s (homologation-state state)
        serial (get s "serial" "")]
    {"homologation_state" (assoc s
                                  "trainsetDid" (str "did:web:etzhayyim.com:yamabiko:trainset:" serial)
                                  "phase" "trainset_did_issued"
                                  "completionPct" 55)
     "next_node" "authority"}))

(defn transition-to-homologation-authority-review
  "Review by homologation authority (RAMS standards, jurisdiction, approval)."
  [state]
  (let [s (homologation-state state)]
    {"homologation_state" (assoc s
                                  "authorityReview" {"ramsStandards" ["EN 50126" "EN 50128" "EN 50129"]
                                                     "jurisdiction" "JP"
                                                     "homologationRegime" "日本 鉄道事業法"
                                                     "authorityDid" "did:web:etzhayyim.com:authority:mlit-jp"
                                                     "decision" "ISSUE_TYPE_APPROVAL"
                                                     "timestamp" "2026-05-27T13:00:00Z"}
                                  "phase" "homologation_authority_review"
                                  "completionPct" 75)
     "next_node" "anchor"}))

(defn transition-to-kotoba-datomic-anchored
  "Anchor trainset record on kotoba-datomic (G2: open registry)."
  [state]
  (let [s (homologation-state state)]
    {"homologation_state" (assoc s
                                  "kotoba_datomicAnchor" {"membraneNamespace" "com.etzhayyim.yamabiko"
                                                          "anchorTxHash" "0xYAMABIKOHOMOLOGATION..."
                                                          "l2Chain" "Base Sepolia (R0 dry-run)"
                                                          "anchorBlockNumber" 0
                                                          "g2Compliant" true
                                                          "openTrainsetRegistry" true}
                                  "phase" "kotoba_datomic_anchored"
                                  "completionPct" 90)
     "next_node" "record"}))

(defn transition-to-record-emitted
  "Emit final homologation record (terminal layer L5c)."
  [state]
  (let [s (homologation-state state)]
    {"homologation_state" (assoc s
                                  "phase" "record_emitted"
                                  "completionPct" 100)
     "homologation_record" {"$type" "com.etzhayyim.yamabiko.homologationRecord"
                            "trainsetId" (get s "trainsetId")
                            "serial" (get s "serial")
                            "trainsetDid" (get s "trainsetDid")
                            "upstreamRecords" (get s "upstreamRecords")
                            "authorityReview" (get s "authorityReview")
                            "kotoba-datomicAnchor" (get s "kotoba_datomicAnchor")
                            "recordedAt" "2026-05-27T13:30:00Z"}
     "next_node" "end"}))
