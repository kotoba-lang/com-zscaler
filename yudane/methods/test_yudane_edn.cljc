#!/usr/bin/env bb
;; 委 yudane — seed loader tests.
;; Run:  bb --classpath 20-actors 20-actors/yudane/methods/test_yudane_edn.cljc
(ns yudane.methods.test-yudane-edn
  (:require [yudane.methods.yudane-edn :as ye]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/yudane/kotoba/seed.edn")

(deftest loads-offers
  (let [os (ye/offers seed-path)]
    (is (>= (count os) 8) "at least eight seeded offers")
    (is (every? #(= :offer (:type %)) os))))

(deftest offers-are-aggregate-and-have-no-person-field
  (doseq [o (ye/offers seed-path)]
    (is (keyword? (:intent-class o)) (str (:id o) " has an intent-class"))
    (is (number? (:cohort-size o)) (str (:id o) " is an AGGREGATE cohort"))
    (is (contains? o :reciprocal) (str (:id o) " declares reciprocity"))
    ;; structural privacy: a seed offer must carry no per-person key
    (is (not (contains? o :person)) (str (:id o) " has no :person field"))
    (is (not (contains? o :intent-content)) (str (:id o) " has no per-person intent text"))))

(deftest classify-splits-by-type
  ;; seed.edn now ships as Datomic/Datascript tx-data (per-row entity, :db/id +
  ;; :yudane.offer/* namespaced keys) — raw rows carry :yudane.offer/type, not a bare
  ;; :type, so `classify` reconstitutes each row before filtering. This asserts both
  ;; the on-disk shape and that every seeded row here is (still) an :offer.
  (let [rows (ye/load-edn seed-path)
        {:keys [offers]} (ye/classify rows)]
    (is (every? #(contains? % :db/id) rows) "seed.edn ships as tx-data (per-row entity)")
    (is (= (count offers) (count rows)) "every seeded row in this file is an :offer")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'yudane.methods.test-yudane-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
