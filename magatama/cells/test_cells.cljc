(ns magatama.cells.test-cells
  "clojure.test tests for ported magatama shionome + suimin cells.
  Run via: bb --classpath 20-actors -e \"(require 'magatama.cells.test-cells)\""
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [magatama.cells.shionome-core :as sc]
            [magatama.cells.shionome-flow-graph.state-machine :as fg]
            [magatama.cells.shionome-ingest.state-machine :as ing]
            [magatama.cells.shionome-regime-observer.state-machine :as ro]
            [magatama.cells.shionome-rotation-weave.state-machine :as rw]
            [magatama.cells.shionome-social-post.state-machine :as sp]
            [magatama.cells.suimin-disclaimer-gate.state-machine :as sdg]
            [magatama.cells.suimin-evidence-grade.state-machine :as seg]
            [magatama.cells.suimin-referral-router.state-machine :as srr]
            [magatama.cells.suimin-source-ingest.state-machine :as ssi]
            [magatama.cells.suimin-treatment-synthesize.state-machine :as sts]))

;; ── Fixtures ─────────────────────────────────────────────────────────────────────

(def flows
  [{:kind "rotation" :source "us-govt-bonds" :target "us-equities" :magnitude 18.0 :sources ["a" "b"]}
   {:kind "fund-inflow" :source "external" :target "us-equities" :magnitude 4.0 :sources ["a" "b"]}
   {:kind "cross-correlation" :source "us-equities" :target "tech" :magnitude 0.9 :sources ["a" "b"]}])

(def risk-tags {"us-equities" "risk" "us-govt-bonds" "safe"})

;; ── shionome-core tests ───────────────────────────────────────────────────────────

(deftest test-screen-flows-passes-valid
  (testing "screen-flows returns valid flows unchanged"
    (is (= (sc/screen-flows flows) flows))))

(deftest test-screen-flows-refuses-trade-token
  (testing "screen-flows refuses a buy kind"
    (is (thrown-with-msg? Exception #"トレードはしない"
          (sc/screen-flows [{:kind "buy" :sources ["a" "b"]}])))))

(deftest test-screen-flows-refuses-unknown-kind
  (testing "screen-flows refuses unknown kind"
    (is (thrown-with-msg? Exception #"G2"
          (sc/screen-flows [{:kind "short" :sources ["a" "b"]}])))))

(deftest test-screen-flows-refuses-undersourced
  (testing "screen-flows refuses flow with < 2 sources"
    (is (thrown-with-msg? Exception #"G3"
          (sc/screen-flows [{:kind "rotation" :sources ["a"]}])))))

(deftest test-trade-token-in
  (testing "trade-token-in detects buy"
    (is (= (sc/trade-token-in "buy the dip") "buy")))
  (testing "trade-token-in detects Japanese 買い"
    (is (= (sc/trade-token-in "今日は買いだ") "買い")))
  (testing "trade-token-in returns empty string on clean text"
    (is (= (sc/trade-token-in "capital-flow observation: risk-on") ""))))

(deftest test-net-flow-correct-values
  (testing "net-flow correct per-bucket values"
    (let [net (into {} (map (fn [r] [(get r "bucket") (get r "net")]) (sc/net-flow flows)))]
      (is (= (get net "us-equities") 22.0))    ; 18 rotation in + 4 inflow
      (is (= (get net "us-govt-bonds") -18.0))
      (is (nil? (get net "tech"))))))           ; cross-correlation excluded

