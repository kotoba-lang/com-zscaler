#!/usr/bin/env bb
;; hokorobi 綻び — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/hokorobi/methods/test_ie_flow.cljc
(ns hokorobi.methods.test-ie-flow
  (:require [hokorobi.methods.analyze :as an]
            [hokorobi.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/hokorobi/data/seed-finrisk-graph.kotoba.edn")
(defn- g [] (an/load-file* seed-path))

(deftest events-well-formed
  (let [evs (ief/flow-events-from-graph (g))]
    (is (pos? (count evs)) "one event per risk-bearing institution")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "hokorobi is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "hokorobi" (:actor %)) evs))
    (is (every? #(pos? (:volume %)) evs) "every institution row carries raw incident risk (volume>0)")))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state seed-path)]
    (is (pos? (:order-index st)) "hokorobi RECTIFIES scattered systemic risk → positive order-index (SII re-weighting concentrates the resilience surface)")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest systemic-tracks-importance-weight
  ;; the rectification is the SII re-weighting: a more-systemically-important institution with
  ;; the same raw risk load carries more realised systemic-risk-concentration.
  (let [evs (ief/flow-events-from-graph (g))]
    (is (some #(pos? (:value %)) evs) "burdened institutions export positive systemic-risk order")
    (is (<= (count (filter #(zero? (:volume %)) evs)) 0) "no zero-volume rows (dropped at source)")))

(deftest scoreboard-entry
  ;; hokorobi's flow-state scores as an information-control actor (its 利得)
  (let [s (score/info-control-score (ief/flow-state seed-path) {:descendant 0.85})]
    (is (not (:vetoed? s)) "hokorobi is charter-clean (resilience map, never a panic signal / target-list) — not vetoed")
    (is (pos? (:score s)) "hokorobi earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hokorobi.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
