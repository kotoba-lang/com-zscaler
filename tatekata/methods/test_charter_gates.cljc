(ns tatekata.methods.test-charter-gates
  "tatekata 建方 — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- nongoals []
  (let [ng (get (manifest) "nonGoals")]
    (or (get ng "goals") (get ng "nonGoals") ng)))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-14-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 15)))) "manifest must declare G1–G14")))

;; ── scope discipline: no high-rise, execution-only (no design), no cost estimation ──
(deftest test-no-high-rise
  (let [n (nongoals)]
    (is (or (str/includes? (str/lower-case (get n "N1")) "high-rise")
            (str/includes? (str/lower-case (get n "N1")) "stories")) "N1 must exclude high-rise")))

(deftest test-execution-only-no-architectural-design
  (let [n (nongoals)]
    (is (str/includes? (str/lower-case (get n "N9")) "design") "N9: tatekata is execution-only — no architectural design")))

(deftest test-no-cost-estimation
  (let [n (nongoals)]
    (is (or (str/includes? (str/lower-case (get n "N10")) "cost")
            (str/includes? (str/lower-case (get n "N10")) "budget")) "N10: no cost estimation / budgeting (finance domain)")))

;; ── G3 witness quorum on progress/material/site records ──
(deftest test-g3-witness-quorum
  (doseq [name ["constructionProgressRecord" "materialAttestation" "siteAttestation"]]
    (is (contains? (required-union (lex name)) "attestingRobots") (str "G3: " name " must require attestingRobots"))))

;; ── G5 sourcing audit — material provenance ──
(deftest test-g5-material-provenance
  (let [req (required-union (lex "materialAttestation"))]
    (doseq [field ["grade" "standard" "qcResult" "supplierName"]]
      (is (contains? req field) (str "G5: materialAttestation must require " field)))))

;; ── G2 site survey before entry ──
(deftest test-g2-site-survey
  (let [req (required-union (lex "siteAttestation"))]
    (doseq [field ["soilClassification" "surveyDate"]]
      (is (contains? req field) (str "G2: siteAttestation must require " field)))))

;; ── safety-incident transparency ──
(deftest test-safety-incident-transparency
  (let [req (required-union (lex "safetyIncidentReport"))]
    (doseq [field ["incidentType" "severity" "incidentDate" "reportDate"]]
      (is (contains? req field) (str "safety: safetyIncidentReport must require " field)))))
