(ns hoshimori.methods.test-datom-emit
  "hoshimori 星守 — Datom-emit tests (ADR-2606073600). 1:1 Clojure port of the two
  datom_emit-dependent tests in tests/test_analyze.py:

    - test_datom_emit_ground_and_transient: GROUND :add datoms + node/edge attribute datoms
      are present; every emitted :bond/* line is flagged :derived] (transient, never stored —
      N1/G2); the tx number threads through ' 7 :add]'.
    - test_determinism: emitting twice from the same seed yields byte-identical output.

  (The five pure-analyze assertions live in tests/test_analyze.cljc; this unit covers the
  datom_emit sibling, mirroring the inochi/asobi/hokorobi precedent.)"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hoshimori.methods.analyze :as analyze]
            [hoshimori.methods.datom-emit :as datom-emit]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-orbit-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        out (datom-emit/emit nodes edges res 7)]
    (is (str/includes? out ":add]") "no ground :add datoms emitted")
    (is (str/includes? out ":shell/regime") "node attribute datoms missing")
    (is (str/includes? out ":en/orbit-load") "edge attribute datoms missing")
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/congestion-concentration"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]")
            (str "derived readout not flagged transient: " line))))
    (is (str/includes? out " 7 :add]"))))

(deftest test-determinism
  (let [{:keys [nodes edges]} (load-seed)
        a (datom-emit/emit nodes edges (analyze/analyze nodes edges) 1)
        {nodes2 :nodes edges2 :edges} (load-seed)
        b (datom-emit/emit nodes2 edges2 (analyze/analyze nodes2 edges2) 1)]
    (is (= a b) "Datom emit is not deterministic")))
