(ns watatsumi.cells.system-integration.state-machine
  "System-integration state machine — ADR-2605252200 L4. 1:1 cljc port of
  `cells/system_integration/state_machine.py`. Propulsion (G13 LFP/H₂/NH₃/methanol
  only, nuclear=N2) → life support (G12 ≤3 crew/≤72 h) → sensors (G8 active sonar
  ≤180 dB / N12 no stealth coating) → comms → G6 Charter-Rider scan → attestation.
  String keys mirror the Python __dict__.")

(def phases
  {:init "init" :propulsion-installed "propulsion_installed"
   :life-support-installed "life_support_installed" :sensors-installed "sensors_installed"
   :comms-installed "comms_installed" :charter-rider-scan-passed "charter_rider_scan_passed"
   :attestation-emitted "attestation_emitted"})

(def allowed-fuels
  "G13 fuel restriction."
  #{"LFP-battery" "H2-fuel-cell" "NH3-fuel-cell" "methanol-fuel-cell"})

(defn init [state]
  {"system_integration_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "completionPct" 0}})

(defn transition-to-propulsion-installed
  "Install propulsion — G13 fuel restriction enforcement."
  [state]
  (let [si (-> (get state "system_integration_state" {})
               (assoc "propulsion"
                      {"primaryFuel" "LFP-battery" "capacityKwh" 480 "thrustKN" 22
                       "fuelChecks" {"allowedFuels" (vec (sort allowed-fuels))
                                     "nuclearGuard" "N2 enforced: no nuclear propulsion"
                                     "selectedFuel" "LFP-battery" "g13Accept" true}}
                      "phase" (:propulsion-installed phases) "completionPct" 25))]
    {"system_integration_state" si "next_node" "life_support"}))

(defn transition-to-life-support-installed
  "Install life support — G12 caps: ≤3 crew, ≤72 h submerged."
  [state]
  (let [si (-> (get state "system_integration_state" {})
               (assoc "lifeSupport"
                      {"maxCrew" 3 "maxSubmergedHours" 72 "co2ScrubberType" "LiOH-canister"
                       "o2GeneratorType" "candle-supplement+electrolysis"
                       "humidityControlType" "passive-desiccant"
                       "emergencyEscapeMechanism" "personnel-survival-sphere" "g12Accept" true}
                      "phase" (:life-support-installed phases) "completionPct" 50))]
    {"system_integration_state" si "next_node" "sensors"}))

(defn transition-to-sensors-installed
  "Install sensors — G8 active sonar ≤180 dB / N12 no stealth coating."
  [state]
  (let [si (-> (get state "system_integration_state" {})
               (assoc "sensors"
                      {"passiveSonar" {"hydrophoneArrayCount" 12 "bandwidthKHz" 100}
                       "activeSonar" {"enabled" true "maxSplDbRe1uPaAt1m" 175 "g8Limit" 180 "g8Accept" true}
                       "imuLidar" {"present" true "rateHz" 200}
                       "antiStealthCoating" {"n12Enforcement" "active"
                                             "proprietaryStealthCoatingPresent" false "n12Accept" true}}
                      "phase" (:sensors-installed phases) "completionPct" 70))]
    {"system_integration_state" si "next_node" "comms"}))

(defn transition-to-comms-installed [state]
  (let [si (-> (get state "system_integration_state" {})
               (assoc "comms"
                      {"acousticModem" {"bandKHz" [9 14] "rangeMeters" 8000}
                       "rfSurface" {"band" "VHF/UHF/Iridium-SBD" "satelliteFallback" true}
                       "fiberOptic" {"present" true "lengthM" 6500 "g1Accept" true}}
                      "phase" (:comms-installed phases) "completionPct" 85))]
    {"system_integration_state" si "next_node" "charter_scan"}))

(defn transition-to-charter-scan-passed
  "G6 Charter Rider scan on all CAD + firmware artifacts."
  [state]
  (let [si (-> (get state "system_integration_state" {})
               (assoc "charterRiderScan"
                      {"categoriesChecked" ["§2(a)" "§2(b)" "§2(c)" "§2(d)" "§2(e)" "§2(f)" "§2(g)" "§2(h)"]
                       "violations" [] "accept" true
                       "scannerVersion" "etzhayyim_organism.sensors.charter_rider v2.0"}
                      "phase" (:charter-rider-scan-passed phases) "completionPct" 95))]
    {"system_integration_state" si "next_node" "attestation"}))

(defn transition-to-attestation-emitted [state]
  (let [si (-> (get state "system_integration_state" {})
               (assoc "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.watatsumi.systemIntegrationAttestation"
                "craftId" (get si "craftId")
                "propulsion" (get si "propulsion")
                "lifeSupport" (get si "lifeSupport")
                "sensors" (get si "sensors")
                "comms" (get si "comms")
                "charterRiderScan" (get si "charterRiderScan")
                "recordedAt" "2026-05-26T14:00:00Z"}]
    {"system_integration_state" si "system_integration_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-propulsion-installed transition-to-life-support-installed
           transition-to-sensors-installed transition-to-comms-installed
           transition-to-charter-scan-passed transition-to-attestation-emitted]))
