(ns kanae.methods.test-charter-gates
  "kanae — constitutional-gate conformance tests. Substrate-native Clojure (ADR-2606160842); 1:1 port of pruned test_charter_gates.py."
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
     "status" (some-> (:actor/status e) name)}))
(defn- lex [name] (json/parse-string (slurp (java.io.File. lexdir (str name ".json")))))

(def ^:private flow-classes
  #{"appropriation" "outlay" "subaward" "procurement-award"
    "intergovernmental-transfer" "aid-disbursement" "loan" "repayment"})
(def ^:private render-types
  #{"sankey-fund-flow" "recipient-treemap" "intergov-transfer-globe" "appropriation-outlay-timeline"})

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

(defn- property-keys [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (map? (get x "properties")) (swap! acc into (keys (get x "properties")))) (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set (kanae carries G1–G15) ──
(deftest test-all-15-gates-declared
  (let [gates (get-in (manifest) ["constitutionalGates" "gates"])]
    (is (= (set (keys gates)) (set (map #(str "G" %) (range 1 16))))
        "manifest must declare G1–G15")))

;; ── G1 non-adjudication — narrative carries the notice; no verdict field anywhere ──
(deftest test-g1-non-adjudicating-notice
  (is (contains? (required-union (lex "flowNarrative")) "nonAdjudicatingNotice")
      "G1: flowNarrative must require nonAdjudicatingNotice"))

(deftest test-g1-no-verdict-field
  (doseq [name ["fundFlowEdge" "flowNarrative" "visualizationManifest"]]
    (let [keys (set (map str/lower-case (property-keys (lex name))))]
      (doseq [bad ["verdict" "truthrating" "score" "ranking"]]
        (is (not (contains? keys bad))
            (str "G1: " name " must not carry a '" bad "' field (kanae renders no verdict)"))))))

;; ── G6 Murakumo-only narration ──
(deftest test-g6-murakumo-only-narration
  (let [req (required-union (lex "flowNarrative"))]
    (doseq [field ["inferenceSubstrate" "model" "murakumoInferenceAttestation"]]
      (is (contains? req field) (str "G6: flowNarrative must require " field)))))

;; ── aggregate-first + publicly-named-basis only ──
(deftest test-aggregate-first-and-public-basis
  (let [req (required-union (lex "visualizationManifest"))]
    (is (contains? req "aggregateOnly") "visualizationManifest must require aggregateOnly (no individual-level render)")
    (is (contains? req "publiclyNamedBasis") "visualizationManifest must require publiclyNamedBasis (names no one beyond the source record)")))

;; ── method + provenance transparency (reads sources, never invents) ──
(deftest test-method-and-provenance-transparency
  (doseq [name ["fundFlowEdge" "flowNarrative"]]
    (let [req (required-union (lex name))]
      (is (and (contains? req "methodNoteCid") (contains? req "sourceRecordCids"))
          (str name " must require methodNoteCid + sourceRecordCids"))))
  (let [mn (required-union (lex "methodNote"))]
    (doseq [field ["definition" "inputs" "version" "methodId"]]
      (is (contains? mn field) (str "methodNote must require " field)))))

;; ── 1-SBT-1-vote governance on a published visualization ──
(deftest test-governance-one-sbt-one-vote
  (let [req (required-union (lex "visualizationManifest"))]
    (doseq [field ["oneSbtOneVoteChainCid" "councilReviewCid"]]
      (is (contains? req field) (str "governance: visualizationManifest must require " field)))))

;; ── bounded flow + render vocabularies ──
(deftest test-bounded-flow-and-render-types
  (is (= (known (lex "fundFlowEdge") "flowClass") flow-classes) "flowClass vocabulary drifted")
  (is (= (known (lex "visualizationManifest") "renderType") render-types) "renderType vocabulary drifted"))
