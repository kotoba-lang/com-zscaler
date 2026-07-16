(ns tanemaki.methods.test-datom-emit
  "tanemaki 種蒔き — Datom-emit tests (ADR-2606122001).
  1:1 Clojure port of the two `datom_emit`-dependent tests deferred out of test_analyze.py
  (test_datom_emit_ground_and_transient + test_determinism), the inochi/rasen precedent.

    - the Datom log emits ground datoms (incl. the PUBLIC :screened DD trail + disclosed rubric
      weights) and flags every derived readout :derived / :bond/is-transient (G3/G4)
    - emit is deterministic (repeat-emit on a re-loaded seed is byte-identical)"
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tanemaki.methods.analyze :as analyze]
            [tanemaki.methods.datom-emit :as datom-emit]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-stewardship-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        out (datom-emit/emit nodes edges res 7)]
    (is (str/includes? out " 7 :add]"))
    (is (str/includes? out ":criterion/weight"))
    (is (str/includes? out ":en/finding")
        "the public :screened DD trail must be in the Datom log")
    (is (str/includes? out ":bond/is-transient true"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]") (str "bond line not flagged transient: " line))))))

(deftest test-determinism
  (let [{:keys [nodes edges]} (load-seed)
        a (datom-emit/emit nodes edges (analyze/analyze nodes edges) 1)
        {nodes2 :nodes edges2 :edges} (load-seed)
        b (datom-emit/emit nodes2 edges2 (analyze/analyze nodes2 edges2) 1)]
    (is (= a b))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'tanemaki.methods.test-datom-emit)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
