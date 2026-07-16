(ns danjo.methods.test-charter-gates
  "danjo — constitutional-gate conformance tests (manifest + central lexicons).
  Substrate-native Clojure (ADR-2606160842). 1:1 port of the pruned methods/test_charter_gates.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
(def ^:private actor-dir (.getParentFile here))                          ;; danjo/
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
(defn- lex-files [] (filter #(.endsWith (.getName ^java.io.File %) ".json") (seq (.listFiles lexdir))))

(def ^:private named-basis
  #{"procurement-awardee" "diet-member-on-record" "budget-recipient" "contracting-authority"})

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
(defn- property-keys [doc]
  (let [acc (atom #{})]
    (letfn [(walk [x] (cond (map? x) (do (when (map? (get x "properties")) (swap! acc into (keys (get x "properties"))))
                                         (doseq [v (vals x)] (walk v)))
                            (sequential? x) (doseq [v x] (walk v))))]
      (walk doc)) @acc))

;; ── full gate set ──
(deftest test-all-13-gates-declared
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"])))
         (set (map #(str "G" %) (range 1 14))))))

;; ── G4 — non-adjudicating: notice const true on observation + report ──
(deftest test-g4-non-adjudicating-notice
  (is (= true (a-const (lex "discrepancyObservation") "nonAdjudicatingNotice")))
  (is (= true (a-const (lex "oversightReport") "nonAdjudicatingNotice"))))

(deftest test-g4-no-verdict-field
  (let [forbidden ["verdict" "accusation" "guilt" "ruling" "conviction" "truthrating"]]
    (doseq [f (lex-files)]
      (let [keys (set (map str/lower-case (property-keys (json/parse-string (slurp f)))))]
        (doseq [word forbidden]
          (is (not (contains? keys word))))))))

;; ── G5 — source provenance: ≥2 cited public records + method note ──
(deftest test-g5-source-provenance
  (let [req (required-union (lex "discrepancyObservation"))]
    (doseq [field ["sourceRecordCids" "methodNoteCid"]]
      (is (contains? req field))))
  (is (contains? (required-union (lex "crossReferenceLink")) "basisRecordCids")))

;; ── G6 — open method: reproducible method note ──
(deftest test-g6-open-method
  (let [mn (required-union (lex "methodNote"))]
    (doseq [field ["definition" "inputs" "version" "methodId"]]
      (is (contains? mn field)))))

;; ── G11 — publicly-named-basis only (names only public-record actors) ──
(deftest test-g11-publicly-named-basis
  (let [doc (lex "discrepancyObservation")]
    (is (contains? (required-union doc) "publiclyNamedBasis"))
    (is (= named-basis (known doc "publiclyNamedBasis")))))

;; ── 1-SBT-1-vote governance on an oversight report ──
(deftest test-governance-one-sbt-one-vote
  (let [req (required-union (lex "oversightReport"))]
    (doseq [field ["councilAttestations" "councilReviewCid" "oneSbtOneVoteChainCid"]]
      (is (contains? req field)))))
