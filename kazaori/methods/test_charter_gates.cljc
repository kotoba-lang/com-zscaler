(ns kazaori.methods.test-charter-gates
  "kazaori — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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

;; ── full gate set ──
(deftest test-all-12-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 13))))
        "manifest must declare G1–G12")))

;; ── G10/G5 — Council-declared, civilian-only, auto-lifting emergency ──
(deftest test-g10-emergency-declaration
  (let [doc (lex "emergencyDeclarationAttestation")
        req (required-union doc)]
    (is (= true (get (consts doc) "civilianOnlyAttested")) "G5: declaration must attest civilian-only")
    (doseq [field ["councilAttestations" "autoLiftAtUtc" "initialDurationDays"]]
      (is (contains? req field) (str "G10/G8: emergency declaration must require " field)))))

;; ── G6/G4 — the quarterly review const ledger (no surveillance / no commercial software) ──
(deftest test-silen-review-const-ledger
  (let [c (consts (lex "silenKazaoriReview"))]
    (is (= 0 (get c "surveillancePenetrationPct")) "G6: surveillance penetration must be const 0")
    (is (= 0 (get c "commercialDisasterMgmtSoftwarePenetrationPct")) "G4: no commercial disaster-mgmt software")
    (is (= true (get c "civilianOnlyCompliance")) "G5: civilian-only compliance const true")
    (is (= true (get c "reviewCompletedWithin90DaysOfLifting")) "review must complete within 90 days of lifting")))

;; ── G9 — Sphere-standard 5-sector compliance audited per emergency ──
(deftest test-g9-sphere-five-sectors
  (let [req (required-union (lex "silenKazaoriReview"))]
    (doseq [field ["sphereCompliance" "waterAttested" "foodAttested" "healthAttested"
                   "shelterAttested" "protectionAttested"]]
      (is (contains? req field) (str "G9: silenKazaoriReview must require " field)))))

;; ── G8 — time-bounded carve-outs (auto-revoke + Council justification) ──
(deftest test-g8-time-bounded-carveout
  (let [req (required-union (lex "emergencyCarveOutLog"))]
    (doseq [field ["autoRevokeAtUtc" "councilAttestations" "carveOutJustificationCid" "gateCarved"]]
      (is (contains? req field) (str "G8: emergencyCarveOutLog must require " field)))))

;; ── supply dispatch is carve-out-gated + sourced ──
(deftest test-supply-dispatch-carveout-gated
  (let [req (required-union (lex "emergencySupplyDispatch"))]
    (doseq [field ["emergencyCarveOutLogCid" "gateCarved" "supplySource"]]
      (is (contains? req field) (str "emergencySupplyDispatch must require " field)))))

;; ── G6 — evacuation check-in is encrypted + member-signed ──
(deftest test-g6-evacuation-encrypted-signed
  (let [req (required-union (lex "evacuationCheckIn"))]
    (doseq [field ["encryptedPayloadCid" "memberSignature"]]
      (is (contains? req field) (str "G6: evacuationCheckIn must require " field)))))
