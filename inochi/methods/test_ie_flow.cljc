#!/usr/bin/env bb
;; inochi 命 — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/inochi/methods/test_ie_flow.cljc
(ns inochi.methods.test-ie-flow
  (:require [inochi.methods.analyze :as an]
            [inochi.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/inochi/data/seed-biosphere-graph.kotoba.edn")
(defn- g [] (an/load-file* seed-path))

(deftest events-well-formed
  (let [evs (ief/flow-events-from-graph (g))]
    (is (pos? (count evs)) "one event per pressure-bearing bearer")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "inochi is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "inochi" (:actor %)) evs))
    (is (every? #(pos? (:volume %)) evs) "every bearer row carries raw incident pressure (volume>0)")))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state seed-path)]
    (is (pos? (:order-index st)) "inochi RECTIFIES scattered ecological pressure → positive order-index (IUCN re-weighting concentrates restoration)")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest restoration-tracks-iucn-essentiality
  ;; the rectification is the IUCN re-weighting: value (restoration) is the load × essentiality,
  ;; so a high-threat bearer with the same raw load carries more realised restoration order.
  (let [evs (ief/flow-events-from-graph (g))]
    (is (some #(pos? (:value %)) evs) "burdened bearers export positive restoration order")
    (is (<= (count (filter #(zero? (:volume %)) evs)) 0) "no zero-volume rows (dropped at source)")))

(deftest scoreboard-entry
  ;; inochi's flow-state scores as an information-control actor (its 利得)
  (let [s (score/info-control-score (ief/flow-state seed-path) {:descendant 0.9})]
    (is (not (:vetoed? s)) "inochi is charter-clean (restoration map, never a target-list) — not vetoed")
    (is (pos? (:score s)) "inochi earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'inochi.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
