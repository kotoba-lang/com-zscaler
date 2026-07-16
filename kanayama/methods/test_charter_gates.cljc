(ns kanayama.methods.test-charter-gates
  "kanayama — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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

(defn- all-known-values [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "knownValues")) (swap! acc into (get x "knownValues"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full constitutional gate set is declared ──
(deftest test-all-14-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 15))))
        "manifest must declare G1–G14")))

;; ── recycling-only: N1–N4 exclude primary mining / smelting / munitions / nuclear ──
(deftest test-recycling-only-nongoals
  (let [n (get-in (manifest) ["nonGoals" "goals"])]
    (doseq [key ["N1" "N2" "N3" "N4"]]
      (is (contains? n key) (str "recycling-only: non-goal " key " must be declared")))
    (is (or (str/includes? (str/lower-case (get n "N1")) "primary mining")
            (str/includes? (str/lower-case (get n "N1")) "bauxite")) "N1 must exclude primary mining")
    (is (or (str/includes? (str/lower-case (get n "N2")) "hall-héroult")
            (str/includes? (str/lower-case (get n "N2")) "primary")) "N2 must exclude primary smelting")
    (is (or (str/includes? (str/lower-case (get n "N3")) "munition")
            (str/includes? (str/lower-case (get n "N3")) "cartridge")) "N3 must exclude munitions/cartridge brass")
    (is (or (str/includes? (str/lower-case (get n "N4")) "nuclear")
            (str/includes? (str/lower-case (get n "N4")) "radio")) "N4 must exclude nuclear/radiological feedstock")))

;; ── G2/G12/G13 quantitative discipline is stated ──
(deftest test-mass-balance-recovery-energy-gates
  (let [g (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (or (str/includes? (get g "G2") "98%") (str/includes? (get g "G2") "≥98")) "G2 must state ≥98% mass-balance closure")
    (is (or (str/includes? (get g "G12") "95%") (str/includes? (get g "G12") "≥95")) "G12 must state ≥95% recovery rate")
    (is (or (str/includes? (str/lower-case (get g "G13")) "captive coal")
            (str/includes? (str/lower-case (get g "G13")) "petroleum coke")
            (str/includes? (str/lower-case (get g "G13")) "petcoke")) "G13 must prohibit captive coal / petcoke")))

;; ── G8 emissions / leachate regulatory basis enumerated ──
(deftest test-g8-emissions-regulatory-basis
  (let [doc (lex "airEmissionsAuditRecord")
        basis (let [b (known doc "regulatoryBasis")]
                (if (seq b) b (all-known-values doc)))]
    (is (some #(str/includes? % "IED") basis) "G8: must cite EU IED 2010/75/EU")
    (is (some #(str/includes? % "12457") basis) "G8: must cite EN 12457 leachate")))

;; ── G4 witness quorum: melt pour records attesting robots ──
(deftest test-g4-melt-requires-attesting-robots
  (let [req (required-union (lex "meltingAttestation"))]
    (is (contains? req "attestingRobots") "G4: meltingAttestation must require attestingRobots (witness quorum)")
    (is (contains? req "alloyComposition") "meltingAttestation must record alloyComposition")))
