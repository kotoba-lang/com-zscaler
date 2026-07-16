(ns hokorobi.tests.test-coverage
  "hokorobi 綻び — coverage-report tests (ADR-2606073400). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hokorobi.methods.analyze :as analyze]
            [hokorobi.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-finrisk-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/includes? md "coverage of all institutions is ~0 by design"))
    (is (str/includes? md "Gap map"))
    ;; the three financial pillars (bank/insurer/pension) appear in a real seed
    (is (and (str/includes? md "bank")
             (str/includes? md "insurer")
             (str/includes? md "pension-fund")))))

(deftest test-three-pillars-present
  (let [{:keys [nodes]} (load-seed)
        sectors (set (for [n (vals nodes)
                           :when (= ":institution" (get n ":organism/kind"))]
                       (get n ":inst/sector")))]
    (is (clojure.set/subset? #{":bank" ":insurer" ":pension-fund"} sectors)
        (str "missing a pillar: " sectors))))
