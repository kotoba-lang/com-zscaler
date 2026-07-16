#!/usr/bin/env bb
;; asobi 遊び — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/asobi/methods/test_ie_flow.cljc
(ns asobi.methods.test-ie-flow
  (:require [asobi.methods.analyze :as an]
            [asobi.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path (-> #'ief/default-seed var-get))
(defn- g [] (an/load-file* seed-path))

(deftest events-well-formed
  (let [evs (ief/flow-events-from-graph (g))]
    (is (pos? (count evs)) "one event per access-bearing cultural bearer")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "asobi is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "asobi" (:actor %)) evs))
    (is (every? #(pos? (:volume %)) evs) "every bearer row carries raw incident access (volume>0)")))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state seed-path)]
    (is (pos? (:order-index st)) "asobi RECTIFIES scattered cultural access → positive order-index (openness re-weighting concentrates)")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest openness-tracks-importance-weight
  (let [evs (ief/flow-events-from-graph (g))]
    (is (some #(pos? (:value %)) evs) "open cultural bearers export positive openness order")
    (is (<= (count (filter #(zero? (:volume %)) evs)) 0) "no zero-volume rows (dropped at source)")))

(deftest scoreboard-entry
  (let [s (score/info-control-score (ief/flow-state seed-path) {:descendant 0.85})]
    (is (not (:vetoed? s)) "asobi is charter-clean (access map, never an engagement ranking) — not vetoed")
    (is (pos? (:score s)) "asobi earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'asobi.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
