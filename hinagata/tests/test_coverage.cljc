(ns hinagata.tests.test-coverage
  "hinagata 雛形 — coverage-report tests (ADR-2606111954). 1:1 Clojure port of
  tests/test_coverage.py. Pure fns.

  Verifies coverage honesty (G5): the report measures real coverage against honest denominators,
  surfaces statute-binding integrity (which clauses are not yet anchored to a public law), and
  never claims completeness it does not have."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.coverage-report :as coverage-report]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-report-renders
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage-report/report nodes edges)]
    (is (str/starts-with? md "# hinagata") "coverage report missing title")
    (is (str/includes? md "coverage of all template families") "missing honest-denominator framing")
    (is (str/includes? md "Statute-binding integrity") "missing statute-binding section")))

(deftest test-legal-systems-plural
  (testing "Law is plural — the seed must span more than one legal system."
    (let [{:keys [nodes]} (load-seed)
          systems (disj (set (for [n (vals nodes)
                                   :when (= ":jurisdiction" (get n ":lt/kind"))]
                               (get n ":jurisdiction/system")))
                        nil)]
      (is (clojure.set/subset? #{":civil-law" ":common-law" ":international"} systems)
          (str "expected plural legal systems, got " systems)))))

(deftest test-statute-binding-surfaced-honestly
  (testing "G5: clauses not yet citing a statute are surfaced as the binding worklist, not hidden."
    (let [{:keys [nodes edges]} (load-seed)
          md (coverage-report/report nodes edges)]
      (is (or (str/includes? md "clauses unbound")
              (str/includes? md "cite at least one public statute"))
          "statute-binding integrity not reported"))))

(deftest test-language-coverage-reported-and-multilingual
  (testing "A worldwide commons must be multilingual, and the report must measure it."
    (let [{:keys [nodes edges]} (load-seed)
          md (coverage-report/report nodes edges)
          langs (disj (set (for [n (vals nodes)
                                 :when (= ":template" (get n ":lt/kind"))]
                             (get n ":template/lang")))
                      nil)]
      (is (str/includes? md "Language coverage") "language coverage not reported")
      (is (>= (count langs) 3) (str "expected a multilingual corpus, got languages " langs))
      ;; every :translates 縁 binds known endpoints (no dangling)
      (doseq [e edges
              :when (= ":translates" (get e ":en/kind"))]
        (is (and (contains? nodes (get e ":en/to")) (contains? nodes (get e ":en/from")))
            "dangling :translates 縁")))))

(deftest test-coverage-is-not-overclaimed
  (testing "Coverage fractions vs world denominators must be tiny (honest ~0-by-design)."
    (let [{:keys [nodes edges]} (load-seed)
          md (coverage-report/report nodes edges)
          concepts (count (filter #(= ":concept" (get % ":lt/kind")) (vals nodes)))]
      (is (< concepts 120) "seed should not claim to cover all contract families")
      (is (or (str/includes? md "e-0") (str/includes? md "e-2") (str/includes? md "e-1"))
          "expected scientific-notation tiny fractions"))))
