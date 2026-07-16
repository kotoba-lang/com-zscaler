(ns himotoki.methods.test-charter-gates
  "himotoki — constitutional-gate conformance tests (manifest + central lexicons).
  Substrate-native Clojure (ADR-2606160842). 1:1 port of the pruned methods/test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
(def ^:private actor-dir (.getParentFile here))                          ;; himotoki/
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))          ;; 20-actors → ROOT
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

(defn- collect [doc attr]
  (let [acc (atom {})]
    (letfn [(walk [x parent]
              (cond (map? x) (do (when (and (string? parent) (contains? x attr))
                                   (swap! acc assoc parent (get x attr)))
                                 (doseq [[k v] x] (walk v k)))
                    (sequential? x) (doseq [v x] (walk v parent))))]
      (walk doc nil)) @acc))
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

;; ── G3 — request carries the true requester + purpose + scope; DSAR/FOIA only ──
(deftest test-g3-requester-purpose-scope
  (let [doc (lex "disclosureRequest")
        req (required-union doc)]
    (doseq [field ["requesterDid" "purpose" "scope"]]
      (is (contains? req field)))
    (is (= #{"dsar" "foia"} (known doc "requestKind")))))

;; ── G4 — dispatched only against a VERIFIED target (no unverified-seed flooding) ──
(deftest test-g4-verified-dispatch
  (let [doc (lex "requestDispatch")
        req (required-union doc)]
    (doseq [field ["requesterDid" "verificationStatusAtDispatch" "deadlineAt"]]
      (is (contains? req field)))
    (is (= #{"maintainer-verified" "council-verified"} (known doc "verificationStatusAtDispatch")))))

;; ── G14 — target carries a cited regime + provenance + verification status ──
(deftest test-g14-target-regime-provenance
  (let [req (required-union (lex "disclosureTarget"))]
    (doseq [field ["regime" "provenance" "verificationStatus" "jurisdiction"]]
      (is (contains? req field)))))

;; ── G5 — appeals route to chigiri + a proper review channel ──
(deftest test-g5-appeal-to-counsel
  (let [doc (lex "appealRecord")]
    (is (contains? (required-union doc) "chigiriTemplateRef"))
    (let [channels (known doc "appealChannel")]
      (doseq [c ["shinsa-seikyu-jp" "dpa-complaint-eu" "foia-appeal-us"]]
        (is (contains? channels c))))))

;; ── deadline tracking on the response ──
(deftest test-deadline-tracked
  (is (contains? (required-union (lex "disclosureResponse")) "deadlineMet")))
