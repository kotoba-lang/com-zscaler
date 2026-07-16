(ns hinagata.methods.test-datom-emit
  "hinagata 雛形 — Datom-emit tests (ADR-2606111954), 1:1 port of the datom assertions in
  tests/test_analyze.py (test_datom_emit_ground_and_transient + test_determinism) PLUS a
  byte-parity test (the keizu/shionome pattern): emit from the seed in Clojure and assert the
  bytes equal what `methods/datom_emit.py` generates on the same seed at the same tx."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.datom-emit :as datom-emit]))

(def ^:private actor-dir
  (-> (io/file *file*) .getParentFile .getParentFile))

(def ^:private seed
  (str (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn")))

(defn- load-seed [] (analyze/load-file* seed))

;; ── ported from test_analyze.py :: test_datom_emit_ground_and_transient ──────
(deftest test-datom-emit-ground-and-transient
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        out (datom-emit/emit nodes edges res 7)]
    (is (str/includes? out ":add]") "no ground :add datoms emitted")
    (is (str/includes? out ":template/title") "node attribute datoms missing")
    (is (str/includes? out ":en/binding-load") "edge attribute datoms missing")
    (is (str/includes? out ":mandated-by") "statute-binding edge datoms missing")
    ;; derived readouts must be flagged transient, NOT persisted as :add
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/groundedness"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]")
            (str "derived readout not flagged transient: " line))))
    (is (str/includes? out " 7 :add]"))))

;; ── ported from test_analyze.py :: test_determinism ──────────────────────────
(deftest test-determinism
  (let [{:keys [nodes edges]} (load-seed)
        a (datom-emit/emit nodes edges (analyze/analyze nodes edges) 1)
        {nodes2 :nodes edges2 :edges} (load-seed)
        b (datom-emit/emit nodes2 edges2 (analyze/analyze nodes2 edges2) 1)]
    (is (= a b) "Datom emit is not deterministic")))

;; ── byte-parity vs python3 (keizu/shionome +1 parity-test pattern) ───────────
(defn- python3-emit
  "Run methods/datom_emit.py on the seed at tx → the generated EDN bytes (or nil if python3
  is unavailable). The Python emitter writes to <actor>/out/legal-template-datoms.kotoba.edn."
  [tx]
  (let [py (io/file actor-dir "methods" "datom_emit.py")
        outdir (io/file (System/getProperty "java.io.tmpdir")
                        (str "hinagata-pyparity-tx" tx))]
    (try
      (let [{:keys [exit]}
            (let [pb (doto (ProcessBuilder.
                            ^java.util.List (vec ["python3" (str py) seed
                                                  "--out" (str outdir) "--tx" (str tx)]))
                       (.redirectErrorStream true))
                  proc (.start pb)
                  _ (slurp (.getInputStream proc))
                  code (.waitFor proc)]
              {:exit code})]
        (when (zero? exit)
          (let [f (io/file outdir "legal-template-datoms.kotoba.edn")]
            (when (.exists f) (slurp f)))))
      (catch Exception _ nil))))

(deftest test-byte-parity-with-python
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (doseq [tx [1 42]]
      (let [clj (datom-emit/emit nodes edges res tx)
            py (python3-emit tx)]
        (if py
          (is (= py clj) (str "Clojure emit must be byte-identical to python3 at tx=" tx))
          ;; python3 unavailable → fall back to a pinned structural invariant so the suite
          ;; never silently no-ops: ground :add + derived :derived datoms present, EDN-wrapped.
          (is (and (str/starts-with? clj ";; hinagata 雛形")
                   (str/includes? clj "[\n")
                   (str/includes? clj (str " " tx " :add]"))
                   (str/includes? clj (str " " tx " :derived]"))
                   (str/ends-with? clj "]\n"))
              "python3 unavailable; structural fallback failed"))))))
