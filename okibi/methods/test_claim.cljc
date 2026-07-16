#!/usr/bin/env bb
;; 燠 okibi — claim-emitter tests (the 澪 mio seam shape).
;; Run:  bb --classpath 20-actors 20-actors/okibi/methods/test_claim.cljc
(ns okibi.methods.test-claim
  (:require [okibi.methods.okibi-edn :as oe]
            [okibi.methods.claim :as c]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/okibi/kotoba/seed.edn")
(defn- claims [] (c/from-nodes (oe/sources seed-path) (oe/sinks seed-path)))

(deftest claim-shape-has-five-verification-facts
  (doseq [cl (claims)]
    (is (= :claim (:type cl)))
    (is (= "okibi" (:source-actor cl)))
    (is (= :waste-heat (:flow-class cl)))
    (is (number? (:order-delta-kwh cl)))
    (is (not (clojure.string/blank? (:baseline-method cl))))
    (is (and (>= (:additionality cl) 0.0) (<= (:additionality cl) 1.0)))
    (is (keyword? (:measurement-source cl)))
    (is (string? (:double-count-key cl)))
    (is (number? (:leakage cl)))))

(deftest one-claim-per-match-keys-unique
  (let [cs (claims)]
    (is (pos? (count cs)) "at least one match emits a claim")
    (is (= (count cs) (count (distinct (map :double-count-key cs)))) "keys unique")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'okibi.methods.test-claim)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
