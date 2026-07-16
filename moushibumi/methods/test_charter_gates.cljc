(ns moushibumi.methods.test-charter-gates
  "moushibumi — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private actor-name (.getName actor-dir))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root (str "00-contracts/lexicons/com/etzhayyim/" actor-name)))

(def ^:private channel-kinds #{"petition" "public-comment" "election-info"})

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

;; ── drafting-assist only, member-confirmed, encrypted ──
(deftest test-drafting-assist-only
  (let [doc (lex "voiceDraft")
        req (required-union doc)]
    (is (= #{"drafting-assist"} (known doc "assistMode")))
    (doseq [field ["memberConfirmed" "encryptedDraftRef"]]
      (is (contains? req field)))))

;; ── self-submit default + consent-bound submission ──
(deftest test-self-submit-default-consent
  (let [doc (lex "submissionRecord")]
    (is (contains? (required-union doc) "consentRef"))
    (is (= #{"member-self-submit" "agent-on-behalf"} (known doc "mode")))))

;; ── G14 — target must cite a verified legal basis ──
(deftest test-g14-target-legal-basis-verified
  (let [doc (lex "participationTarget")
        req (required-union doc)]
    (doseq [field ["legalBasis" "provenance" "verificationStatus" "organ"]]
      (is (contains? req field)))
    (is (= #{"unverified-seed" "maintainer-verified" "council-verified"} (known doc "verificationStatus")))))

;; ── G3 — channel kinds are procedure-only ──
(deftest test-g3-channel-kinds
  (is (= channel-kinds (known (lex "participationTarget") "channelKind"))))

;; ── G4 — own-voice: every working record is member-bound ──
(deftest test-member-bound-records
  (doseq [name ["participationSession" "participationMatch" "voiceDraft"
                "submissionRecord" "statusTrack"]]
    (is (contains? (required-union (lex name)) "memberDid"))))
