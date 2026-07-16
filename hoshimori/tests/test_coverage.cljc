(ns hoshimori.tests.test-coverage
  "hoshimori 星守 — coverage-report tests (ADR-2606073600). 1:1 Clojure port of tests/test_coverage.py."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hoshimori.methods.analyze :as analyze]
            [hoshimori.methods.coverage-report :as coverage]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-orbit-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-coverage-renders-and-is-honest
  (let [{:keys [nodes edges]} (load-seed)
        md (coverage/report nodes edges)]
    (is (str/includes? md "coverage of all catalogued objects is ~0 BY DESIGN"))
    (is (str/includes? md "Gap map"))
    ;; the key regimes appear in a real seed
    (is (and (str/includes? md "leo-low")
             (str/includes? md "geo")
             (str/includes? md "meo")))))

(deftest test-all-regimes-present
  (let [{:keys [nodes]} (load-seed)
        regimes (set (for [n (vals nodes)
                           :when (= ":shell" (get n ":organism/kind"))]
                       (get n ":shell/regime")))]
    (is (clojure.set/subset? #{":leo-low" ":sso" ":meo" ":geo"} regimes)
        (str "missing a regime: " regimes))))
