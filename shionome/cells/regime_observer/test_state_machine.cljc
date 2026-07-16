(ns shionome.cells.regime-observer.test-state-machine
  "clojure.test port of the regime_observer assertions from
  `cells/test_state_machines.py` (ADR-2606072200).

  Ports ONLY the two regime_observer cases (test_regime_observer_risk_on +
  test_regime_observer_indeterminate). The Python file also covers OTHER cells'
  state machines — those are deferred (see the deferral note returned to the
  caller): ingest (5), flow_graph (1), rotation_weave (1), social_post (3), and
  test_all_cells_solve_raise (R0 .solve() G8 raise across all 5 cells). Those
  exercise sibling cells' `state_machine.py`/`cell.py` that have not been ported
  to .cljc yet."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [shionome.cells.regime-observer.state-machine :as sm]))

;; ── regime_observer ──────────────────────────────────────────────

(deftest test-regime-observer-risk-on
  (let [st (sm/transition-to-observed
            {:cell-state {}
             :net {"eq" 20.0 "bonds" -18.0}
             :risk-tag {"eq" "risk" "bonds" "safe"}})
        cs (:cell-state st)]
    (is (= "risk-on" (:regime cs)))
    (is (true? (:no-trade-notice cs)))
    (testing "phase advances to observed"
      (is (= "observed" (:phase cs))))))

(deftest test-regime-observer-indeterminate
  (let [st (sm/transition-to-observed {:cell-state {} :net {} :risk-tag {}})]
    (is (= "indeterminate" (:regime (:cell-state st))))))

;; ── extra coverage of the ported branch ladder (parity guards) ───

(deftest test-regime-observer-risk-off
  (let [st (sm/transition-to-observed
            {:cell-state {}
             :net {"eq" -20.0 "bonds" 18.0}
             :risk-tag {"eq" "risk" "bonds" "safe"}})]
    (is (= "risk-off" (:regime (:cell-state st))))))

(deftest test-regime-observer-mixed
  ;; risk_net > 0 AND safe_net > 0 → falls through to "mixed"
  (let [st (sm/transition-to-observed
            {:cell-state {}
             :net {"eq" 20.0 "bonds" 18.0}
             :risk-tag {"eq" "risk" "bonds" "safe"}})]
    (is (= "mixed" (:regime (:cell-state st))))))

(deftest test-net-rounding-and-defaults
  (let [st (sm/transition-to-observed
            {:cell-state {}
             :net {"eq" 20.123456}
             :risk-tag {"eq" "risk"}})
        cs (:cell-state st)]
    (is (= 20.1235 (:risk-net cs)))
    (is (= 0.0 (:safe-net cs)))
    (is (true? (:no-trade-notice cs)))))

(deftest test-closed-regime-state-surface
  (testing "an unexpected cell-state field raises (RegimeState(**...) parity)"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/transition-to-observed
                  {:cell-state {:bogus 1} :net {} :risk-tag {}})))))

#?(:clj
   (defn -main [& _]
     (run-tests 'shionome.cells.regime-observer.test-state-machine)))
