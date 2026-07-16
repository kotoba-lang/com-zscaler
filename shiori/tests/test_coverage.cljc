(ns shiori.tests.test-coverage
  "shiori 栞 — coverage-report tests (ADR-2606082100). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [shiori.methods.analyze :as analyze]
            [shiori.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-wellbecoming-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/includes? md "coverage of all cohorts/detractors is ~0 by design"))
    (is (str/includes? md "Gap map"))
    ;; all four facets present: cohort, detractor, driver (structural), mitigator
    (is (and (str/includes? md "isolation")
             (str/includes? md "always-on-work-culture")
             (str/includes? md "social-connection")))))

(deftest test-all-severities-and-both-relief-and-pressure-present
  (let [{:keys [nodes]} (load-seed)
        detrs (filter #(= ":detractor" (get % ":organism/kind")) (vals nodes))
        sevs (set (map #(get % ":detractor/severity") detrs))
        kinds (set (map #(get % ":organism/kind") (vals nodes)))]
    (is (clojure.set/subset? #{":critical" ":severe" ":moderate"} sevs)
        (str "thin severity spine: " sevs))
    (is (contains? kinds ":mitigator") "no relief side (the 守り) in the seed")
    (is (contains? kinds ":driver") "no structural-driver side in the seed")))
