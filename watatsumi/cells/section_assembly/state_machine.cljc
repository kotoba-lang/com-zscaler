(ns watatsumi.cells.section-assembly.state-machine
  "Section-assembly state machine — ADR-2605252200 L2. 1:1 cljc port of
  `cells/section_assembly/state_machine.py`. Stack N rings → internal frames →
  bulkheads → penetrators (N1: NO weapon mounts) → ≥2-robot attestation. String
  keys mirror the Python __dict__.")

(def phases
  {:init "init" :rings-verified "rings_verified" :rings-stacked "rings_stacked"
   :internal-frames-installed "internal_frames_installed"
   :penetrators-installed "penetrators_installed" :attestation-emitted "attestation_emitted"})

(def forbidden-penetrator-kinds
  "N1 enforcement: no weapon-mount penetrators."
  #{"torpedo-tube" "missile-silo" "mine-laying-bay" "weapon-mount"
    "ordnance-stowage" "depth-charge-rack"})

(defn init [state]
  {"section_assembly_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "sectionIndex" (get state "sectionIndex" 0)
    "completionPct" 0}})

(defn transition-to-rings-verified [state]
  (let [sa (-> (get state "section_assembly_state" {})
               (assoc "phase" (:rings-verified phases)
                      "ringAttestations" (mapv (fn [i] {"ringIndex" i "attestationCid" (str "bafkreiring" i "...")})
                                               (range 4))
                      "completionPct" 15))]
    {"section_assembly_state" sa "next_node" "stack"}))

(defn transition-to-rings-stacked [state]
  (let [sa (-> (get state "section_assembly_state" {})
               (assoc "phase" (:rings-stacked phases) "sectionLengthMm" 12000 "completionPct" 40))]
    {"section_assembly_state" sa "next_node" "frames"}))

(defn transition-to-frames-installed [state]
  (let [sa (-> (get state "section_assembly_state" {})
               (assoc "phase" (:internal-frames-installed phases)
                      "bulkheads" [{"index" 0 "kind" "pressure-bulkhead" "positionMm" 0}
                                   {"index" 1 "kind" "watertight-door" "positionMm" 6000}
                                   {"index" 2 "kind" "pressure-bulkhead" "positionMm" 12000}]
                      "completionPct" 65))]
    {"section_assembly_state" sa "next_node" "penetrators"}))

(defn transition-to-penetrators-installed
  "Install hull penetrators. N1 enforcement: no weapon mounts."
  [state]
  (let [pens [{"kind" "personnel-hatch" "positionMm" 1500 "diaMm" 800}
              {"kind" "sensor-head-passive-sonar" "positionMm" 3000 "diaMm" 200}
              {"kind" "acoustic-modem" "positionMm" 5000 "diaMm" 150}
              {"kind" "manipulator-mount-otete" "positionMm" 7500 "diaMm" 250}
              {"kind" "ballast-tank-vent" "positionMm" 10000 "diaMm" 100}]
        weapon-check {"n1Enforcement" "active"
                      "forbiddenKinds" (vec (sort forbidden-penetrator-kinds))
                      "violationsFound" (filterv #(contains? forbidden-penetrator-kinds (get % "kind")) pens)
                      "accept" true}
        sa (-> (get state "section_assembly_state" {})
               (assoc "phase" (:penetrators-installed phases)
                      "penetrators" pens "weaponMountCheck" weapon-check "completionPct" 90))]
    {"section_assembly_state" sa "next_node" "attestation"}))

(def ^:private robot-sigs
  [{"robotDid" "did:web:etzhayyim.com:ama-unit-1" "role" "structural"
    "timestamp" "2026-05-26T12:00:00Z" "signature" "..."}
   {"robotDid" "did:web:etzhayyim.com:mimi-marine-unit-1" "role" "metrology"
    "timestamp" "2026-05-26T12:00:05Z" "signature" "..."}])

(defn transition-to-attestation-emitted [state]
  (let [sa (-> (get state "section_assembly_state" {})
               (assoc "robotSignatures" robot-sigs
                      "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.watatsumi.sectionAssemblyAttestation"
                "craftId" (get sa "craftId")
                "sectionIndex" (get sa "sectionIndex")
                "ringAttestations" (get sa "ringAttestations")
                "sectionLengthMm" (get sa "sectionLengthMm")
                "bulkheads" (get sa "bulkheads")
                "penetrators" (get sa "penetrators")
                "weaponMountCheck" (get sa "weaponMountCheck")
                "attestingRobots" robot-sigs
                "recordedAt" "2026-05-26T12:00:10Z"}]
    {"section_assembly_state" sa "section_assembly_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-rings-verified transition-to-rings-stacked
           transition-to-frames-installed transition-to-penetrators-installed
           transition-to-attestation-emitted]))
