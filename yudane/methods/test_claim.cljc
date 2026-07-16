#!/usr/bin/env bb
;; 委 yudane — claim-emitter tests (the 澪 mio seam shape; consent-bound + content-free).
;; Run:  bb --classpath 20-actors 20-actors/yudane/methods/test_claim.cljc
(ns yudane.methods.test-claim
  (:require [yudane.methods.yudane-edn :as ye]
            [yudane.methods.claim :as c]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/yudane/kotoba/seed.edn")
(defn- claims [] (c/from-offers (ye/offers seed-path)))

(deftest only-consented-offers-emit-claims
  ;; the seed has 4 consented + 4 refused → exactly 4 claims (consent-bound seam).
  (is (= 4 (count (claims)))))

(deftest claim-shape-has-five-verification-facts-and-is-content-free
  (doseq [cl (claims)]
    (is (= :claim (:type cl)))
    (is (= "yudane" (:source-actor cl)))
    (is (= :intention (:flow-class cl)))
    (is (number? (:order-delta-kwh cl)))
    (is (not (clojure.string/blank? (:baseline-method cl))))
    (is (and (>= (:additionality cl) 0.0) (<= (:additionality cl) 1.0)))
    (is (keyword? (:measurement-source cl)))
    (is (string? (:double-count-key cl)))
    (is (number? (:leakage cl)))
    ;; content-free: the claim carries no per-person key
    (is (not (contains? cl :person)))
    (is (not (contains? cl :intent-content)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'yudane.methods.test-claim)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
