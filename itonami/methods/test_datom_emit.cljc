(ns itonami.methods.test-datom-emit
  "itonami 営み — Datom-emit tests (ADR-2606082300).
  1:1 Clojure port of the three `datom_emit`-dependent assertions deferred out of
  test_analyze.py (the inochi/rasen precedent):

    - G2 datoms-blob: the emitted Datom log leaks no :worker/* or :person/* dimension
      (the deferred half of test_no_worker_dimension_anywhere — per-worker monitoring is
      structurally unrepresentable)
    - the Datom log emits ground :add datoms (incl. scan-cycle ticks) + derived OEE, and
      flags every :ops/* KPI line :derived / :bond/is-transient (G3 — not a fact)
    - emit is deterministic (repeat-emit on a re-loaded seed is byte-identical)"
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [itonami.methods.analyze :as analyze]
            [itonami.methods.datom-emit :as datom-emit]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-factory-ops.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-no-worker-dimension-datoms
  ;; G2: per-worker monitoring is structurally unrepresentable. No :worker/* may appear in
  ;; the emitted Datom log (the deferred datoms-blob half of test_no_worker_dimension_anywhere).
  (let [{:keys [stations ticks]} (load-seed)
        res (analyze/analyze stations ticks)
        out (datom-emit/emit stations ticks res 3)]
    (is (and (not (str/includes? out ":worker")) (not (str/includes? out ":person")))
        "datoms leaked a person/worker dimension (G2 violation)")))

(deftest test-datom-emit-ground-and-transient
  (let [{:keys [stations ticks]} (load-seed)
        res (analyze/analyze stations ticks)
        out (datom-emit/emit stations ticks res 7)]
    (is (str/includes? out ":add]") "no ground :add datoms")
    (is (str/includes? out ":tick/state") "scan-cycle tick datoms missing")
    (is (str/includes? out ":ops/oee") "derived OEE datom missing")
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out " 7 :add]"))
    ;; every KPI/routing line MUST be flagged :derived (G3 — not a fact)
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":ops/"))
        (is (str/includes? line ":derived]") (str "KPI not flagged transient: " line))))))

(deftest test-determinism
  (let [{:keys [stations ticks]} (load-seed)
        a (datom-emit/emit stations ticks (analyze/analyze stations ticks) 1)
        {s2 :stations t2 :ticks} (load-seed)
        b (datom-emit/emit s2 t2 (analyze/analyze s2 t2) 1)]
    (is (= a b) "Datom emit is not deterministic")))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'itonami.methods.test-datom-emit)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
