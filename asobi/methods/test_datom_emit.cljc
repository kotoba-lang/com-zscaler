(ns asobi.methods.test-datom-emit
  "asobi 遊び — Datom-emit tests (ADR-2606073200). 1:1 port of the two datom_emit-dependent
  tests in tests/test_analyze.py (test_datom_emit_ground_and_transient, test_determinism)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [asobi.methods.analyze :as analyze]
            [asobi.methods.datom-emit :as datom-emit]))

(def seed
  (-> *file* clojure.java.io/file .getParentFile .getParentFile
      (clojure.java.io/file "data" "seed-asobi-graph.kotoba.edn")))

(defn- load-seed []
  (let [{:keys [nodes edges]} (analyze/load-file* seed)]
    [nodes edges]))

(deftest test-datom-emit-ground-and-transient
  (let [[nodes edges] (load-seed)
        res (analyze/analyze nodes edges)
        out (datom-emit/emit nodes edges res 7)]
    (is (str/includes? out ":add]") "no ground :add datoms emitted")
    (is (str/includes? out ":work/access") "node attribute datoms missing")
    (is (str/includes? out ":en/access-load") "edge attribute datoms missing")
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/participation-openness"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]")
            (str "derived readout not flagged transient: " line))))
    (is (str/includes? out " 7 :add]"))))

(deftest test-determinism
  (let [[nodes edges] (load-seed)
        a (datom-emit/emit nodes edges (analyze/analyze nodes edges) 1)
        [nodes2 edges2] (load-seed)
        b (datom-emit/emit nodes2 edges2 (analyze/analyze nodes2 edges2) 1)]
    (is (= a b) "Datom emit is not deterministic")))
