(ns himawari.methods.test-charter-gates
  "himawari — constitutional-gate conformance tests (manifest + central lexicons).
  Substrate-native Clojure (ADR-2606160842). 1:1 port of the pruned methods/test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
(def ^:private actor-dir (.getParentFile here))                          ;; himawari/
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))          ;; 20-actors → ROOT
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))
(defn- manifest [] (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(defn- collect [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x attr))
                                   (swap! acc assoc parent (get x attr)))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))
(defn- a-const [doc field] (get (collect doc "const") field))
(defn- known [doc field] (some-> (get (collect doc "knownValues") field) set))
(defn- required-union [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (sequential? (get x "required")) (swap! acc into (get x "required")))
                                         (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-14-gates-declared
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))
         (set (map #(str "G" %) (range 1 15))))))

;; ── G2 — feedstock provenance: region + chain-of-custody + audit (no forced-labor polysilicon) ──
(deftest test-g2-feedstock-provenance
  (let [doc (lex "polysiliconProvenanceAttestation")
        req (required-union doc)]
    (doseq [field ["regionCode" "originRegionAttestationCid" "chainOfCustody" "sourcingAuditCid"]]
      (is (contains? req field)))
    (is (= #{"solar-grade-6N" "solar-grade-6N+" "recycled-kerf"} (known doc "feedstockGrade")))))

;; ── G3 — F-gas process-chemistry abatement on every cell batch ──
(deftest test-g3-fgas-abatement
  (let [req (required-union (lex "cellBatchRecord"))]
    (doseq [field ["gasAbatementCid" "minDreFloor" "meetsG3Floor" "uncontrolledVenting"]]
      (is (contains? req field)))))

;; ── G12 — no external sale: outbound destination is the hikari install site only ──
(deftest test-g12-no-external-sale
  (is (= "hikari-install-site" (a-const (lex "outboundManifest") "destinationKind"))))

;; ── anti-weaponization + no-server-key declared on outbound/module ──
(deftest test-anti-weaponization-and-no-server-key
  (is (contains? (required-union (lex "outboundManifest")) "weaponizationPayload"))
  (is (contains? (required-union (lex "moduleAttestation")) "serverHeldKey")))

;; ── G11 — deterministic traceability: signed provenance digest on module ──
(deftest test-g11-traceability-signed
  (let [doc (lex "moduleAttestation")
        req (required-union doc)]
    (doseq [field ["provenanceChainDigest" "signedDigest" "signer"]]
      (is (contains? req field)))
    (is (= #{"content-binding-sha256" "ed25519"} (known doc "alg")))))

;; ── G4 — witness-signed attestations across the line ──
(deftest test-g4-witness-signed
  (doseq [name ["cellBatchRecord" "moduleAttestation" "waferBatchRecord"]]
    (let [req (required-union (lex name))]
      (is (and (contains? req "attestingRobots") (contains? req "signature"))))))
