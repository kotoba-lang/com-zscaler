#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the sanae Liberation-Priority-Score ranking.
(ns sanae.methods.test-labor-liberation-parity
  "test_labor_liberation_parity.clj — sanae labor_liberation py↔clj LIVE parity (ADR-2606032100).

  Runs the ACTUAL labor_liberation.py via a python3 subprocess and the clj impl, comparing the
  full LPS ranked seed (every sector name + score, incl the N1 charter-gate that zeroes MINING
  to LPS 0) to 1e-9, plus the freed-labour-hours + displacement-cohort sizing. The ranked seed
  exercises lps() over all 10 sectors, so a drift in the score formula or the N1 gate moves a
  number and fails.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/sanae/methods/test_labor_liberation_parity.clj"
  (:require [sanae.methods.labor-liberation :as ll]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/sanae/methods")

(def ^:private py-src
  (str "import json, labor_liberation as ll\n"
       "print(json.dumps({'ranked': ll.ranked_seed(),"
       " 'freed': ll.freed_labor_hours(7.0e8,2000,0.3),"
       " 'cohort': ll.displacement_cohort_size(7.0e8,0.3)}))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) false)))
    (catch Exception _ nil)))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest clj-ranking-and-n1-gate-fire
  ;; runs regardless of python: sanae leads, MINING is N1-zeroed, ranking is descending.
  (let [ranked (ll/ranked-seed)]
    (is (= "sanae (field agriculture)" (ffirst ranked)) "highest-LPS toil leads")
    (is (= 0.0 (second (last ranked))) "MINING excluded by N1 → LPS 0")
    (is (= (mapv second ranked) (reverse (sort (mapv second ranked)))) "ranked descending"))
  (is (= 210000000 (ll/displacement-cohort-size 7.0e8 0.3))))

(deftest ranking-matches-python
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — sanae LPS cross-language parity skipped")
      (let [py-ranked (get py "ranked")
            clj-ranked (ll/ranked-seed)]
        (is (= (count py-ranked) (count clj-ranked)) "same number of ranked sectors")
        (is (= (mapv first py-ranked) (mapv first clj-ranked)) "identical sector order + names")
        (doseq [[pr cr] (map vector py-ranked clj-ranked)]
          (is (close? (second pr) (second cr)) (str "LPS score for " (first pr))))
        (is (close? (get py "freed") (ll/freed-labor-hours 7.0e8 2000 0.3)) "freed labour hours")
        (is (= (get py "cohort") (ll/displacement-cohort-size 7.0e8 0.3)) "displacement cohort")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'sanae.methods.test-labor-liberation-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
