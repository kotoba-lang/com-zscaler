(ns sarutahiko.cells.electrical-integration.state-machine
  "Electrical-integration state machine — ADR-2605252500 L5b. 1:1 cljc port of
  `cells/electrical_integration/state_machine.py`. Harness routing → ECU flash →
  G1/N8 open-source firmware verification → diagnostics. String keys mirror the
  Python __dict__."
  (:require [clojure.string :as str]))

(def phases
  {:init "init" :harness-routed "harness_routed" :ecu-flashed "ecu_flashed"
   :open-source-verified "open_source_verified" :diagnostics-passed "diagnostics_passed"
   :attestation-emitted "attestation_emitted"})

(defn init [state]
  {"electrical_state" {"phase" (:init phases)
                       "chassisId" (get state "chassisId" "SARUTAHIKO-CHASSIS-0001")
                       "completionPct" 0}})

(defn transition-to-harness-routed [state]
  (let [s (-> (get state "electrical_state" {})
              (assoc "harnessLayout" {"totalWireMassKg" 28 "branchCount" 14
                                      "routingCid" "bafkreiroute..." "akariUnits" 2}
                     "phase" (:harness-routed phases) "completionPct" 25))]
    {"electrical_state" s "next_node" "flash"}))

(defn transition-to-ecu-flashed [state]
  (let [s (-> (get state "electrical_state" {})
              (assoc "ecuFlash" {"ecuModel" "etzhayyim-open-ecu-v1"
                                 "firmwareCid" "bafkreiopenecuFW..."
                                 "firmwareLicense" "Apache 2.0 + Charter Compliance Rider v2.0"
                                 "flashTimestamp" "2026-05-26T16:30:00Z"}
                     "phase" (:ecu-flashed phases) "completionPct" 55))]
    {"electrical_state" s "next_node" "verify"}))

(defn transition-to-open-source-verified
  "G1 + N8 enforcement: firmware open-source license required."
  [state]
  (let [s0 (get state "electrical_state" {})
        license-str (get (get s0 "ecuFlash" {}) "firmwareLicense" "")
        has-apache (str/includes? license-str "Apache 2.0")
        has-rider (str/includes? license-str "Charter Compliance Rider")
        s (assoc s0 "openSourceVerification"
                 {"g1Enforcement" "active" "n8Enforcement" "active"
                  "firmwareLicense" license-str
                  "containsApache2" has-apache
                  "containsCharterRider" has-rider
                  "proprietaryNdaPresent" false
                  "accept" (and has-apache has-rider)}
                 "phase" (:open-source-verified phases) "completionPct" 75)]
    {"electrical_state" s "next_node" "diagnostics"}))

(defn transition-to-diagnostics-passed [state]
  (let [s (-> (get state "electrical_state" {})
              (assoc "diagnostics" {"obdIIScan" "PASS" "canBusIntegrity" "PASS"
                                    "wakeUpSleepCycle" "PASS" "shortCircuitCheck" "PASS"
                                    "groundResistanceOhms" 0.04}
                     "phase" (:diagnostics-passed phases) "completionPct" 92))]
    {"electrical_state" s "next_node" "attestation"}))

(defn transition-to-attestation-emitted [state]
  (let [s (-> (get state "electrical_state" {})
              (assoc "phase" (:attestation-emitted phases) "completionPct" 100))
        record {"$type" "com.etzhayyim.sarutahiko.electricalAttestation"
                "chassisId" (get s "chassisId")
                "harnessLayout" (get s "harnessLayout")
                "ecuFlash" (get s "ecuFlash")
                "openSourceVerification" (get s "openSourceVerification")
                "diagnostics" (get s "diagnostics")
                "recordedAt" "2026-05-26T17:00:00Z"}]
    {"electrical_state" s "electrical_attestation" record "next_node" "end"}))

(defn run-chain [input-state]
  (reduce (fn [s f] (f s))
          (merge input-state (init input-state))
          [transition-to-harness-routed transition-to-ecu-flashed
           transition-to-open-source-verified transition-to-diagnostics-passed
           transition-to-attestation-emitted]))
