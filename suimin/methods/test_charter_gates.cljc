(ns suimin.methods.test-charter-gates
  "suimin — structural charter-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(def ^:private PROVENANCE-WHITELIST #{"pmid" "doi" "cochrane-cd-id" "guideline-id" "icsd3-code" "icd11-code"})
(def ^:private GRADE-VALUES #{"high" "moderate" "low" "very-low"})

(defn- load-lex [name] (json/parse-string (slurp (java.io.File. lexdir name))))
(defn- lex-files [] (filter #(.endsWith (.getName ^java.io.File %) ".json") (seq (.listFiles lexdir))))

;; Collect every value stored under `key` anywhere in the lexicon tree.
(defn- collect [doc key]
  (let [acc (atom [])]
    (letfn [(walk [x] (cond (map? x) (do (when (contains? x key) (swap! acc conj (get x key))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (doseq [req (collect doc "required")]
      (when (sequential? req) (swap! acc into req)))
    @acc))

(defn- property-keys [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (map? (get x "properties")) (swap! acc into (keys (get x "properties")))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── G1 source-whitelist + provenance ──
(deftest test-g1-evidence-requires-source-and-provenance
  (let [req (required-union (load-lex "evidenceRecord.json"))]
    (doseq [field ["sourceClass" "provenanceId" "provenanceIdKind"]]
      (is (contains? req field) (str "G1: evidenceRecord must require " field)))))

(deftest test-g1-provenance-id-kinds-are-whitelisted
  (let [kinds (atom #{})]
    (doseq [kv (collect (load-lex "evidenceRecord.json") "knownValues")]
      (when (and (sequential? kv) (seq (set/intersection (set kv) PROVENANCE-WHITELIST)))
        (swap! kinds into kv)))
    (is (seq @kinds) "G1: evidenceRecord must enumerate provenanceIdKind")
    (is (set/subset? @kinds PROVENANCE-WHITELIST)
        (str "G1: provenance kinds escaped the whitelist: " (set/difference @kinds PROVENANCE-WHITELIST)))))

(deftest test-g1-source-whitelist-requires-grade-ceiling
  (let [req (required-union (load-lex "sourceWhitelist.json"))]
    (doseq [field ["maxDefaultGrade" "provenanceIdKind"]]
      (is (contains? req field) (str "G1: each whitelisted sourceClass must declare " field)))))

;; ── G2 evidence-grade mandatory ──
(deftest test-g2-evidence-record-requires-grade-and-studytype
  (let [req (required-union (load-lex "evidenceRecord.json"))]
    (is (and (contains? req "evidenceGrade") (contains? req "studyType")))))

(deftest test-g2-grade-vocabulary-is-grade-shaped
  (let [grades (atom #{})]
    (doseq [kv (collect (load-lex "evidenceRecord.json") "knownValues")]
      (when (and (sequential? kv) (seq (set/intersection (set kv) GRADE-VALUES)))
        (swap! grades into kv)))
    (is (set/subset? GRADE-VALUES @grades)
        (str "G2: evidenceGrade must cover GRADE levels " GRADE-VALUES ", got " @grades))))

(deftest test-g2-synthesis-requires-overall-grade
  (is (contains? (required-union (load-lex "treatmentSynthesis.json")) "overallEvidenceGrade")))

;; ── G3 mandatory disclaimer ──
(deftest test-g3-patient-facing-outputs-require-disclaimer
  (doseq [name ["conditionProfile.json" "referralPathway.json" "treatmentSynthesis.json"]]
    (is (contains? (required-union (load-lex name)) "disclaimerTextUri")
        (str "G3: " name " must require disclaimerTextUri"))))

;; ── G4 referral-not-treatment ──
(deftest test-g4-referral-lists-facilities-only
  (is (contains? (required-union (load-lex "referralPathway.json")) "recommendedFacilityKinds")))

(deftest test-g4-no-booking-purchase-or-diagnosis-field
  (let [forbidden ["booking" "reservation" "appointment" "purchase" "diagnosis" "prescription" "devicesale"]]
    (doseq [f (lex-files)]
      (let [keys (set (map str/lower-case (property-keys (json/parse-string (slurp f)))))]
        (doseq [word forbidden]
          (is (not (contains? keys word))
              (str "G4: " (.getName ^java.io.File f) " must not declare a '" word "' field")))))))
