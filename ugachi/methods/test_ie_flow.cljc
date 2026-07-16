#!/usr/bin/env bb
;; ugachi 穿ち — ie-flow embedding tests (the SoS scoring leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/ugachi/methods/test_ie_flow.cljc
(ns ugachi.methods.test-ie-flow
  (:require [ugachi.methods.ugachi-edn :as ue]
            [ugachi.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/ugachi/kotoba/seed.edn")
(defn- ps [] (ue/projects seed-path))

(deftest events-well-formed
  (let [evs (ief/flow-events (ps))]
    (is (pos? (count evs)) "one event per project")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "ugachi is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "ugachi" (:actor %)) evs))))

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state (ps))]
    (is (pos? (:order-index st)) "ugachi RECTIFIES scattered extraction-risk → positive order-index")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest refuse-exports-protective-only
  (let [by-type (group-by :type (ief/flow-events (ps)))]
    (is (every? #(zero? (:value %)) (get by-type "refuse"))
        "refuse is PROTECTIVE — exports 0 stewardship-energy (a catastrophic/monopolistic project rejected)")
    (when-let [permits (get by-type "propose-r0")]
      (is (every? #(pos? (:value %)) permits) "propose-r0 delivers realised stewardship order"))))

(deftest scoreboard-entry
  ;; ugachi's flow-state scores as an information-control actor (its 利得)
  (let [s (score/info-control-score (ief/flow-state (ps)) {:descendant 0.8})]
    (is (not (:vetoed? s)) "ugachi is charter-clean — not vetoed")
    (is (pos? (:score s)) "ugachi earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
