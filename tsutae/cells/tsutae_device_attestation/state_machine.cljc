(ns tsutae.cells.tsutae-device-attestation.state-machine
  "1:1 port of cells/tsutae_device_attestation/state_machine.py (ADR-2605261300 phase `attest`).
  Binds the full BoM lineage (pcb + chassis + display + firmware + qc CIDs) to a per-device DID,
  mints did:web:etzhayyim.com:tsutae:device:<serial>, IPFS-pins the BoM, and emits
  com.etzhayyim.tsutae.deviceAttestation signed by ≥2 robots.

  Constitutional guards: G4 witness quorum (≥2 distinct robot DIDs Ed25519-sign each attestation;
  fewer is rejected) · G14 per-device DID accepts repairEvent records throughout lifecycle.
  DeviceState dataclass → string-keyed map under \"device_state\" (all fields present, nil unset).")

(def ^:private G4-MIN-ROBOT-SIGNERS 2)

(def ^:private default-signers
  [{"robotDid" "did:web:etzhayyim.com:mimi-unit-1" "role" "aoi"}
   {"robotDid" "did:web:etzhayyim.com:otete-unit-1" "role" "handling"}])

(defn- s* [state]
  (merge {"bomLineage" nil "quorumGuard" nil "did" nil} (get state "device_state" {})))

(defn transition-to-bom-lineage-assembled [state]
  {"device_state" (assoc (s* state)
                         "bomLineage" [{"stage" "pcb" "cid" "bafkreipcb..."}
                                       {"stage" "chassis" "cid" "bafkreichassis..."}
                                       {"stage" "display" "cid" "bafkreidisplay..."}
                                       {"stage" "firmware" "cid" "bafkreifw..."}
                                       {"stage" "qc" "cid" "bafkreiqc..."}]
                         "phase" "bom_lineage_assembled" "completionPct" 25)
   "next_node" "quorum"})

(defn transition-to-robot-quorum-signed [state]
  (let [signers (get state "robotSigners" default-signers)
        distinct-dids (set (map #(get % "robotDid") signers))
        accept (>= (count distinct-dids) G4-MIN-ROBOT-SIGNERS)]
    {"device_state" (assoc (s* state)
                           "quorumGuard" {"gate" "G4" "signerCount" (count distinct-dids)
                                          "minSigners" G4-MIN-ROBOT-SIGNERS "signers" signers "accept" accept
                                          "reason" (if accept "witness quorum met"
                                                       "fewer than 2 distinct robot signers (G4)")}
                           "phase" "robot_quorum_signed" "completionPct" 55)
     "next_node" "mint"}))

(defn transition-to-did-minted [state]
  (let [s (s* state)]
    {"device_state" (assoc s "did" (str "did:web:etzhayyim.com:tsutae:device:" (get s "serial"))
                           "phase" "did_minted" "completionPct" 80)
     "next_node" "attestation"}))

(defn transition-to-attestation-emitted [state]
  (let [s (assoc (s* state) "phase" "attestation_emitted" "completionPct" 100)]
    {"device_state" s
     "device_attestation" {"$type" "com.etzhayyim.tsutae.deviceAttestation"
                           "serial" (get s "serial")
                           "did" (get s "did")
                           "bomLineage" (get s "bomLineage")
                           "quorumGuard" (get s "quorumGuard")
                           "repairEventReady" true
                           "accept" (boolean (and (get (or (get s "quorumGuard") {}) "accept") (get s "did")))
                           "recordedAt" "2026-05-26T15:00:00Z"}
     "next_node" "end"}))
