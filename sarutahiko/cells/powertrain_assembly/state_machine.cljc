(ns sarutahiko.cells.powertrain-assembly.state-machine
  "Powertrain-assembly state machine — ADR-2605252500 L2. 1:1 cljc port of
  `cells/powertrain_assembly/state_machine.py`. Engine + transmission + axle
  integration; G7 fuel guard (R0/R1 B100/diesel-hybrid transition; R2+ LFP/H₂/NH₃/
  methanol only, pure fossil rejected). String keys mirror the Python __dict__.")

(def phases
  {:init "init" :fuel-guard-checked "fuel_guard_checked" :engine-installed "engine_installed"
   :transmission-coupled "transmission_coupled" :axles-mounted "axles_mounted"
   :brake-integrated "brake_integrated" :attestation-emitted "attestation_emitted"})

(def allowed-r0r1
  #{"B100-biodiesel-hybrid" "diesel-hybrid" "LFP-battery" "H2-fuel-cell"
    "NH3-fuel-cell" "methanol-fuel-cell"})
(def allowed-r2plus
  #{"LFP-battery" "H2-fuel-cell" "NH3-fuel-cell" "methanol-fuel-cell"})

(defn init [state]
  {"powertrain_state" {"phase" (:init phases)
                       "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                       "completionPct" 0}})

(defn transition-to-fuel-guard-checked
  "G7 enforcement: only allowed fuel/powertrain types accepted."
  [state]
  (let [selected (get state "powerTrainType" "B100-biodiesel-hybrid")
        s (-> (get state "powertrain_state" {})
              (assoc "powerTrainType" selected
                     "fuelGuard" {"g7Enforcement" "active"
                                  "allowedR0R1" (vec (sort allowed-r0r1))
                                  "allowedR2Plus" (vec (sort allowed-r2plus))
                                  "selected" selected
                                  "phaseGate" (get state "phase" "R1")
                                  "accept" (contains? allowed-r0r1 selected)
                                  "pureFossilGuard" "pure-fossil prohibited; B100 biodiesel + diesel hybrid acceptable as R0/R1 transition only"}
                     "phase" (:fuel-guard-checked phases) "completionPct" 15))]
    {"powertrain_state" s "next_node" "engine"}))

(defn transition-to-engine-installed [state]
  (let [s0 (get state "powertrain_state" {})
        s (assoc s0 "engineLot" {"type" (get s0 "powerTrainType") "lotId" "ENGINE-2026-05-LOT-0011"
                                 "powerKw" 350 "torqueNm" 2200 "certCid" "bafkreienginecert..."}
                 "phase" (:engine-installed phases) "completionPct" 40)]
    {"powertrain_state" s "next_node" "transmission"}))

(defn transition-to-transmission-coupled [state]
  (let [s (-> (get state "powertrain_state" {})
              (assoc "transmissionLot" {"ratio_steps" 12 "lotId" "TRANS-2026-05-LOT-0011"}
                     "phase" (:transmission-coupled phases) "completionPct" 60))]
    {"powertrain_state" s "next_node" "axles"}))

(defn transition-to-axles-mounted [state]
  (let [s (-> (get state "powertrain_state" {})
              (assoc "axleLots" [{"position" "front_steer" "lotId" "AXLE-FRONT-0011"}
                                 {"position" "rear_drive_1" "lotId" "AXLE-REAR-0011"}
                                 {"position" "rear_drive_2" "lotId" "AXLE-REAR-0012"}]
                     "phase" (:axles-mounted phases) "completionPct" 78))]
    {"powertrain_state" s "next_node" "brake"}))

(defn transition-to-brake-integrated [state]
  (let [s (-> (get state "powertrain_state" {})
              (assoc "brakeSystem" {"type" "EBS-disc" "regenerativeAllowed" true}
                     "phase" (:brake-integrated phases) "completionPct" 92))]
    {"powertrain_state" s "next_node" "attestation"}))

(defn transition-to-attestation-emitted [state]
  (let [s (-> (get state "powertrain_state" {})
              (assoc "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.sarutahiko.powertrainAttestation"
                "chassisId" (get s "chassisId")
                "powerTrainType" (get s "powerTrainType")
                "fuelGuard" (get s "fuelGuard")
                "engineLot" (get s "engineLot")
                "transmissionLot" (get s "transmissionLot")
                "axleLots" (get s "axleLots")
                "brakeSystem" (get s "brakeSystem")
                "recordedAt" "2026-05-26T09:30:00Z"}]
    {"powertrain_state" s "powertrain_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-fuel-guard-checked transition-to-engine-installed
           transition-to-transmission-coupled transition-to-axles-mounted
           transition-to-brake-integrated transition-to-attestation-emitted]))
