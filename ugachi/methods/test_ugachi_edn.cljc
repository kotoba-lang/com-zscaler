#!/usr/bin/env bb
;; ugachi 穿ち — loader tests.
;; Run:  bb --classpath 20-actors 20-actors/ugachi/methods/test_ugachi_edn.cljc
(ns ugachi.methods.test-ugachi-edn
  (:require [ugachi.methods.ugachi-edn :as ue]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/ugachi/kotoba/seed.edn")

(deftest parse-edn-roundtrips
  (let [rows (ue/parse-edn "[{:type :project :id \"x\" :resource \"copper\"}]")]
    (is (= "x" (:id (first rows))))
    (is (= :project (:type (first rows))))))

(deftest load-and-classify
  (let [{:keys [projects]} (ue/classify (ue/load-edn seed-path))]
    (is (>= (count projects) 10) "seed has the gate-shape spread")
    (is (every? #(= :project (:type %)) projects))
    (is (every? #(contains? % :consent) projects) "every project declares a consent boolean")
    (is (every? #(contains? % :carbon) projects) "every project declares a carbon class")))

(deftest projects-convenience
  (let [ps (ue/projects seed-path)]
    (is (some #(= "deepsea-nodule-d" (:id %)) ps))
    (is (some #(= "cu-diversify-b" (:id %)) ps))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-ugachi-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
