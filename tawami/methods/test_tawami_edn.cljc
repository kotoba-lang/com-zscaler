#!/usr/bin/env bb
;; 撓 tawami — seed loader tests.
;; Run:  bb --classpath 20-actors 20-actors/tawami/methods/test_tawami_edn.cljc
(ns tawami.methods.test-tawami-edn
  (:require [tawami.methods.tawami-edn :as te]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tawami/kotoba/seed.edn")

(deftest loads-assets
  (let [as (te/assets seed-path)]
    (is (vector? as))
    (is (>= (count as) 11) "at least eleven seeded assets")
    (is (every? #(= :asset (:type %)) as))))

(deftest assets-have-required-shape
  (doseq [x (te/assets seed-path)]
    (is (string? (:id x)))
    (is (keyword? (:resource-class x)) (str (:id x) " has a resource-class"))
    (is (number? (:shiftable-kw x)) (str (:id x) " has shiftable-kw"))
    (is (number? (:response-time-min x)) (str (:id x) " has response-time-min"))
    (is (number? (:availability x)) (str (:id x) " has availability"))))

(deftest classify-splits-by-type
  (let [rows (te/load-edn seed-path)
        {:keys [assets]} (te/classify rows)]
    (is (= (count assets) (count (filter #(= :asset (:type %)) rows))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tawami.methods.test-tawami-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
