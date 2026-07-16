#!/usr/bin/env bb
;; kafun 花粉 — seed loader tests.
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_kafun_edn.cljc
(ns kafun.methods.test-kafun-edn
  (:require [kafun.methods.kafun-edn :as ke]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/kafun/kotoba/seed.edn")

(deftest loads-all-stands
  (let [ss (ke/stands seed-path)]
    (is (= 12 (count ss)) "seed has 12 synthetic stands")
    (is (every? #(= :stand (:type %)) ss))
    (is (every? :id ss))
    (is (apply distinct? (map :id ss)) "stand ids are unique")))

(deftest classify-splits-by-type
  (let [rows (ke/load-edn seed-path)
        {:keys [stands]} (ke/classify rows)]
    (is (= (count rows) (count stands)) "every seed row is a :stand")))

(deftest stands-declare-known-keys
  (let [a (first (filter #(= "sugi-tama-a" (:id %)) (ke/stands seed-path)))]
    (is (= :sugi (:species a)))
    (is (number? (:area-ha a)))
    (is (contains? #{:net-negative :net-neutral :net-positive} (:carbon a)))
    (is (contains? #{:none :partial :sufficient} (:sapling-supply a)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-kafun-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
