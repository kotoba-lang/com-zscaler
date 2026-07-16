(ns suki.methods.test-charter-gates
  "suki — structural charter-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(def ^:private FUEL-SET
  #{"B100-biodiesel-R0-R1" "diesel-LFP-hybrid-R0-R1"
    "LFP-electric-R2-plus" "H2-fuel-cell-R2-plus" "methanol-fuel-cell-R2-plus"})
(def ^:private RECORDS
  ["cabAttestation.json" "chassisAttestation.json" "electricalEcuAttestation.json"
   "emissionsAuditRecord.json" "fieldTestRecord.json" "hitchPtoAttestation.json"
   "paintAttestation.json" "powertrainAttestation.json"])

(defn- load-lex [name] (json/parse-string (slurp (java.io.File. lexdir name))))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (= parent field) (contains? x "knownValues")) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── G9/G10 Right-to-Repair ──
(deftest test-g10-right-to-repair-fields
  (let [req (required-union (load-lex "electricalEcuAttestation.json"))]
    (doseq [field ["ecuReflashNotManufacturerGated" "noDealerLockoutAttest"
                   "replacementPartsCataloguedOpen" "diagnosticCodesOpenInterpretable"
                   "g10RtrInvariantVerify" "unlockStateAtShipDefault"]]
      (is (contains? req field) (str "G10 RTR: electricalEcuAttestation must require " field)))))

(deftest test-g9-bootloader-and-canbus-are-open
  (let [doc (load-lex "electricalEcuAttestation.json")
        boots (known doc "name")]
    (is (and (seq boots) (every? #(str/includes? (str/lower-case %) "open") boots))
        (str "G9: bootloader options must all be open, got " boots))
    (is (= (known doc "canBusProtocol") #{"ISOBUS-ISO-11783-open"}))))

;; ── G7 — fuel transition: no fossil-only R2+ powertrain ──
(deftest test-g7-fuel-set-no-fossil-only-r2
  (doseq [name ["powertrainAttestation.json" "emissionsAuditRecord.json"]]
    (let [doc (load-lex name)
          field (if (str/starts-with? name "powertrain") "fuelType" "currentFuelType")]
      (is (= (known doc field) FUEL-SET) (str "G7: " name " " field " must be exactly " FUEL-SET))))
  (is (contains? (required-union (load-lex "powertrainAttestation.json")) "g7PhaseGateVerify")))

;; ── G8 — emissions audit ──
(deftest test-g8-emissions-audit-required
  (let [req (required-union (load-lex "emissionsAuditRecord.json"))]
    (doseq [field ["noxGramPerKwh" "particulateMatterGramPerKwh" "iso8178NrscPass"
                   "iso8178NrtcPass" "jurisdictionCertifications"]]
      (is (contains? req field) (str "G8: emissionsAuditRecord must require " field)))))

;; ── G4 — witness quorum on every attestation ──
(deftest test-g4-witness-quorum-on-all-records
  (doseq [name RECORDS]
    (is (contains? (required-union (load-lex name)) "witnessRobotDids") (str "G4: " name " must require witnessRobotDids"))))

;; ── N11 — no surveillance hardware in the cab ──
(deftest test-n11-no-surveillance-hardware
  (is (contains? (required-union (load-lex "cabAttestation.json")) "noSurveillanceHardwareVerified")))

;; ── G3 — modular implement ──
(deftest test-g3-implement-no-drm-multivendor
  (let [doc (load-lex "hitchPtoAttestation.json")
        req (required-union doc)]
    (doseq [field ["noDrmDetectionVerified" "multiVendorCompatVerify"]]
      (is (contains? req field) (str "G3: hitchPtoAttestation must require " field)))
    (is (= (known doc "implementDetectionProtocol") #{"ISOBUS-ISO-11783-open"}))
    (is (= (known doc "hitchCategory") #{"Cat-I" "Cat-II" "Cat-III"}))))

;; ── G12 — KPI cap ──
(deftest test-g12-kpi-cap-verified
  (let [req (required-union (load-lex "fieldTestRecord.json"))]
    (doseq [field ["g12KpiCapVerify" "saeAutonomyLevel" "maxRoadSpeedKmh" "maxFieldSpeedKmh" "maxAxleLoadT"]]
      (is (contains? req field) (str "G12: fieldTestRecord must require " field)))))
