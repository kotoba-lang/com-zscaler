(ns asobi.tests.test-coverage
  "asobi 遊び — coverage-report tests (ADR-2606073200). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [asobi.methods.analyze :as analyze]
            [asobi.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-asobi-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (clojure.string/includes? md "coverage of all culture is ~0 by design"))
    (is (clojure.string/includes? md "Gap map"))
    ;; both an open and an enclosed access category appear in a real seed
    (is (and (clojure.string/includes? md "public-domain")
             (clojure.string/includes? md "proprietary")))))

(deftest test-media-and-domains-present
  (let [{:keys [nodes]} (load-seed)
        media (set (for [n (vals nodes)
                         :when (= ":work" (get n ":organism/kind"))]
                     (get n ":work/medium")))
        domains (set (for [n (vals nodes)
                           :when (= ":practice" (get n ":organism/kind"))]
                       (get n ":practice/domain")))]
    (is (and (contains? media ":music") (contains? media ":text")))
    (is (and (contains? domains ":sport") (contains? domains ":music")))))
