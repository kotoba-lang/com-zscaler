(ns shionome.methods.test-analyze
  "test_analyze.cljc — 潮目 (shionome) end-to-end membrane + empty-graph path. ADR-2606072201.
  1:1 Clojure port of `methods/test_analyze.py` (clojure.test). Every Python assertion ported.
  Since the Clojure `run` is pure over a parsed graph (I/O at the #?(:clj) -main edge), the report
  is exercised via `report-md` / `render-json` rather than a temp-dir write — same bytes."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [shionome.methods.analyze :as a]
            #?(:clj [shionome.methods.edn :as e])))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")

;; the empty seed — exercises the "(none in seed)" fallbacks + empty posts.
(def empty-seed
  "{:graph {:name \"empty\"} :buckets [] :flows [] :snapshots []}")

#?(:clj
   (defn- run-seed []
     (let [res (a/run (e/load-edn seed-path))
           c (get res "concentration")]
       {:res res
        :report (a/report-md c (get res "posts"))
        :graph (a/graph-edn (get res "graph"))})))

;; ── test_run_produces_outputs ─────────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-run-produces-outputs
     (let [{:keys [res report graph]} (run-seed)]
       ;; the report / graph-edn / render-json are all produced (the #?(:clj) -main writes them)
       (is (str/includes? report "shionome"))
       (is (str/includes? graph ":flow/id"))
       (is (str/includes? (a/render-json (get res "concentration")) "shionome"))
       (is (= "risk-on" (get-in res ["concentration" "regime" "regime"])))
       (is (= 3 (count (get res "posts")))))))

;; ── test_report_has_no_trade_disclaimer ───────────────────────────────────────────────────────
#?(:clj
   (deftest test-report-has-no-trade-disclaimer
     (let [{:keys [report]} (run-seed)]
       (is (str/includes? report "トレードはしない"))
       (is (str/includes? report "risk-on")))))

;; ── test_empty_graph_path ─────────────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-empty-graph-path
     (let [res (a/run (e/parse-edn empty-seed))
           c (get res "concentration")
           report (a/report-md c (get res "posts"))]
       (is (= 0 (get c "bucket_count")))
       (is (= [] (get res "posts")))
       (is (str/includes? report "(none in seed)")))))

;; ── test_kanae_flows_skip_count ───────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-kanae-flows-skip-count
     (let [res (a/run (e/load-edn seed-path))]
       (is (= 3 (get-in res ["kanae_flows" "skipped_count"]))))))

#?(:clj (defn -main [& _] (run-tests 'shionome.methods.test-analyze)))
