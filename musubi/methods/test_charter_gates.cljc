(ns musubi.methods.test-charter-gates
  "musubi — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-13-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 14)))))))

;; ── G7 — no bride price / dowry ──
(deftest test-g7-no-bride-price-dowry
  (is (= false (a-const (lex "ceremonyPerformanceAttestation") "bridePriceOrDowryAttested")))
  (is (= 0 (a-const (lex "silenMusubiReview") "bridePriceOrDowryEventsCount"))))

;; ── G3/G12 — no clergy class: officiant is community-witnessed-competent L5 vocation-flow ──
(deftest test-g3-no-clergy-class
  (let [c (lex "officiantAttestation")]
    (is (= "community-witnessed-competent" (a-const c "officiantClass")))
    (is (= "L5" (a-const c "lLevel")))
    (is (= "vocation-flow" (a-const c "employmentRelation")))
    (is (= 0 (a-const (lex "silenMusubiReview") "clergyClassOfficiantPenetrationPct")))))

;; ── G5 — per-party consent on every ceremony ──
(deftest test-g5-party-consent
  (let [req (required-union (lex "ceremonyPerformanceAttestation"))]
    (doseq [field ["partyConsentCids" "primaryPartyDids" "officiantAttestationCid"]]
      (is (contains? req field)))))

;; ── G10 — multi-generational witness cohort ──
(deftest test-g10-multi-gen-cohort
  (let [req (required-union (lex "ceremonyPerformanceAttestation"))]
    (doseq [field ["under18Count" "adultCount" "over65Count" "cohortRatio" "witnessAttestationCids"]]
      (is (contains? req field)))))

;; ── G6/G12 — review const ledger (no commercial software, vocation-flow compliant) ──
(deftest test-silen-review-ledger
  (let [c (lex "silenMusubiReview")]
    (is (= 0 (a-const c "commercialWeddingFuneralSoftwarePenetrationPct")))
    (is (= 10000 (a-const c "officiantVocationFlowCompliantRatioPctIntegerHundredths")))))

;; ── cross-doctrinal + open to community ──
(deftest test-cross-doctrinal-open
  (is (= true (a-const (lex "seasonalCeremonyCalendar") "openToCommunity"))))
