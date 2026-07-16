(ns tokigusuri.methods.test-datom-emit
  "tokigusuri 時薬 — Datom-emit tests (ADR-2606171300). Sibling of hokorobi
  methods/test_datom_emit — ground/transient flagging + determinism."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [tokigusuri.methods.analyze :as analyze]
            [tokigusuri.methods.datom-emit :as de]))

(def seed
  (str (-> *file* clojure.java.io/file .getParentFile .getParentFile)
       "/data/seed-pharma-patent-graph.kotoba.edn"))

(defn- load-seed [] (analyze/load-file* seed))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        out (de/emit nodes edges res 7)]
    (is (str/includes? out ":add]") "no ground :add datoms emitted")
    (is (str/includes? out ":drug/essentiality") "node attribute datoms missing")
    (is (str/includes? out ":en/barrier-load") "edge attribute datoms missing")
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/access-barrier-concentration"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]")
            (str "derived readout not flagged transient: " line))))
    (is (str/includes? out " 7 :add]"))))

(deftest test-determinism
  (let [{:keys [nodes edges]} (load-seed)
        a (de/emit nodes edges (analyze/analyze nodes edges) 1)
        {nodes2 :nodes edges2 :edges} (load-seed)
        b (de/emit nodes2 edges2 (analyze/analyze nodes2 edges2) 1)]
    (is (= a b) "Datom emit is not deterministic")))
