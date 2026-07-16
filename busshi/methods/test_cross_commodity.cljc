#!/usr/bin/env bb
;; busshi 物資 — tests for the per-producer cross-commodity chokepoint lens.
;; Run:  bb --classpath 20-actors 20-actors/busshi/methods/test_cross_commodity.cljc
(ns busshi.methods.test-cross-commodity
  "Tests for cross-commodity-chokepoints — the per-producer cross-commodity view that surfaces the
  producer dominating the most (high-multigen-risk) commodities, the systemic §2(l) de-monopolization
  priority a per-commodity ranking misses. Aggregate MAP (producer↔commodity counts + a risk
  weight), routed to resilience, never a target-list / trade (G1/G2/G5)."
  (:require [busshi.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private commodities
  [{:id "cu" :name "Copper"  :class :base-metal    :producers [[:CL 28] [:other 72]]          :carbon-intensity 0.5 :irreversibility 0.6}
   {:id "li" :name "Lithium" :class :rare-critical :producers [[:CL 40] [:AU 30] [:other 30]] :carbon-intensity 0.3 :irreversibility 0.7}
   {:id "au" :name "Gold"    :class :precious      :producers [[:AU 15] [:other 85]]          :carbon-intensity 0.2 :irreversibility 0.2}])

(def ^:private result (a/analyze commodities))

(deftest the-producer-spanning-the-most-commodities-leads
  (let [{:keys [producer commodities-count commodities]} (first (a/cross-commodity-chokepoints result))]
    (is (= :CL producer) "CL tops Copper + Lithium → leads the cross-commodity list")
    (is (= 2 commodities-count) "CL is the top source for 2 commodities")
    (is (= ["Copper" "Lithium"] commodities) "the commodities it dominates, name-sorted")))

(deftest risk-weight-sums-the-dominated-commodities-multigen-risk
  (let [by-prod (into {} (map (juxt :producer identity) (a/cross-commodity-chokepoints result)))]
    (is (= 1 (:commodities-count (by-prod :AU))) "AU dominates only Gold")
    (is (> (:risk-weight (by-prod :CL)) (:risk-weight (by-prod :AU)))
        "CL's risk weight exceeds AU's — more commodities, higher multigen risk")))

(deftest aggregate-only-producer-and-commodity-no-other-detail
  (let [row (first (a/cross-commodity-chokepoints result))]
    (is (= #{:producer :commodities-count :risk-weight :commodities} (set (keys row)))
        "rows carry only producer + counts + the commodity names it tops — no coordinates/persons")))

(deftest limit-caps-the-list
  (is (<= (count (a/cross-commodity-chokepoints result 1)) 1) "the optional limit truncates the ranking"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-cross-commodity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
