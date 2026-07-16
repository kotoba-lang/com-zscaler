(ns kizashi.methods.test-charter-gates
  "kizashi — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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
(defn- lex-files [] (filter #(.endsWith (.getName ^java.io.File %) ".json") (seq (.listFiles lexdir))))

(defn- consts [doc]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x "const"))
                                   (swap! acc assoc parent (get x "const")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

(defn- known [doc field]
  (let [acc (atom #{})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (= parent field) (contains? x "knownValues"))
                                   (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- property-keys [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (map? (get x "properties")) (swap! acc into (keys (get x "properties")))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-14-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 15))))
        "manifest must declare G1–G14")))

;; ── G3 — non-diagnostic: mandatory disclaimer + consult recommendation ──
(deftest test-g3-non-diagnostic-disclaimer
  (let [doc (lex "attributionReport")
        disc (get (consts doc) "disclaimer" "")]
    (is (and (str/includes? disc "確定原因ではありません") (str/includes? disc "受診"))
        "G3: attributionReport must carry the non-diagnostic disclaimer const")
    (is (contains? (required-union doc) "consultRecommendation") "G3: attributionReport must require a consultRecommendation")))

(deftest test-g3-no-diagnosis-or-prescription-field
  (let [forbidden ["diagnosis" "prescription" "icd10" "icd11" "treatment" "medication"]]
    (doseq [f (lex-files)]
      (let [keys (set (map str/lower-case (property-keys (json/parse-string (slurp f)))))]
        (doseq [word forbidden]
          (is (not (contains? keys word))
              (str "G3: " (.getName ^java.io.File f) " must not declare a '" word "' field (kizashi senses, never diagnoses)")))))))

;; ── G10 — verified-modality-only / anti-pseudoscience ──
(deftest test-g10-anti-pseudoscience
  (let [doc (lex "modalityCapability")
        req (required-union doc)
        grades (known doc "evidenceGrade")]
    (doseq [field ["canDetect" "cannotDetect" "councilAttestationCid" "evidenceGrade" "regulatoryClass"]]
      (is (contains? req field) (str "G10: modalityCapability must require " field)))
    (is (and (contains? grades "X-excluded-pseudoscience") (contains? grades "A-validated-clinical"))
        "G10: evidenceGrade must grade modalities (incl. an explicit pseudoscience-excluded tier)")))

;; ── G4 — medical-device regulatory class declared ──
(deftest test-g4-regulatory-class
  (let [rc (known (lex "modalityCapability") "regulatoryClass")]
    (is (= rc #{"non-regulated-wellness" "samd-software" "regulated-medical-device" "ionizing-licensed-facility-only"})
        (str "G4: regulatoryClass taxonomy drifted, got " rc))))

;; ── G2 — encrypted biometric envelope + consent ──
(deftest test-g2-encrypted-and-consent
  (let [sess (required-union (lex "scanSessionAttestation"))]
    (is (and (contains? sess "encryptedPayloadCid") (contains? sess "scanConsentCid")) "G2: scan session must be encrypted + consented")
    (is (contains? (required-union (lex "modalityObservation")) "encryptedPayloadCid") "G2: observation must be encrypted")))

;; ── referrals route to the clinical actors (sensing is upstream) ──
(deftest test-referral-to-clinical-actors
  (is (= (known (lex "triageReferral") "targetActor") #{"mitate" "iyashi" "kokoro"})
      "referral must route to clinical actors (mitate/iyashi/kokoro), not self-adjudicate"))
