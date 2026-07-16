#!/usr/bin/env bb
;; ugachi × busshi grounding bridge tests.
;; Run:  bb --classpath 20-actors 20-actors/ugachi/methods/test_bridge.cljc
(ns ugachi.methods.test-bridge
  (:require [ugachi.methods.ugachi-edn :as ue]
            [ugachi.methods.bridge :as br]
            [ugachi.methods.gate :as gate]
            [clojure.test :refer [deftest is run-tests]]))

(def ug-seed "20-actors/ugachi/kotoba/seed.edn")
(def bu-seed "20-actors/busshi/kotoba/seed.edn")
(defn- projects [] (ue/projects ug-seed))
(defn- commodities []
  (vec (filter #(= (:type %) :commodity) (ue/normalize-rows (clojure.edn/read-string (slurp bu-seed))))))
(defn- bindex [] (br/busshi-index (commodities)))
(defn- by-id [id] (first (filter #(= id (:id %)) (projects))))
(defn- grounded [id] (br/ground-project (by-id id) (bindex)))

(deftest resource-mapping-covers-seed-resources
  ;; every project resource that busshi tracks must map (others → :unmapped is ok)
  (is (= "cu" (br/resource->commodity "copper")))
  (is (= "w" (br/resource->commodity "tungsten")))
  (is (= "ree" (br/resource->commodity "rare-earth")))
  (is (nil? (br/resource->commodity "aggregate")) "aggregate not a busshi commodity → unmapped"))

(deftest diversify-corroborated-on-concentrated-commodity
  ;; tungsten is 80% single-country in busshi → :critical → diversify is REAL
  (let [g (grounded "w-diversify-c")]
    (is (= :diversify (:monopoly-effect g)))
    (is (empty? (get-in g [:grounding :flags])))
    (is (= :critical (get-in g [:grounding :context :chokepoint])))))

(deftest diversify-downgraded-on-unconcentrated-commodity
  ;; copper top-share is 24 in busshi → :low → "diversify" claim is unsupported
  (let [g (grounded "cu-diversify-b")]
    (is (= :neutral (:monopoly-effect g)) "downgraded from declared :diversify")
    (is (= [:overclaimed-diversification] (get-in g [:grounding :flags])))
    (is (= :diversify (get-in g [:grounding :declared])))))

(deftest entrench-corroborated-on-concentrated-commodity
  ;; rare-earth is 69% single-country → :critical → entrench claim stands
  (let [g (grounded "ree-entrench-f")]
    (is (= :entrench (:monopoly-effect g)))
    (is (empty? (get-in g [:grounding :flags])))))

(deftest unmapped-resource-keeps-declared
  ;; deep-sea nodule / aggregate not in busshi → declared effect preserved, context :unmapped
  (let [g (grounded "deepsea-nodule-d")]
    (is (= :unmapped (get-in g [:grounding :context :busshi])))
    (is (= (:monopoly-effect (by-id "deepsea-nodule-d")) (:monopoly-effect g)))))

(deftest grounding-never-fabricates-entrench
  ;; conservative invariant: no project's grounded effect becomes :entrench unless
  ;; it was ALREADY declared :entrench (grounding must never create a false refusal).
  (let [bi (bindex)]
    (doseq [p (projects)]
      (let [g (br/ground-project p bi)]
        (when (and (= :entrench (:monopoly-effect g))
                   (not= :entrench (:monopoly-effect p)))
          (is false (str (:id p) ": grounding fabricated :entrench")))))
    (is true)))

(deftest grounded-verdict-still-refuses-entrenchment
  ;; end-to-end: grounded assessment keeps the monopoly-entrenchment refusal
  (let [a (br/ground-and-assess (projects) (commodities))]
    (is (get a "grounded"))
    (is (>= (count (get a "adjustments")) 1) "at least the copper overclaim is adjusted")
    ;; ree-entrench-f still refused after grounding
    (let [g (grounded "ree-entrench-f")]
      (is (= :refuse (:verdict (gate/verdict g)))))
    ;; cu-diversify-b still permitted (downgrade to neutral doesn't refuse)
    (let [g (grounded "cu-diversify-b")]
      (is (= :propose-r0 (:verdict (gate/verdict g)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-bridge)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
