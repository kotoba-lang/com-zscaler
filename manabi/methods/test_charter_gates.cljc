(ns manabi.methods.test-charter-gates
  "manabi — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(defn- manifest [] (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn"))))
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
(deftest test-all-14-gates-declared
  (let [gates (set (map :gate/id (:actor/gates (manifest))))]
    (is (= gates (set (map #(str "G" %) (range 1 15)))))))

;; ── G7 — anti-credentialism: no degree/credential claimed ──
(deftest test-g7-anti-credentialism
  (is (= false (a-const (lex "domainMasteryAttestation") "credentialClaimedAttested"))))

;; ── G6 — minor privacy discriminator on session records ──
(deftest test-g6-minor-age-bucket
  (doseq [name ["learningSessionAttestation" "certPrepSession"]]
    (is (= #{"minor" "adult"} (known (lex name) "learnerAgeBucket")))))

;; ── G4 — curriculum review: Charter alignment + Rider scan + open license ──
(deftest test-g4-curriculum-review
  (let [doc (lex "curriculumAttestation")
        req (required-union doc)]
    (doseq [field ["charterAlignmentAttestations" "charterRiderScanResult" "licenseDeclaration"]]
      (is (contains? req field)))
    (is (= #{"Apache-2.0" "CC-BY-SA-4.0"} (known doc "licenseDeclaration")))
    (is (= #{"pass" "fail" "warn"} (known doc "charterRiderScanResult")))))

;; ── G3 — anti-addiction UX attestation on every module ──
(deftest test-g3-anti-addiction-ux
  (let [req (required-union (lex "curriculumAttestation"))]
    (doseq [field ["noAddictionUxAttestationCid" "inquiryFractionPct"]]
      (is (contains? req field)))))

;; ── personal material import is internal-only + encrypted + licensed (no piracy) ──
(deftest test-personal-import-internal-encrypted-licensed
  (let [doc (lex "personalMaterialImport")
        req (required-union doc)]
    (is (= true (a-const doc "internalOnly")))
    (doseq [field ["encryptedPayloadCid" "licenseAcknowledgment"]]
      (is (contains? req field)))
    (is (= #{"owner-purchased-individual-use" "owner-authored" "public-domain"}
           (known doc "licenseAcknowledgment")))))
