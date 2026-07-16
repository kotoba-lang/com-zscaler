(ns mizuho.methods.test-charter-gates
  "mizuho — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(defn- manifest []
  (let [e (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))
        gm (into {} (map (fn [g] [(:gate/id g) g]) (:actor/gates e)))]
    {"constitutionalGates" {"gates" gm}
     "gates" gm
     "nonGoals" (:actor/non-goals e)
     "cells" (:actor/cells e)
     "name" (:actor/id e)
     "purpose" (:actor/purpose e)
     "tier" "Tier-B"
     "status" (some-> (:actor/status e) name)}))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- collect [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x attr)) (swap! acc assoc parent (get x attr)))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- a-const [doc field] (get (collect doc "const") field))
(defn- known [doc field] (some-> (get (collect doc "knownValues") field) set))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-12-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 13)))))))

;; ── G5/G6 — no bottled water + no mandatory fluoridation ──
(deftest test-g5-g6-no-bottled-no-fluoridation
  (let [doc (lex "silenMizuhoReview")]
    (is (= 0 (a-const doc "bottledWaterUnitsDistributed")))
    (is (= false (a-const doc "fluoridationAdditionAttested")))))

;; ── G11 — water-source waqf-inalienability ──
(deftest test-g11-waqf-source
  (is (= true (a-const (lex "waterSupplySourceRegistry") "waqfInalienabilityAttested")))
  (is (= 10000 (a-const (lex "silenMizuhoReview") "sourceWaqfInalienabilityAttestedRatioPctIntegerHundredths"))))

;; ── G12/G4 — vocation-flow operators + no commercial utility software ──
(deftest test-g12-g4-vocation-no-commercial-software
  (let [doc (lex "silenMizuhoReview")]
    (is (= 10000 (a-const doc "operatorVocationFlowCompliantRatioPctIntegerHundredths")))
    (is (= 0 (a-const doc "commercialUtilitySoftwarePenetrationPct")))))

;; ── G3 — WHO-guideline water quality with a critical-halt status ──
(deftest test-g3-who-quality-halt
  (let [doc (lex "waterQualityAttestation")
        req (required-union doc)]
    (doseq [field ["whoLimitIntegerHundredths" "overallComplianceStatus" "supplyGrade"]]
      (is (contains? req field)))
    (is (contains? (known doc "overallComplianceStatus") "non-compliant-critical-halt"))))

;; ── wastewater discharge is under a jurisdictional permit ──
(deftest test-wastewater-permit
  (let [req (required-union (lex "wastewaterDischargeAttestation"))]
    (doseq [field ["jurisdictionalPermitCid" "permitCompliant" "permitLimitIntegerHundredths"]]
      (is (contains? req field)))))

;; ── contamination incidents are notified + categorized ──
(deftest test-contamination-incident-notified
  (let [req (required-union (lex "waterContaminationIncident"))]
    (doseq [field ["notifiedAtUtc" "severity" "incidentCategory" "sourceRegistryCid"]]
      (is (contains? req field)))))
