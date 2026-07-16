(ns kurashimori.methods.test-charter-gates
  "kurashimori — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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
(deftest test-all-15-gates-declared
  (let [gates (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))]
    (is (= gates (set (map #(str "G" %) (range 1 16)))))))

;; ── G2/G5 — the cooling-off output is an estimate, NOT a legal opinion (UPL boundary) ──
(deftest test-cooling-off-is-not-legal-opinion
  (is (= false (a-const (lex "coolingOffAssessment") "isLegalOpinion"))))

;; ── drafting-assist only (no representation), encrypted + member-confirmed ──
(deftest test-drafting-assist-only-member-confirmed
  (let [doc (lex "remedyDraft")
        req (required-union doc)]
    (is (= #{"drafting-assist"} (known doc "assistMode")))
    (doseq [field ["memberConfirmed" "encryptedDraftRef"]]
      (is (contains? req field)))))

;; ── self-send default + consent-bound dispatch ──
(deftest test-dispatch-self-send-default-consent
  (let [doc (lex "dispatchRecord")]
    (is (contains? (required-union doc) "consentRef"))
    (is (= #{"member-self-send" "agent-on-behalf"} (known doc "mode")))))

;; ── G14 — a remedy must cite a verified legal basis ──
(deftest test-g14-remedy-legal-basis-verified
  (let [doc (lex "remedyTarget")
        req (required-union doc)]
    (doseq [field ["legalBasis" "provenance" "verificationStatus"]]
      (is (contains? req field)))
    (is (= #{"unverified-seed" "maintainer-verified" "council-verified"} (known doc "verificationStatus")))))

;; ── G5 — escalation routes to the proper bodies ──
(deftest test-g5-escalation-routes
  (let [forums (known (lex "escalationReferral") "forum")]
    (doseq [f ["shohi-seikatsu-center" "chigiri-counsel" "hotline-188"]]
      (is (contains? forums f)))))

;; ── own-matter: every working record is member-bound ──
(deftest test-member-bound-records
  (doseq [name ["complaintSession" "coolingOffAssessment" "remedyDraft"
                "dispatchRecord" "statusTrack" "escalationReferral"]]
    (is (contains? (required-union (lex name)) "memberDid"))))
