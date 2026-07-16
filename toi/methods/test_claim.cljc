#!/usr/bin/env bb
;; 樋 toi — claim-emitter tests (the 澪 mio seam shape).
;; Run:  bb --classpath 20-actors 20-actors/toi/methods/test_claim.cljc
(ns toi.methods.test-claim
  (:require [toi.methods.toi-edn :as te]
            [toi.methods.claim :as c]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/toi/kotoba/seed.edn")
(defn- claims [] (c/from-nodes (te/jobs seed-path) (te/sites seed-path)))

(deftest claim-shape-has-five-verification-facts
  (doseq [cl (claims)]
    (is (= :claim (:type cl)))
    (is (= "toi" (:source-actor cl)))
    (is (= :compute-routing (:flow-class cl)))
    (is (and (number? (:order-delta-kwh cl)) (pos? (:order-delta-kwh cl))) (str (:id cl) " carries routed kWh"))
    (is (not (clojure.string/blank? (:baseline-method cl))))
    (is (and (>= (:additionality cl) 0.0) (<= (:additionality cl) 1.0)))
    (is (keyword? (:measurement-source cl)))
    (is (string? (:double-count-key cl)))
    (is (number? (:leakage cl)))))

(deftest one-claim-per-routing-keys-unique
  (let [cs (claims)]
    (is (= 5 (count cs)) "five routings → five claims")
    (is (= (count cs) (count (distinct (map :double-count-key cs)))) "keys unique")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'toi.methods.test-claim)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
