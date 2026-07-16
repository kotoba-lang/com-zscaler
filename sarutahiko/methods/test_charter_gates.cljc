(ns sarutahiko.methods.test-charter-gates
  "sarutahiko — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(def ^:private POWERTRAINS
  #{"B100-biodiesel-hybrid" "diesel-hybrid" "LFP-battery"
    "H2-fuel-cell" "NH3-fuel-cell" "methanol-fuel-cell"})

(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
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
(deftest test-all-14-gates-declared
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))
         (set (map #(str "G" %) (range 1 15))))))

;; ── G1/N8 open-source firmware, no proprietary NDA ──
(deftest test-g1-n8-open-source-firmware
  (let [c (consts (lex "electricalAttestation.json"))]
    (is (= (get c "g1Enforcement") "active"))
    (is (= (get c "n8Enforcement") "active"))
    (is (= (get c "proprietaryNdaPresent") false))
    (is (contains? (required-union (lex "electricalAttestation.json")) "openSourceVerification"))))

;; ── G7 fuel transition: no pure-fossil R2+ powertrain ──
(deftest test-g7-fuel-transition
  (let [doc (lex "powertrainAttestation.json")]
    (is (= (get (consts doc) "g7Enforcement") "active"))
    (is (= (known doc "powerTrainType") POWERTRAINS))
    (is (contains? (required-union doc) "fuelGuard"))))

;; ── G8 emissions basis + paint VOC ≤100 g/L ──
(deftest test-g8-emissions-and-voc
  (let [basis0 (known (lex "emissionsAuditRecord.json") "regulatoryBasis")
        basis (if (seq basis0)
                basis0
                (let [acc (atom #{})]
                  (letfn [(w [x] (cond (map? x) (do (when (sequential? (get x "knownValues")) (swap! acc into (get x "knownValues"))) (doseq [v (vals x)] (w v)))
                                       (sequential? x) (doseq [v x] (w v))))]
                    (w (lex "emissionsAuditRecord.json"))) @acc))]
    (is (some #(clojure.string/includes? % "Euro 7") basis))
    (is (= (get (consts (lex "paintAttestation.json")) "vocLimitGPerL") 100))))

;; ── G12 max road speed ≤90 km/h ──
(deftest test-g12-speed-cap
  (is (= (get (consts (lex "roadTestRecord.json")) "maxSpeedLimitKmh") 90))
  (is (contains? (required-union (lex "roadTestRecord.json")) "g12KpiCheck")))

;; ── frame: straightness ≤1.0 mm/m + HSLA grade only ──
(deftest test-frame-straightness-and-grade
  (let [doc (lex "frameAttestation.json")]
    (is (= (get (consts doc) "specStraightnessLimitMmPerM") 1.0))
    (is (= (known doc "grade") #{"HSLA-590" "HSLA-780" "HSLA-980"}))))

;; ── G4 witness quorum on frame + final marriage ──
(deftest test-g4-witness-quorum
  (doseq [name ["frameAttestation.json" "marriageAttestation.json"]]
    (is (contains? (required-union (lex name)) "attestingRobots") (str "G4: " name " must require attestingRobots"))))
