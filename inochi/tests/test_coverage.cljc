(ns inochi.tests.test-coverage
  "inochi 命 — coverage-report tests (ADR-2606073000). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [inochi.methods.analyze :as analyze]
            [inochi.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-biosphere-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    ;; honest denominator disclosure present
    (is (clojure.string/includes? md "coverage of all life is ~0 by design"))
    ;; all three realms are represented in a real seed
    (is (and (clojure.string/includes? md "terrestrial")
             (clojure.string/includes? md "marine")))
    ;; the gap map names next-wave targets (freshwater/fungi/etc. are thin by design)
    (is (clojure.string/includes? md "Gap map"))))

(deftest test-realms-present
  (let [{:keys [nodes]} (load-seed)
        realms (set (for [n (vals nodes)
                          :when (contains? #{":ecosystem" ":biome"} (get n ":organism/kind"))]
                      (get n ":eco/realm")))]
    (is (and (contains? realms ":terrestrial") (contains? realms ":marine")))))
