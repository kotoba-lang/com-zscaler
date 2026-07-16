#!/usr/bin/env bb
;; busshi 物資 — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/busshi/methods/test_ie_flow.cljc
(ns busshi.methods.test-ie-flow
  (:require [busshi.methods.busshi-edn :as be]
            [busshi.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/busshi/kotoba/seed.edn")
(defn- cs [] (be/commodities seed-path))

(deftest events-well-formed
  (let [evs (ief/flow-events (cs))]
    (is (pos? (count evs)) "one event per commodity")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "busshi is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "busshi" (:actor %)) evs))
    (is (every? #(zero? (:risk %)) evs) "observation-only — never trades/mines, no actuation risk")))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state (cs))]
    (is (pos? (:order-index st)) "busshi RECTIFIES scattered commodity risk → positive order-index")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest de-monopolization-outranks-resilience
  (let [by-type (group-by :type (ief/flow-events (cs)))]
    ;; a clear chokepoint (de-monopolization) exports more order per unit risk than diffuse baseline
    (when (and (seq (get by-type "de-monopolization")) (seq (get by-type "resilience")))
      (let [dm-factor (/ (:value (first (get by-type "de-monopolization")))
                         (max 1e-9 (:volume (first (get by-type "de-monopolization")))))
            res-factor (/ (:value (first (get by-type "resilience")))
                          (max 1e-9 (:volume (first (get by-type "resilience")))))]
        (is (> dm-factor res-factor) "de-monopolization rectifies more order per unit risk than resilience")))))

(deftest scoreboard-entry
  (let [s (score/info-control-score (ief/flow-state (cs)) {:descendant 0.8})]
    (is (not (:vetoed? s)) "busshi is charter-clean (observation-only) — not vetoed")
    (is (pos? (:score s)) "busshi earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