(deftest test-net-flow-excludes-external
  (testing "net-flow does not create a bucket for 'external'"
    (let [result (sc/net-flow flows)
          buckets (set (map #(get % "bucket") result))]
      (is (not (contains? buckets "external"))))))

(deftest test-top-rotation
  (testing "top-rotation returns largest bucket→bucket pair"
    (let [r (sc/top-rotation flows)]
      (is (= (get r "from") "us-govt-bonds"))
      (is (= (get r "to") "us-equities"))
      (is (= (get r "magnitude") 18.0)))))

(deftest test-top-rotation-nil-on-empty
  (testing "top-rotation returns nil when no capital-movement flows"
    (is (nil? (sc/top-rotation [])))))

(deftest test-regime-risk-on
  (testing "regime returns risk-on when risk bucket net > 0 and safe <= 0"
    (let [net (sc/net-flow flows)
          reg (sc/regime net risk-tags)]
      (is (= (get reg "regime") "risk-on"))
      (is (= (get reg "no_trade_notice") true)))))

(deftest test-regime-indeterminate
  (testing "regime returns indeterminate when risk-net=0 and safe-net=0"
    (let [reg (sc/regime [] {})]
      (is (= (get reg "regime") "indeterminate")))))

(deftest test-draft-dry-run-post-ok
  (testing "draft-dry-run-post returns valid dry-run envelope"
    (let [p (sc/draft-dry-run-post "クロスアセット観測: risk-on（記述）" ["a" "b"])]
      (is (= (get p "status") "dry-run"))
      (is (= (get p "is_mirror") true))
      (is (= (get p "no_trade_notice") true))
      (is (= (get p "server_held_key") false))
      (is (clojure.string/includes? (get p "body") "トレードはしない")))))

(deftest test-draft-dry-run-post-refuses-trade-body
  (testing "draft-dry-run-post refuses trade token in body"
    (is (thrown-with-msg? Exception #"G2"
          (sc/draft-dry-run-post "buy signal: risk-on" ["a" "b"])))))

(deftest test-draft-dry-run-post-refuses-undersourced
  (testing "draft-dry-run-post refuses < 2 sources"
    (is (thrown-with-msg? Exception #"G3"
          (sc/draft-dry-run-post "clean body" ["a"])))))

;; ── Cell state-machine tests ─────────────────────────────────────────────────────

(deftest test-flow-graph-run-chain
  (testing "flow-graph run-chain computes net flows"
    (let [result (fg/run-chain {:flows flows})]
      (is (seq (:net result))))))

(deftest test-ingest-run-chain-valid
  (testing "ingest run-chain passes valid batch"
    (let [result (ing/run-chain {:context {:market_batch flows}})]
      (is (= (:refusal result) ""))
      (is (seq (:flows result))))))

(deftest test-ingest-run-chain-refuses-trade-token
  (testing "ingest run-chain refuses trade-token batch"
    (let [result (ing/run-chain {:context {:market_batch [{:kind "buy" :sources ["a" "b"]}]}})]
      (is (clojure.string/includes? (:refusal result) "G2"))
      (is (empty? (:flows result))))))

(deftest test-regime-observer-run-chain
  (testing "regime observer run-chain produces regime map"
    (let [state  {:net [{:bucket "us-equities" :net 22.0} {:bucket "us-govt-bonds" :net -18.0}]
                  :context {"risk_tags" {"us-equities" "risk" "us-govt-bonds" "safe"}}}
          result (ro/run-chain state)]
      (is (map? (:regime result))))))

(deftest test-rotation-weave-run-chain
  (testing "rotation-weave run-chain finds top rotation"
    (let [result (rw/run-chain {:flows flows})]
      (is (= (get (:rotation result) "from") "us-govt-bonds")))))

(deftest test-rotation-weave-empty-flows
  (testing "rotation-weave run-chain returns empty map on empty flows"
    (let [result (rw/run-chain {:flows []})]
      (is (= (:rotation result) {})))))

(deftest test-social-post-run-chain-ok
  (testing "social-post run-chain returns dry-run post"
    (let [result (sp/run-chain {:regime {"regime" "risk-on" "risk_net" 22.0 "safe_net" -18.0}
                                :context {:sources ["a" "b"]}})]
      (is (= (get-in result [:post "status"]) "dry-run"))
      (is (= (:refusal result) "")))))

(deftest test-social-post-run-chain-refusal-on-undersourced
  (testing "social-post run-chain captures refusal on < 2 sources"
    (let [result (sp/run-chain {:regime {"regime" "risk-on" "risk_net" 1.0 "safe_net" 0.0}
                                :context {:sources ["a"]}})]
      (is (clojure.string/includes? (:refusal result) "G3"))
      (is (= (:post result) {})))))

;; ── Suimin Council gate tests ─────────────────────────────────────────────────────

(deftest test-suimin-disclaimer-gate-council-gate
  (testing "disclaimer-gate run-chain throws scaffold-only until Council activated"
    (is (thrown-with-msg? Exception #"scaffold-only"
          (sdg/run-chain {})))
    (is (= :council-activation
           (get (try (sdg/run-chain {})
                     (catch Exception e (ex-data e)))
                :gate)))))

(deftest test-suimin-evidence-grade-council-gate
  (testing "evidence-grade run-chain throws scaffold-only until Council activated"
    (is (thrown-with-msg? Exception #"scaffold-only"
          (seg/run-chain {})))))

(deftest test-suimin-referral-router-council-gate
  (testing "referral-router run-chain throws scaffold-only until Council activated"
    (is (thrown-with-msg? Exception #"scaffold-only"
          (srr/run-chain {})))))

(deftest test-suimin-source-ingest-council-gate
  (testing "source-ingest run-chain throws scaffold-only until Council activated"
    (is (thrown-with-msg? Exception #"scaffold-only"
          (ssi/run-chain {})))))

(deftest test-suimin-treatment-synthesize-council-gate
  (testing "treatment-synthesize run-chain throws scaffold-only until Council activated"
    (is (thrown-with-msg? Exception #"scaffold-only"
          (sts/run-chain {})))))

;; ── Runner ────────────────────────────────────────────────────────────────────────

(defn -main [& _args]
  (let [{:keys [fail error]} (run-tests 'magatama.cells.test-cells)]
    (when (pos? (+ fail error))
      (System/exit 1))))
