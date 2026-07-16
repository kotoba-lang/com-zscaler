(ns fuchi.cells.test-state-machine
  "State-machine tests for 扶持 (fuchi) cells (R0). 1:1 port of cells/test_state_machines.py
  (ADR-2606052300). covenant_intake (G4/G5/G9) · need_assessment (G2/G3) · allocation_compute
  (G1/G2/G5) · routing_dispatch (G3) · governance_gate (G7); .solve() raises at R0."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [fuchi.cells.covenant-intake.state-machine :as ci]
            [fuchi.cells.need-assessment.state-machine :as na]
            [fuchi.cells.allocation-compute.state-machine :as ac]
            [fuchi.cells.routing-dispatch.state-machine :as rd]
            [fuchi.cells.governance-gate.state-machine :as gg]))

;; ── covenant_intake ──
(defn- intake [& {:as over}]
  (ci/transition-to-screened (merge {"cell_state" {} "did" "did:m:abel" "covenant" "vowed"
                                     "tenure_months" 96 "hazard_permille" 1800
                                     "owns_payoff" false "server_held_key" false} over)))

(deftest test-intake-screens-and-records
  (let [cs (get (intake) "cell_state")]
    (is (= "screened" (get cs "phase")))
    (let [cs2 (get (ci/transition-to-recorded {"cell_state" cs}) "cell_state")]
      (is (= "recorded" (get cs2 "phase")))
      (is (= false (get-in cs2 ["payload" "ownsPayoff"])))
      (is (= false (get-in cs2 ["payload" "serverHeldKey"]))))))

(deftest test-intake-refuses-bad-covenant
  (let [cs (get (intake "covenant" "anon") "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "G4"))))
(deftest test-intake-refuses-owns-payoff
  (let [cs (get (intake "owns_payoff" true) "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "G5"))))
(deftest test-intake-refuses-server-key
  (let [cs (get (intake "server_held_key" true) "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "no-server-key"))))
(deftest test-intake-cannot-record-without-screen
  (is (= "refused" (get-in (ci/transition-to-recorded {"cell_state" {}}) ["cell_state" "phase"]))))

;; ── need_assessment ──
(deftest test-assess-builds-envelope
  (let [cs (get (na/transition-to-assessed {"cell_state" {} "did" "did:m:abel" "lines"
                  [{"line" "food" "imputed_usd_micros_yr" 4000000000 "cash_usd_micros" 0}
                   {"line" "energy" "imputed_usd_micros_yr" 1000000000 "cash_usd_micros" 0}]}) "cell_state")]
    (is (= "assessed" (get cs "phase")))
    (is (= 5000000000 (get cs "imputed_total")))
    (is (every? #(= 0 (get % "cashUsdMicros")) (get cs "payload")))))
(deftest test-assess-refuses-cash-line
  (let [cs (get (na/transition-to-assessed {"cell_state" {} "did" "d" "lines" [{"line" "cash" "imputed_usd_micros_yr" 1 "cash_usd_micros" 0}]}) "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "G3"))))
(deftest test-assess-refuses-nonzero-cash
  (let [cs (get (na/transition-to-assessed {"cell_state" {} "did" "d" "lines" [{"line" "food" "imputed_usd_micros_yr" 1 "cash_usd_micros" 9}]}) "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "cash≡0"))))

;; ── allocation_compute ──
(defn- compute* [& {:as over}]
  (ac/transition-to-computed (merge {"cell_state" {} "did" "did:m:abel" "instrument" "sustenance"
                                     "tenure_months" 96 "hazard_permille" 1800 "owns_payoff" false} over)))
(deftest test-compute-produces-weight-and-zero-cash
  (let [cs (get (compute*) "cell_state")]
    (is (= "computed" (get cs "phase")))
    (is (> (get cs "weight") 0))
    (is (= 0 (get-in cs ["payload" "cashUsdMicros"])))
    (is (= false (get-in cs ["payload" "serverHeldKey"])))))
(deftest test-compute-refuses-equity-instrument
  (let [cs (get (compute* "instrument" "equity") "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "G1"))))
(deftest test-compute-refuses-carry-and-dividend
  (doseq [bad ["carry" "dividend" "revenue-share" "exit"]]
    (let [cs (get (compute* "instrument" bad) "cell_state")]
      (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "G1")))))
(deftest test-compute-refuses-owns-payoff
  (let [cs (get (compute* "owns_payoff" true) "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "G5"))))

;; ── routing_dispatch ──
(deftest test-route-decomposes-to-rails
  (let [cs (get (rd/transition-to-routed {"cell_state" {} "did" "a" "lines"
                  [{"line" "food" "imputed_usd_micros_yr" 5 "cash_usd_micros" 0}
                   {"line" "liquidity" "imputed_usd_micros_yr" 5 "cash_usd_micros" 0}]}) "cell_state")
        by (into {} (map (fn [r] [(get r "kind") r]) (get cs "rails")))]
    (is (= "routed" (get cs "phase")))
    (is (= false (get-in by ["food-mitsuho" "memberPrincipal"])))
    (is (= true (get-in by ["liquidity-warifu" "memberPrincipal"])))
    (is (= 0.5 (get cs "in_kind_coverage")))))
(deftest test-route-refuses-cash-rail
  (let [cs (get (rd/transition-to-routed {"cell_state" {} "did" "a" "lines" [{"line" "food" "imputed_usd_micros_yr" 1 "cash_usd_micros" 9}]}) "cell_state")]
    (is (= "refused" (get cs "phase"))) (is (str/includes? (get cs "refusal") "cash≡0"))))

;; ── governance_gate ──
(deftest test-gov-auto-for-low-in-kind
  (let [cs (get (gg/transition-to-routed {"cell_state" {} "alloc_id" "a" "imputed_total" 8000000000 "context" "food energy sustenance"}) "cell_state")]
    (is (= "auto" (get cs "route")))
    (is (= "accepted" (get-in (gg/transition-to-decided {"cell_state" cs}) ["cell_state" "outcome"])))))
(deftest test-gov-vote-above-ceiling
  (let [cs (get (gg/transition-to-routed {"cell_state" {} "alloc_id" "a" "imputed_total" 28000000000 "context" "remote-robotics teleop"}) "cell_state")]
    (is (= "sbt-vote" (get cs "route")))
    (is (= "accepted" (get-in (gg/transition-to-decided {"cell_state" cs "yes" 11 "no" 2}) ["cell_state" "outcome"])))))
(deftest test-gov-council-for-invariant-touch
  (let [cs (get (gg/transition-to-routed {"cell_state" {} "alloc_id" "a" "imputed_total" 1 "context" "new commons-land grant for housing"}) "cell_state")]
    (is (= "council-lv7" (get cs "route")))
    (is (= "pending" (get-in (gg/transition-to-decided {"cell_state" cs}) ["cell_state" "outcome"])))))
(deftest test-gov-refused-for-rider-hit
  (let [cs (get (gg/transition-to-routed {"cell_state" {} "alloc_id" "a" "imputed_total" 1 "context" "requests an affiliate ad-revenue share"}) "cell_state")]
    (is (= "refused" (get cs "route")))
    (is (= "refused" (get-in (gg/transition-to-decided {"cell_state" cs}) ["cell_state" "outcome"])))))

;; ── all .solve() raise at R0 ──
(deftest test-all-cells-solve-raises
  (doseq [solve [ci/solve na/solve ac/solve rd/solve gg/solve]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (solve {})))))
