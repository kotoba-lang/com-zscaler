(ns kanayama.methods.test-agent
  "kanayama 金 — agent gate tests. 1:1 port of py/test_agent.py (custom harness → clojure.test).
  Offline: mass-balance audit (G2), KPI caps (G12), witness quorum (G4), USDC + tithe settlement
  (G7/G11/G15)."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.methods.agent :as agent]))

(defn- blocked? [r] (= true (get r "blocked")))

(deftest test-intake-qa-pass (is (= true (get (agent/intake-qa 500.0 0.8 2.1 45) ":intake/qaPassed"))))
(deftest test-intake-qa-fail-cl-residue (is (= true (get (agent/intake-qa 500.0 3.0 2.0 45) ":intake/blocked"))))
(deftest test-intake-qa-fail-moisture (is (= true (get (agent/intake-qa 500.0 0.8 6.0 45) ":intake/blocked"))))
(deftest test-intake-qa-fail-fe-contamination (is (= true (get (agent/intake-qa 500.0 0.8 2.0 600) ":intake/blocked"))))

(deftest test-mass-balance-ok
  (let [out (agent/check-mass-balance 500.0 475.0 20.0 5.0)]
    (is (and (= true (get out "ok")) (>= (get out "closure_pct") 98.0)))))

(deftest test-mass-balance-fail-low-closure
  (let [out (agent/check-mass-balance 500.0 300.0 50.0 50.0)]
    (is (and (= false (get out "ok")) (< (get out "closure_pct") 98.0)))))

(deftest test-mass-balance-invalid-input
  (is (= false (get (agent/check-mass-balance 0.0 1.0 1.0 1.0) "ok"))))

(deftest test-kpi-recovery-below-min (is (= false (get (agent/check-kpi-caps 90.0 5.0) "ok"))))
(deftest test-kpi-energy-above-cap (is (= false (get (agent/check-kpi-caps 96.0 7.0) "ok"))))
(deftest test-kpi-both-ok (is (= true (get (agent/check-kpi-caps 96.0 5.5) "ok"))))

(deftest test-witness-quorum-ok
  (let [out (agent/check-witness-sigs ["did:web:robot.yokin" "did:web:robot.kamado"])]
    (is (and (= true (get out "ok")) (= 2 (get out "witness_count"))))))

(deftest test-witness-quorum-fail-one (is (= false (get (agent/check-witness-sigs ["did:web:robot.yokin"]) "ok"))))
(deftest test-witness-quorum-fail-empty (is (= false (get (agent/check-witness-sigs []) "ok"))))

(deftest test-settlement-tithe-split
  (let [s (agent/build-settlement-intent 1000000000)]
    (is (= 100000000 (get s "titheMinor")))
    (is (= 900000000 (get s "operatorPayoutMinor")))
    (is (= "intent" (get s "state")))
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-only-with-sig
  (is (= "executed" (get (agent/build-settlement-intent 500000000 "0xoperatorsig") "state"))))

(deftest test-batch-settlement-ok
  (let [out (agent/finalize-batch-settlement "b1" 500.0 475.0 20.0 5.0 96.0 5.5)]
    (is (not (blocked? out)))
    (is (contains? out ":batchSettlement/id"))))

(deftest test-batch-settlement-blocked-mass-balance
  (is (blocked? (agent/finalize-batch-settlement "b2" 500.0 300.0 50.0 50.0 96.0 5.5))))

(deftest test-batch-settlement-blocked-kpi
  (is (blocked? (agent/finalize-batch-settlement "b3" 500.0 475.0 20.0 5.0 90.0 5.5))))
