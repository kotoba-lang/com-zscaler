(ns makura.methods.test-charter-gates
  "makura — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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

(defn- collect [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x attr)) (swap! acc assoc parent (get x attr)))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))

(defn- known [doc field] (some-> (get (collect doc "knownValues") field) set))

(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required"))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set + non-goals ──
(deftest test-all-14-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 15)))))))

;; ── G14 — no embedded electronics (anti-surveillance consumer good) ──
(deftest test-g14-no-embedded-electronics
  (let [doc (lex "pillowLotAttestation")]
    (is (contains? (required-union doc) "g14NoEmbeddedElectronics"))
    (is (= #{"none"} (known doc "embeddedElectronics")))))

;; ── G11/G12/G13 — KPI cap + full BoM + take-back QR on every pillow ──
(deftest test-g11-g12-g13-pillow-invariants
  (let [req (required-union (lex "pillowLotAttestation"))]
    (doseq [field ["withinG11Cap" "g12FullBomDisclosed" "g13TakeBackQrPresent" "bom" "pillowDid"]]
      (is (contains? req field)))))

;; ── G6 — isocyanate worker-exposure record ──
(deftest test-g6-worker-exposure-record
  (let [doc (lex "workerExposureRecord")
        req (required-union doc)
        agents (known doc "agent")]
    (is (and (contains? req "g6Compliant") (contains? req "agent")))
    (is (and (contains? agents "MDI") (contains? agents "TDI")))))

;; ── G8 — flame-retardant disclosed on every lot (FR-free baseline) ──
(deftest test-g8-flame-retardant-recorded
  (is (contains? (required-union (lex "pillowLotAttestation")) "fireRetardant")))

;; ── G3/G4 — witness quorum + bilingual label ──
(deftest test-g3-g4-witness-and-bilingual
  (doseq [name ["pillowLotAttestation" "foamBatchAttestation" "qcRecord" "fabricAttestation"]]
    (is (contains? (required-union (lex name)) "attestingRobots")))
  (is (contains? (required-union (lex "pillowLotAttestation")) "g4BilingualMinimumMet")))

;; ── charter scan at the fabric door (§2 a–e) ──
(deftest test-fabric-charter-scan
  (let [doc (lex "fabricAttestation")]
    (is (contains? (required-union doc) "charterScan"))
    (is (= #{"clear" "warn" "violation"} (known doc "section2cSurveillance")))))

;; ── G13 — take-back recycling loop (cross-actor with hodoki seat foam) ──
(deftest test-g13-recycling-certificate-loop
  (let [req (required-union (lex "recyclingCertificate"))]
    (doseq [field ["recycledBlend" "chainEntryCid" "intakeCenterDid" "returnedPillowDid"]]
      (is (contains? req field)))))
