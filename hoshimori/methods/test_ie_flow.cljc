#!/usr/bin/env bb
;; hoshimori — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/hoshimori/methods/test_ie_flow.cljc
(ns hoshimori.methods.test-ie-flow
  (:require [hoshimori.methods.analyze :as an]
            [hoshimori.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path (-> #'ief/default-seed var-get))
(defn- g [] (an/load-file* seed-path))

(deftest events-well-formed
  (let [evs (ief/flow-events-from-graph (g))]
    (is (pos? (count evs)) "one event per burdened bearer")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "hoshimori is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "hoshimori" (:actor %)) evs))
    (is (every? #(pos? (:volume %)) evs) "every bearer row carries raw incident load (volume>0)")))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state seed-path)]
    (is (pos? (:order-index st)) "hoshimori RECTIFIES scattered orbital crowding→stewardship → positive order-index (re-weighting concentrates)")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest concentration-tracks-importance-weight
  (let [evs (ief/flow-events-from-graph (g))]
    (is (some #(pos? (:value %)) evs) "burdened bearers export positive congestion order")
    (is (<= (count (filter #(zero? (:volume %)) evs)) 0) "no zero-volume rows (dropped at source)")))

(deftest scoreboard-entry
  (let [s (score/info-control-score (ief/flow-state seed-path) {:descendant 0.85})]
    (is (not (:vetoed? s)) "hoshimori is charter-clean (never a targeting aid (G1 dual-use)) — not vetoed")
    (is (pos? (:score s)) "hoshimori earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hoshimori.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
