#!/usr/bin/env bb
;; shionome 潮目 — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/shionome/methods/test_ie_flow.cljc
(ns shionome.methods.test-ie-flow
  (:require [shionome.methods.edn :as e]
            [shionome.methods.weave :as w]
            [shionome.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")
(defn- g [] (w/weave (e/load-edn seed-path)))

(deftest events-well-formed
  (let [evs (ief/flow-events-from-graph (g))]
    (is (pos? (count evs)) "one event per bucket")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "shionome is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "shionome" (:actor %)) evs))
    (is (every? #(<= (:value %) (:volume %)) evs)
        "value (net accumulation) ≤ volume (gross churn) — the rectified fraction")))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state seed-path)]
    (is (pos? (:order-index st)) "shionome RECTIFIES scattered churn → positive order-index (capital pools)")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest distributing-buckets-export-no-realised-order
  ;; net-outflow buckets are the scattered SOURCE → 0 realised order; only the pools (net-inflow) export order
  (let [evs (ief/flow-events-from-graph (g))]
    (is (some #(zero? (:value %)) evs) "the net-outflow (distributing) buckets export 0 order")
    (is (some #(pos? (:value %)) evs) "the net-inflow (pool) buckets export positive order")))

(deftest scoreboard-entry
  ;; shionome's flow-state scores as an information-control actor (its 利得)
  (let [s (score/info-control-score (ief/flow-state seed-path) {:descendant 0.9})]
    (is (not (:vetoed? s)) "shionome is charter-clean (never trades, G2) — not vetoed")
    (is (pos? (:score s)) "shionome earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'shionome.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
