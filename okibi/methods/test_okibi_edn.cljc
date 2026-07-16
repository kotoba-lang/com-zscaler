#!/usr/bin/env bb
;; 燠 okibi — seed loader tests.
;; Run:  bb --classpath 20-actors 20-actors/okibi/methods/test_okibi_edn.cljc
(ns okibi.methods.test-okibi-edn
  (:require [okibi.methods.okibi-edn :as oe]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/okibi/kotoba/seed.edn")

(deftest loads-sources-and-sinks
  (is (>= (count (oe/sources seed-path)) 4) "≥4 sources")
  (is (>= (count (oe/sinks seed-path)) 6) "≥6 sinks")
  (is (every? #(= :source (:type %)) (oe/sources seed-path)))
  (is (every? #(= :sink (:type %)) (oe/sinks seed-path))))

(deftest sources-have-temp-and-location
  (doseq [s (oe/sources seed-path)]
    (is (number? (:temp-c s)) (str (:id s) " has supply temp"))
    (is (number? (:kw s)))
    (is (and (number? (:lat s)) (number? (:lon s))) (str (:id s) " has coordinates"))))

(deftest sinks-have-required-temp
  (doseq [k (oe/sinks seed-path)]
    (is (number? (:temp-req-c k)) (str (:id k) " has a required temperature"))
    (is (number? (:kw-demand k)))
    (is (and (number? (:lat k)) (number? (:lon k))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'okibi.methods.test-okibi-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
