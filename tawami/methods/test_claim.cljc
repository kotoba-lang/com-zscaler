#!/usr/bin/env bb
;; 撓 tawami — claim-emitter tests (the 澪 mio seam shape).
;; Run:  bb --classpath 20-actors 20-actors/tawami/methods/test_claim.cljc
(ns tawami.methods.test-claim
  (:require [tawami.methods.tawami-edn :as te]
            [tawami.methods.claim :as c]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tawami/kotoba/seed.edn")
(defn- claims [] (c/from-assets (te/assets seed-path)))

(def mio-flow-classes #{:peak-shave :renewable-absorb :compute-routing :flexibility :intention :waste-heat})

(deftest claim-shape-has-five-verification-facts
  (doseq [cl (claims)]
    (is (= :claim (:type cl)))
    (is (= "tawami" (:source-actor cl)))
    (is (contains? mio-flow-classes (:flow-class cl)) (str (:id cl) " has a valid mio flow-class"))
    (is (number? (:order-delta-kwh cl)))
    ;; the five §9 verification facts mio requires
    (is (not (clojure.string/blank? (:baseline-method cl))) (str (:id cl) " baseline"))
    (is (and (>= (:additionality cl) 0.0) (<= (:additionality cl) 1.0)) (str (:id cl) " additionality"))
    (is (keyword? (:measurement-source cl)) (str (:id cl) " measurement"))
    (is (string? (:double-count-key cl)) (str (:id cl) " double-count-key"))
    (is (number? (:leakage cl)) (str (:id cl) " leakage"))))

(deftest one-claim-per-asset-keys-unique
  (let [cs (claims)]
    (is (= (count cs) (count (te/assets seed-path))))
    (is (= (count cs) (count (distinct (map :double-count-key cs)))) "double-count keys unique")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tawami.methods.test-claim)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
