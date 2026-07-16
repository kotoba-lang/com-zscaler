(ns yamabiko.cells.traction-electrical.state-machine
  "1:1 port of cells/traction_electrical/state_machine.py — ADR-2605252600 L4.

  25 kV AC / 1500 V DC pantograph + traction inverter + ATP/ATO firmware.
  G1 + N5 enforcement: ATP/ATO firmware Apache 2.0 + Charter Rider, no NDA.
  G7 propulsion guard: R0/R1 BEMU+H₂ acceptable; R2+ full electric only.")

(defn- traction-state
  "TractionState defaults merged with any existing \"traction_state\" map (string keys).
   Input state values override defaults."
  [state]
  (let [existing (get state "traction_state" {})
        trainset-id (or (get existing "trainsetId")
                        (get state "trainsetId")
                        "YAMABIKO-TRAINSET-0001")]
    (merge {"phase" "init"
            "trainsetId" trainset-id
            "completionPct" 0
            "propulsionType" nil
            "propulsionGuard" nil
            "pantograph" nil
            "inverter" nil
            "atpAtoFirmware" nil
            "openSourceVerification" nil}
           existing)))

(defn transition-to-propulsion-guard-checked
  "Check propulsion type against G7 gate (R0/R1: AC/DC/BEMU/H2; R2+: adds NH3)."
  [state]
  (let [s (traction-state state)
        allowed-r0r1 #{"overhead-25kV-AC" "overhead-1500V-DC" "third-rail-750V-DC" "BEMU-LFP" "H2-fuel-cell-hybrid"}
        allowed-r2plus #{"overhead-25kV-AC" "overhead-1500V-DC" "third-rail-750V-DC" "BEMU-LFP" "H2-fuel-cell-hybrid" "NH3-fuel-cell-hybrid"}
        selected (get state "propulsionType" "overhead-25kV-AC")]
    {"traction_state" (assoc s
                              "propulsionType" selected
                              "propulsionGuard" {"g7Enforcement" "active"
                                                 "allowedR0R1" (sort allowed-r0r1)
                                                 "allowedR2Plus" (sort allowed-r2plus)
                                                 "selected" selected
                                                 "phaseGate" (get state "phase" "R1")
                                                 "accept" (contains? allowed-r0r1 selected)
                                                 "dieselGuard" "R2+ diesel locomotive prohibited (N4)"}
                              "phase" "propulsion_guard_checked"
                              "completionPct" 15)
     "next_node" "pantograph"}))

(defn transition-to-pantograph-installed
  "Install pantograph system (2x wing, 25 kV AC rated)."
  [state]
  (let [s (traction-state state)]
    {"traction_state" (assoc s
                             "pantograph" {"count" 2
                                           "type" "wing"
                                           "ratedVoltageV" 25000
                                           "currentA" 1000}
                             "phase" "pantograph_installed"
                             "completionPct" 35)
     "next_node" "inverter"}))

(defn transition-to-inverter-installed
  "Install SiC-MOSFET inverter."
  [state]
  (let [s (traction-state state)]
    {"traction_state" (assoc s
                             "inverter" {"type" "SiC-MOSFET"
                                         "ratedPowerKw" 4880
                                         "efficiencyPct" 98.2}
                             "phase" "inverter_installed"
                             "completionPct" 55)
     "next_node" "atp"}))

(defn transition-to-atp-ato-flashed
  "Flash ATP/ATO firmware (ETCS-Level-2, GoA-3; N7: no GoA 4 Wave 1)."
  [state]
  (let [s (traction-state state)]
    {"traction_state" (assoc s
                             "atpAtoFirmware" {"atpStandard" "ETCS-Level-2"
                                              "atoLevel" "GoA-3"
                                              "atoMaxLevel" 3
                                              "n7Note" "GoA 4 = N7 constitutional non-goal Wave 1"
                                              "firmwareCid" "bafkreiatp-ato-fw..."
                                              "firmwareLicense" "Apache 2.0 + Charter Compliance Rider v2.0"
                                              "flashTimestamp" "2026-05-26T14:00:00Z"}
                             "phase" "atp_ato_flashed"
                             "completionPct" 75)
     "next_node" "verify"}))

(defn transition-to-open-source-verified
  "Verify open-source ATP/ATO firmware (G1 + N5: Apache 2.0 + Charter Rider required)."
  [state]
  (let [s (traction-state state)
        license-str (get-in s ["atpAtoFirmware" "firmwareLicense"] "")]
    {"traction_state" (assoc s
                             "openSourceVerification" {"g1Enforcement" "active"
                                                       "n5Enforcement" "active"
                                                       "firmwareLicense" license-str
                                                       "containsApache2" (clojure.string/includes? license-str "Apache 2.0")
                                                       "containsCharterRider" (clojure.string/includes? license-str "Charter Compliance Rider")
                                                       "proprietaryNdaPresent" false
                                                       "accept" (and (clojure.string/includes? license-str "Apache 2.0")
                                                                     (clojure.string/includes? license-str "Charter Compliance Rider"))}
                             "phase" "open_source_verified"
                             "completionPct" 92)
     "next_node" "attestation"}))

(defn transition-to-attestation-emitted
  "Emit final traction electrical attestation."
  [state]
  (let [s (traction-state state)]
    {"traction_state" (assoc s
                             "phase" "attestation_emitted"
                             "completionPct" 100)
     "traction_electrical_attestation" {"$type" "com.etzhayyim.yamabiko.tractionElectricalAttestation"
                                        "trainsetId" (get s "trainsetId")
                                        "propulsionType" (get s "propulsionType")
                                        "propulsionGuard" (get s "propulsionGuard")
                                        "pantograph" (get s "pantograph")
                                        "inverter" (get s "inverter")
                                        "atpAtoFirmware" (get s "atpAtoFirmware")
                                        "openSourceVerification" (get s "openSourceVerification")
                                        "recordedAt" "2026-05-26T14:00:10Z"}
     "next_node" "end"}))
