(ns hinagata.tests.test-maturity
  "hinagata 雛形 — maturity-scorecard tests (ADR-2606111954). 1:1 Clojure port of
  tests/test_maturity.py. Pure fns (maturity reuses analyze + validate).

  The scorecard reports DISCLOSED structural facts about the commons (size / grounding /
  integrity), never a verdict on the law (G1/G3/G5)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.maturity :as maturity]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))
(defn load-seed [] (analyze/load-file* seed))

(deftest test-scorecard-renders-and-is-generated-banner
  (let [{:keys [nodes edges]} (load-seed)
        md (maturity/maturity nodes edges)]
    (is (and (str/starts-with? md "# hinagata") (str/includes? md "GENERATED")))
    (doseq [section ["## Size" "## Quality gates" "## Core-clause worldwide grounding" "## Readiness"]]
      (is (str/includes? md section) (str "missing section " section)))))

(deftest test-scorecard-reports-clean-integrity
  (let [{:keys [nodes edges]} (load-seed)
        md (maturity/maturity nodes edges)]
    (is (str/includes? md "0 errors / 0 warnings") "scorecard should reflect clean integrity")))

(deftest test-core-clauses-grounded-across-multiple-jurisdictions
  (testing "Each of the 8 core clauses must rest on law in >1 jurisdiction (the worldwide goal)."
    (let [{:keys [nodes edges]} (load-seed)
          st-jx (into {} (for [n (vals nodes) :when (= ":statute" (get n ":lt/kind"))]
                           [(get n ":lt/id") (get n ":statute/jurisdiction")]))]
      (doseq [cl maturity/core-clauses]
        (is (contains? nodes cl) (str "core clause " cl " missing from graph"))
        (let [jx (set (for [e edges
                            :when (and (contains? analyze/cite-kinds (get e ":en/kind"))
                                       (= cl (get e ":en/from")))]
                        (get st-jx (get e ":en/to"))))
              jx (set (filter some? jx))]
          (is (>= (count jx) 2) (str "core clause " cl " grounded in only " (count jx) " jurisdiction(s)")))))))
