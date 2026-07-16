#!/usr/bin/env bb
;; 樋 toi — seed loader tests.
;; Run:  bb --classpath 20-actors 20-actors/toi/methods/test_toi_edn.cljc
(ns toi.methods.test-toi-edn
  (:require [toi.methods.toi-edn :as te]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/toi/kotoba/seed.edn")

(deftest loads-jobs-and-sites
  (is (>= (count (te/jobs seed-path)) 6) "≥6 jobs")
  (is (>= (count (te/sites seed-path)) 5) "≥5 sites")
  (is (every? #(= :job (:type %)) (te/jobs seed-path)))
  (is (every? #(= :site (:type %)) (te/sites seed-path))))

(deftest jobs-have-energy-and-movability
  (doseq [j (te/jobs seed-path)]
    (is (number? (:kwh j)) (str (:id j) " has kwh"))
    (is (contains? j :movable) (str (:id j) " declares movability"))))

(deftest sites-have-carbon-and-capacity
  (doseq [s (te/sites seed-path)]
    (is (number? (:carbon-intensity s)) (str (:id s) " has carbon-intensity"))
    (is (number? (:capacity-kwh s)))
    (is (contains? s :heat-demand-sink) (str (:id s) " declares heat-demand-sink"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'toi.methods.test-toi-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
