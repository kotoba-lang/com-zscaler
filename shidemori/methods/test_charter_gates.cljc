(ns shidemori.methods.test-charter-gates
  "shidemori — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
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
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir name))))

(defn- consts [doc]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x "const")) (swap! acc assoc parent (get x "const")))
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
              (cond (map? x) (do (when (and (= parent field) (contains? x "knownValues")) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── full gate set ──
(deftest test-all-13-gates-declared
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))
         (set (map #(str "G" %) (range 1 14))))))

;; ── G3 — non-eschatological: no afterlife doctrine imposed ──
(deftest test-g3-no-afterlife-doctrine-imposed
  (is (= (get (consts (lex "memorialNftAttestation.json")) "afterlifeDoctrineImposed") false)))

;; ── G7 — no embalming chemicals ──
(deftest test-g7-no-embalming
  (is (= (get (consts (lex "externalMortuaryEngagement.json")) "embalmingChemicalsUsed") false)))

;; ── G10 — waqf-inalienable cemetery + biodegradable green burial ──
(deftest test-g10-waqf-green-burial
  (let [c (consts (lex "cemeteryLandAttestation.json"))]
    (is (= (get c "waqfInalienabilityAttested") true))
    (is (= (get c "biodegradableShroudPineCasketOnlyAttested") true))))

;; ── the quarterly-review dignity const ledger ──
(deftest test-silen-review-dignity-ledger
  (let [c (consts (lex "silenShidemoriReview.json"))
        expected {"eschatologicalContentEventsCount" 0
                  "commercialMemorialSoftwarePenetrationPct" 0
                  "embalmingChemicalUsageEventsCount" 0
                  "mortuarySurveillanceEventsCount" 0
                  "stateLicensedMortuaryFirstPartyPct" 0
                  "mandatoryBurialEventsCount" 0
                  "singleDoctrinalAfterlifeMonopolyEventsCount" 0
                  "commercialMemorialAiUsageCount" 0
                  "cemeteryLandWaqfInalienabilityCompliantRatioPctIntegerHundredths" 10000
                  "guardianVocationFlowCompliantRatioPctIntegerHundredths" 10000}]
    (doseq [[field want] expected]
      (is (= (get c field) want) (str "silenShidemoriReview." field " must be const " want ", got " (get c field))))))

;; ── G9 — free conscience ──
(deftest test-g9-free-conscience
  (let [doc (lex "memorialNftAttestation.json")]
    (is (contains? (required-union doc) "memberDirectiveCid"))
    (is (contains? (known doc "doctrinalAccommodation") "member-directive-respecting-no-doctrine-imposed"))))

;; ── cross-doctrinal remembrance is open to the community ──
(deftest test-cross-doctrinal-open
  (is (= (get (consts (lex "chinkonRemembranceAttestation.json")) "openToCommunityAttested") true)))
