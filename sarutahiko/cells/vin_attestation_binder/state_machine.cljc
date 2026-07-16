(ns sarutahiko.cells.vin-attestation-binder.state-machine
  "VIN-attestation-binder state machine — ADR-2605252500 terminal cell (G2 + G13).
  1:1 cljc port of `cells/vin_attestation_binder/state_machine.py`. Aggregate all
  upstream attestations → per-VIN vehicleManufactureRecord; issue per-VIN DID +
  kotoba-datomic anchor (G2 open VIN registry). String keys mirror the Python
  __dict__.")

(def phases
  {:init "init" :records-collected "records_collected" :vin-assigned "vin_assigned"
   :vehicle-did-issued "vehicle_did_issued" :kotoba-datomic-anchored "kotoba-datomic_anchored"
   :record-emitted "record_emitted"})

(defn init [state]
  {"binder_state" {"phase" (:init phases)
                   "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                   "completionPct" 0}})

(defn transition-to-records-collected [state]
  (let [s (-> (get state "binder_state" {})
              (assoc "upstreamRecords" {"frameAttestation" "bafkreiframe..."
                                        "powertrainAttestation" "bafkreipt..."
                                        "cabBodyAttestation" "bafkreicab..."
                                        "marriageAttestation" "bafkreimarry..."
                                        "paintAttestation" "bafkreipaint..."
                                        "electricalAttestation" "bafkreielec..."
                                        "roadTestRecord" "bafkreiroad..."
                                        "emissionsAuditRecord" "bafkreiemis..."}
                     "phase" (:records-collected phases) "completionPct" 25))]
    {"binder_state" s "next_node" "vin"}))

(defn transition-to-vin-assigned [state]
  (let [s (-> (get state "binder_state" {})
              (assoc "vin" "ETZSARUTAHIKO00000A0001"   ;; 17-char VIN equivalent
                     "phase" (:vin-assigned phases) "completionPct" 50))]
    {"binder_state" s "next_node" "did"}))

(defn transition-to-vehicle-did-issued [state]
  (let [s0 (get state "binder_state" {})
        s (assoc s0 "vehicleDid" (str "did:web:etzhayyim.com:sarutahiko:vehicle:" (get s0 "vin"))
                 "phase" (:vehicle-did-issued phases) "completionPct" 70)]
    {"binder_state" s "next_node" "anchor"}))

(defn transition-to-kotoba-datomic-anchored [state]
  (let [s (-> (get state "binder_state" {})
              (assoc "kotoba_datomicAnchor" {"membraneNamespace" "com.etzhayyim.sarutahiko"
                                             "anchorTxHash" "0xSARUTAHIKOVINBINDER..."
                                             "l2Chain" "Base Sepolia (R0 dry-run)"
                                             "anchorBlockNumber" 0 "g2Compliant" true "openVinRegistry" true}
                     "phase" (:kotoba-datomic-anchored phases) "completionPct" 90))]
    {"binder_state" s "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [s (-> (get state "binder_state" {})
              (assoc "phase" (:record-emitted phases) "completionPct" 100))
        record {"$type" "etzhayyim:sarutahiko:vehicleManufactureRecord"
                "chassisId" (get s "chassisId")
                "vin" (get s "vin")
                "vehicleDid" (get s "vehicleDid")
                "upstreamRecords" (get s "upstreamRecords")
                "kotoba-datomicAnchor" (get s "kotoba_datomicAnchor")
                "recordedAt" "2026-05-26T20:00:00Z"}]
    {"binder_state" s "vehicle_manufacture_record" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-records-collected transition-to-vin-assigned
           transition-to-vehicle-did-issued transition-to-kotoba-datomic-anchored
           transition-to-record-emitted]))
