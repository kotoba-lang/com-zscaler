(ns hodoki.methods.test-charter-gates
  "hodoki — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (= parent field) (contains? x "knownValues"))
                                   (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set + civilian-only non-goals ──
(deftest test-all-14-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 15))))
        "manifest must declare G1–G14")))

(deftest test-civilian-only-no-military-aerospace
  (let [ng (get (manifest) "nonGoals")
        n (get ng "goals" (get ng "nonGoals" ng))]
    (is (str/includes? (str/lower-case (get n "N1")) "military") "N1 must exclude military vehicles")
    (is (or (str/includes? (str/lower-case (get n "N2")) "aerospace")
            (str/includes? (str/lower-case (get n "N2")) "aircraft")) "N2 must exclude aerospace vehicles")))

;; ── G8 (CONSTITUTIONAL FIRST) — mandatory data wipe before disassembly ──
(deftest test-g8-data-wipe-attestation
  (let [doc (lex "dataWipeAttestation")
        req (required-union doc)
        methods (known doc "method")]
    (doseq [field ["ecuWipes" "method" "verified" "g8Compliant"]]
      (is (contains? req field) (str "G8: dataWipeAttestation must require " field)))
    (is (and (contains? methods "cryptographic-destruction") (contains? methods "physical-chip-shred"))
        "G8: wipe method must include cryptographic destruction + physical chip-shred fallback")))

;; ── G6 — F-gas capture ≥95%, no atmospheric venting ──
(deftest test-g6-fgas-capture
  (let [req (required-union (lex "depollutionAttestation"))]
    (doseq [field ["fgasCapture" "recoveryRatePct" "atmosphericVentingDetected" "g6Compliant"]]
      (is (contains? req field) (str "G6: depollutionAttestation must require " field)))))

;; ── G7 — Li-ion thermal safety ──
(deftest test-g7-battery-thermal-safety
  (let [req (required-union (lex "batteryHandlingRecord"))]
    (doseq [field ["g7Compliant" "soh" "thermalBaseline" "routingDecision"]]
      (is (contains? req field) (str "G7: batteryHandlingRecord must require " field)))))

;; ── G12 (CONSTITUTIONAL FIRST) — right-to-repair part catalog ──
(deftest test-g12-right-to-repair-catalog
  (let [req (required-union (lex "partsHarvestCatalog"))]
    (doseq [field ["g12RightToRepairInvariant" "g12NoDrmCircumvention" "g12NoProprietaryLockIn"
                   "g12PublicDiscovery" "vinProvenance" "partDid" "catalogCid"]]
      (is (contains? req field) (str "G12: partsHarvestCatalog must require " field)))))

;; ── G14 — PGM (Pt/Pd/Rh) yield audit ──
(deftest test-g14-pgm-yield-audit
  (let [req (required-union (lex "catalystRecoveryRecord"))]
    (is (and (contains? req "g14Compliant") (contains? req "yieldAudit"))
        "G14: catalystRecoveryRecord must require g14Compliant + yieldAudit")))

;; ── G13 — ≥95% recovery + cross-actor circular feed (→ kanayama) ──
(deftest test-g13-circular-feed-to-kanayama
  (let [req (required-union (lex "shredOutputAttestation"))]
    (doseq [field ["g13Compliant" "kanayamaHandoff" "ferrousMassKg" "nonFerrousAlMassKg"]]
      (is (contains? req field) (str "G13: shredOutputAttestation must require " field " (circular feed to kanayama)")))))

;; ── intake: stolen / charter screen at the door ──
(deftest test-intake-screens-stolen-and-charter
  (let [req (required-union (lex "elvIntakeRecord"))]
    (doseq [field ["charterScan" "stolenRegistryCheck" "n7TitleVerified" "vin"]]
      (is (contains? req field) (str "intake: elvIntakeRecord must require " field)))))
