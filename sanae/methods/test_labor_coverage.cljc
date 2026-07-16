#!/usr/bin/env bb
;; sanae 早苗 — labor-liberation build-priority coverage tests.
;; Run:  bb --classpath 20-actors 20-actors/sanae/methods/test_labor_coverage.cljc
(ns sanae.methods.test-labor-coverage
  (:require [sanae.methods.labor-coverage :as cov]
            [clojure.test :refer [deftest is run-tests]]))

;; Deterministic roster probe (a test fixture, not the live filesystem): the seven built
;; labor-liberation actors; hofuri + ama deliberately absent so the worklist is stable.
(def ^:private built #{"sanae" "kiyome" "kuramori" "hataori" "soma" "kamado" "tatekata"})
(defn- exists? [slug] (contains? built slug))

(deftest build-priority-ranks-and-annotates
  (let [bp (cov/build-priority exists?)]
    (is (= 10 (count bp)) "all seed sectors present")
    (is (apply >= (map :lps bp)) "sorted by LPS descending")
    (is (every? #(and (:slug %) (contains? % :has-actor)) bp) "each annotated with :slug + :has-actor")
    (is (= "hofuri" (:slug (some #(when (= "hofuri (meat processing)" (:name %)) %) bp)))
        "slug = the sector name's first token, lower-cased")))

(deftest coverage-gaps-are-the-actorless-priorities
  (let [gaps (cov/coverage-gaps (cov/build-priority exists?))
        slugs (set (map :slug gaps))]
    (is (contains? slugs "hofuri") "meat-processing is a priority gap (no actor)")
    (is (contains? slugs "ama") "fishing/aquaculture is a priority gap (no actor)")
    (is (not (contains? slugs "sanae")) "built sectors are not gaps")
    (is (apply >= (map :lps gaps)) "worklist sorted by LPS descending")))

(deftest charter-excluded-never-a-build-target
  ;; even with NO actor anywhere, a charter-fit-0 sector (MINING N1, LPS 0) never enters the worklist
  (let [gaps (cov/coverage-gaps (cov/build-priority (constantly false)))]
    (is (not (some #(= "mining" (:slug %)) gaps)) "MINING stays excluded even with no actor")
    (is (every? #(pos? (:lps %)) gaps) "every worklist item has positive LPS")
    (is (= 9 (count gaps)) "all 9 charter-clean sectors are gaps when the roster is empty")))

(deftest report-renders
  (let [md (cov/coverage-report (cov/build-priority exists?))]
    (is (re-find #"build-priority coverage" md))
    (is (re-find #"hofuri" md))
    (is (re-find #"GAP" md) "the gap sectors are flagged")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'sanae.methods.test-labor-coverage)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
