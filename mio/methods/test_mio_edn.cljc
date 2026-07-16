#!/usr/bin/env bb
;; 澪 mio — seed loader tests.
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_mio_edn.cljc
(ns mio.methods.test-mio-edn
  (:require [mio.methods.mio-edn :as me]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/mio/kotoba/seed.edn")

(deftest loads-claims
  (let [cs (me/claims seed-path)]
    (is (vector? cs))
    (is (>= (count cs) 15) "at least fifteen seeded claims")
    (is (every? #(= :claim (:type %)) cs))))

(deftest claims-have-required-shape
  (doseq [c (me/claims seed-path)]
    (is (string? (:id c)))
    (is (keyword? (:flow-class c)) (str (:id c) " has a flow-class keyword"))
    (is (number? (:order-delta-kwh c)) (str (:id c) " has a numeric order-delta"))
    (is (contains? c :additionality) (str (:id c) " declares additionality"))
    (is (contains? c :leakage) (str (:id c) " declares leakage"))
    (is (contains? c :double-count-key) (str (:id c) " declares a double-count-key"))))

(deftest classify-splits-by-type
  (let [rows (me/load-edn seed-path)
        {:keys [claims]} (me/classify rows)]
    (is (= (count claims) (count (filter #(= :claim (:type %)) rows))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-mio-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
