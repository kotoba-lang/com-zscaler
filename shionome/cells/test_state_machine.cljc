(ns shionome.cells.test-state-machine
  "潮目 (shionome) cell state machines + R0 .solve() raise. 1:1 port of cells/test_state_machines.py
  (ADR-2606072200), for the 4 string-keyed cells (ingest/flow_graph/rotation_weave/social_post) +
  the all-cells solve-raise. regime_observer unit tests live in regime_observer/test_state_machine.cljc."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [shionome.cells.ingest.state-machine :as ing]
            [shionome.cells.flow-graph.state-machine :as fg]
            [shionome.cells.rotation-weave.state-machine :as rw]
            [shionome.cells.social-post.state-machine :as sp]
            [shionome.cells.regime-observer.state-machine :as ro]))

;; ── ingest ──
(deftest test-ingest-clean-batch-records
  (let [st (ing/transition-to-screened {"cell_state" {} "buckets" [{"scope" ":asset-class"}]
                                        "flows" [{"kind" ":rotation" "sources" ["a" "b"]}] "snapshots" []})]
    (is (= "screened" (get-in st ["cell_state" "phase"])))
    (let [st2 (ing/transition-to-recorded st)]
      (is (= "recorded" (get-in st2 ["cell_state" "phase"])))
      (is (= 2 (get-in st2 ["cell_state" "recorded"]))))))

(deftest test-ingest-refuses-person-bucket
  (let [st (ing/transition-to-screened {"cell_state" {} "buckets" [{"scope" ":individual"}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G1"))))

(deftest test-ingest-refuses-trade-token-flow-kind
  (let [st (ing/transition-to-screened {"cell_state" {} "flows" [{"kind" ":buy" "sources" ["a" "b"]}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G2"))))

(deftest test-ingest-refuses-undersourced-flow
  (let [st (ing/transition-to-screened {"cell_state" {} "flows" [{"kind" ":rotation" "sources" ["a"]}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G3"))))

(deftest test-ingest-cannot-record-unscreened
  (let [st (ing/transition-to-recorded {"cell_state" {"phase" "init"}})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))))

;; ── flow_graph ──
(deftest test-flow-graph-indexes-net
  (let [st (fg/transition-to-indexed {"cell_state" {} "flows"
            [{"kind" ":rotation" "source" "bonds" "target" "eq" "magnitude" 10.0}
             {"kind" ":fund-inflow" "source" "external" "target" "eq" "magnitude" 5.0}
             {"kind" ":cross-correlation" "source" "eq" "target" "tech" "magnitude" 0.9}]})
        net (get-in st ["cell_state" "net"])]
    (is (= 15.0 (get net "eq")))
    (is (= -10.0 (get net "bonds")))
    (is (not (contains? net "tech")))))

;; ── rotation_weave ──
(deftest test-rotation-weave-ranks-pairs
  (let [st (rw/transition-to-woven {"cell_state" {} "flows"
            [{"kind" ":rotation" "source" "bonds" "target" "eq" "magnitude" 12.0}
             {"kind" ":rotation" "source" "cash" "target" "eq" "magnitude" 8.0}
             {"kind" ":cross-correlation" "source" "eq" "target" "tech" "magnitude" 0.9}]})
        pairs (get-in st ["cell_state" "pairs"])]
    (is (= ["bonds" "eq" 12.0] (first pairs)))
    (is (every? #(not (and (= (nth % 0) "eq") (= (nth % 1) "tech"))) pairs))))

;; ── regime_observer (keyword convention — the sibling port) ──
(deftest test-regime-observer-risk-on
  (let [st (ro/transition-to-observed {:cell-state {} :net {"eq" 20.0 "bonds" -18.0} :risk-tag {"eq" "risk" "bonds" "safe"}})
        cs (:cell-state st)]
    (is (= "risk-on" (:regime cs)))
    (is (= true (:no-trade-notice cs)))))

(deftest test-regime-observer-indeterminate
  (is (= "indeterminate" (-> (ro/transition-to-observed {:cell-state {} :net {} :risk-tag {}}) :cell-state :regime))))

;; ── social_post ──
(deftest test-social-post-drafts-dry-run
  (let [st (sp/transition-to-drafted {"cell_state" {} "body" "資金がTreasuriesからequitiesへ回転" "sources" ["a" "b"]})]
    (is (= "drafted" (get-in st ["cell_state" "phase"])))
    (is (= "dry-run" (get-in st ["cell_state" "status"])))))

(deftest test-social-post-refuses-trade-token-body
  (let [st (sp/transition-to-drafted {"cell_state" {} "body" "推奨: buy equities now" "sources" ["a" "b"]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G2"))))

(deftest test-social-post-refuses-undersourced
  (let [st (sp/transition-to-drafted {"cell_state" {} "body" "観測" "sources" ["a"]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))
    (is (str/includes? (get-in st ["cell_state" "refusal"]) "G3"))))

;; ── R0: every cell's .solve() raises (G8) ──
(deftest test-all-cells-solve-raise
  (doseq [solve [ing/solve fg/solve rw/solve ro/solve sp/solve]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0" (solve {})))))
