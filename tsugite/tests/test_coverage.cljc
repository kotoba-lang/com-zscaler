(ns tsugite.tests.test-coverage
  "tsugite 継ぎ手 — coverage-report tests (ADR-2606073800). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [tsugite.methods.analyze :as analyze]
            [tsugite.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-peoples-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/includes? md "coverage of all peoples/languages is ~0 by design"))
    (is (str/includes? md "Gap map"))
    ;; both strands present: displacement (refugee/stateless) and erasure (endangered languages)
    (is (and (str/includes? md "refugee-population")
             (str/includes? md "critically-endangered")))))

(deftest test-both-strands-present
  (let [{:keys [nodes]} (load-seed)
        pkinds (set (for [n (vals nodes)
                          :when (= ":people" (get n ":organism/kind"))]
                      (get n ":people/kind")))
        langs (filter #(= ":language" (get % ":organism/kind")) (vals nodes))]
    (is (clojure.set/subset? #{":refugee-population" ":stateless" ":indigenous"} pkinds)
        (str "thin peoples: " pkinds))
    (is (>= (count langs) 5) "endangered-language strand too thin")))
