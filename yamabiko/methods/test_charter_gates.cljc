(ns yamabiko.methods.test-charter-gates
  "yamabiko 山彦 — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(def ^:private PROPULSION
  #{"overhead-25kV-AC" "overhead-1500V-DC" "third-rail-750V-DC"
    "BEMU-LFP" "H2-fuel-cell-hybrid" "NH3-fuel-cell-hybrid"})

(defn- consts [doc]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (contains? x "const") (string? parent)) (swap! acc assoc parent (get x "const")))
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
              (cond (map? x) (do (when (and (contains? x "knownValues") (= parent field)) (swap! acc into (get x "knownValues")))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

;; ── full gate set ──
(deftest test-all-14-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 15)))) "manifest must declare G1–G14")))

;; ── G1/N5 open firmware, no proprietary signalling NDA ──
(deftest test-g1-n5-open-firmware
  (let [c (consts (lex "tractionElectricalAttestation"))]
    (is (and (= (get c "g1Enforcement") "active") (= (get c "n5Enforcement") "active")) "G1/N5 enforcement must be active")
    (is (= (get c "g7Enforcement") "active") "G7 enforcement must be active")
    (is (= (get c "proprietaryNdaPresent") false) "N5: no proprietary signalling NDA")
    (is (contains? (required-union (lex "tractionElectricalAttestation")) "openSourceVerification"))))

;; ── G7 propulsion (no diesel R2+) + autonomy ≤ GoA-3 (N7 = no GoA-4) ──
(deftest test-g7-propulsion-and-goa-cap
  (let [doc (lex "tractionElectricalAttestation")]
    (is (= (known doc "propulsionType") PROPULSION) "propulsionType vocabulary drifted (no diesel)")
    (let [ato (known doc "atoLevel")]
      (is (= ato #{"GoA-1" "GoA-2" "GoA-3"}) "N7: autonomy must cap at GoA-3 (no GoA-4)"))))

;; ── N6 no advertising + N8 no face recognition in the interior ──
(deftest test-n6-no-ads-n8-no-face-recognition
  (let [c (consts (lex "interiorAttestation"))]
    (is (= (get c "n6AdvertisingPresent") false) "N6: no third-party advertising in interior")
    (is (= (get c "n8FaceRecognitionPresent") false) "N8: no face recognition / mass-surveillance")
    (is (= (get c "g5Trilingual") true) "G5: trilingual PIS")
    (is (= (get (consts (lex "finalAssemblyAttestation")) "n6AdvertisingFreeAccept") true) "N6: final assembly advertising-free")))

;; ── G12 speed cap ≤320 km/h ──
(deftest test-g12-speed-cap
  (is (= (get (consts (lex "dynamicTestRecord")) "maxSpeedLimitKmh") 320) "G12: max speed must be const 320 km/h")
  (is (contains? (required-union (lex "dynamicTestRecord")) "g12KpiCheck")))

;; ── G2 homologation: open registry + kotoba anchor ──
(deftest test-g2-homologation-open-registry
  (let [doc (lex "homologationRecord")
        c (consts doc)]
    (is (and (= (get c "openTrainsetRegistry") true) (= (get c "g2Compliant") true)) "G2: open trainset registry + compliant")
    (let [req (required-union doc)]
      (doseq [field ["kotoba-datomicAnchor" "trainsetDid" "authorityReview"]]
        (is (contains? req field) (str "homologation must require " field))))))

;; ── G4 witness quorum on carbody / bogie / final assembly ──
(deftest test-g4-witness-quorum
  (doseq [name ["carbodyAttestation" "bogieAttestation" "finalAssemblyAttestation"]]
    (is (contains? (required-union (lex name)) "attestingRobots") (str "G4: " name " must require attestingRobots"))))
