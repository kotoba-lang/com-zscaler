(ns shionome.cells.test-membrane-flow
  "潮目 (shionome) cell-chain integration. 1:1 port of cells/test_membrane_flow.py (ADR-2606072200):
  ingest ─▶ flow_graph ─▶ rotation_weave ─▶ regime_observer ─▶ social_post (dry-run), threaded
  end-to-end. The regime that falls out of regime_observer becomes social_post's body."
  (:require [clojure.test :refer [deftest is]]
            [shionome.cells.ingest.state-machine :as ing]
            [shionome.cells.flow-graph.state-machine :as fg]
            [shionome.cells.rotation-weave.state-machine :as rw]
            [shionome.cells.regime-observer.state-machine :as ro]
            [shionome.cells.social-post.state-machine :as sp]))

(def buckets [{"scope" ":asset-class"} {"scope" ":asset-class"}])
(def flows [{"kind" ":rotation" "source" "bonds" "target" "eq" "magnitude" 18.0 "sources" ["a" "b"]}
            {"kind" ":fund-inflow" "source" "external" "target" "eq" "magnitude" 4.0 "sources" ["a" "b"]}])
(def risk-tag {"eq" "risk" "bonds" "safe"})

(deftest test-full-membrane-chain-reaches-dry-run-post
  ;; 1) ingest — screen + record
  (let [st (ing/transition-to-screened {"cell_state" {} "buckets" buckets "flows" flows})]
    (is (= "screened" (get-in st ["cell_state" "phase"])))
    (is (= "recorded" (get-in (ing/transition-to-recorded st) ["cell_state" "phase"])))
    ;; 2) flow_graph — net flow per bucket
    (let [g (fg/transition-to-indexed {"cell_state" {} "flows" flows})
          net (get-in g ["cell_state" "net"])]
      (is (= 22.0 (get net "eq"))) (is (= -18.0 (get net "bonds")))
      ;; 3) rotation_weave — top pair
      (is (= ["bonds" "eq" 18.0] (first (get-in (rw/transition-to-woven {"cell_state" {} "flows" flows}) ["cell_state" "pairs"]))))
      ;; 4) regime_observer — risk-on from net + tags (keyword convention)
      (let [regime (-> (ro/transition-to-observed {:cell-state {} :net net :risk-tag risk-tag}) :cell-state :regime)]
        (is (= "risk-on" regime))
        ;; 5) social_post — the regime becomes a dry-run post body (no trade token)
        (let [body (str "クロスアセット観測: " regime "（記述であり助言ではない）")
              p (sp/transition-to-drafted {"cell_state" {} "body" body "sources" ["a" "b"]})]
          (is (= "drafted" (get-in p ["cell_state" "phase"])))
          (is (= "dry-run" (get-in p ["cell_state" "status"]))))))))

(deftest test-membrane-refuses-trade-token-at-post-stage
  (let [p (sp/transition-to-drafted {"cell_state" {} "body" "buy signal: risk-on" "sources" ["a" "b"]})]
    (is (= "refused" (get-in p ["cell_state" "phase"])))))

(deftest test-membrane-refuses-person-bucket-at-ingest
  (let [st (ing/transition-to-screened {"cell_state" {} "buckets" [{"scope" ":portfolio"}]})]
    (is (= "refused" (get-in st ["cell_state" "phase"])))))
