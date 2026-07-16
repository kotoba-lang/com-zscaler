(ns kamado.cells.decommission-plan.test-state-machine
  "Tests for the kamado decommission_plan state machine (ADR-2606051500 port). Drives init → scoped
  → planned → gated and pins every gate: G3 intervention allow-list (keyword-style norm + fossil
  life-extension refused), unknown convert target refused, phase ordering (plan-before-scope /
  gate-before-plan), G5 principal allow-list + serverHeldKey-false, and the G8 intent-only gated
  payload."
  (:require [clojure.test :refer [deftest is]]
            [kamado.cells.decommission-plan.state-machine :as sm]))

(deftest test-full-progression
  (let [s0 {"cell_state" {} "refinery" "ref-7" "intervention" ":decommission" "convert_to" ":hikari-solar"}
        s1 (sm/transition-to-scoped s0)
        s2 (sm/transition-to-planned (merge s1 {"principal" ":member"}))
        s3 (sm/transition-to-gated s2)]
    (is (= "scoped" (get-in s1 ["cell_state" "phase"])))
    (is (= "decommission" (get-in s1 ["cell_state" "intervention"])))   ; norm stripped the ':'
    (is (= "hikari-solar" (get-in s1 ["cell_state" "convert_to"])))
    (is (= "planned" (get-in s2 ["cell_state" "phase"])))
    (is (= "member" (get-in s2 ["cell_state" "principal"])))
    (is (= false (get-in s2 ["cell_state" "payload" "serverHeldKey"])))
    (is (= "ref-7" (get-in s2 ["cell_state" "payload" "refinery"])))
    (is (= "gated" (get-in s3 ["cell_state" "phase"])))
    (is (= "intent-only" (get-in s3 ["cell_state" "payload" "status"])))
    (is (= true (get-in s3 ["cell_state" "payload" "outwardGated"])))))

(deftest test-g3-fossil-life-extension-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3 violation"
                        (sm/transition-to-scoped {"cell_state" {} "intervention" ":expand"})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3 violation"
                        (sm/transition-to-scoped {"cell_state" {} "intervention" "restart-fossil"})))
  ;; all four permitted interventions scope cleanly
  (doseq [iv ["decommission" "remediate" "convert" "monitor"]]
    (is (= "scoped" (get-in (sm/transition-to-scoped {"cell_state" {} "intervention" iv}) ["cell_state" "phase"])) iv)))

(deftest test-unknown-convert-target-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown convert-to target"
                        (sm/transition-to-scoped {"cell_state" {} "intervention" "convert" "convert_to" "fossil-restart"}))))

(deftest test-phase-ordering
  ;; plan before scope
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires a scoped intervention first"
                        (sm/transition-to-planned {"cell_state" {}})))
  ;; gate before plan
  (let [scoped (sm/transition-to-scoped {"cell_state" {} "intervention" "decommission"})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires a planned intervention first"
                          (sm/transition-to-gated scoped)))))

(deftest test-g5-gates
  (let [scoped (sm/transition-to-scoped {"cell_state" {} "intervention" "decommission"})]
    ;; bad principal
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"must be operator\|member"
                          (sm/transition-to-planned (merge scoped {"principal" "robot"}))))
    ;; serverHeldKey true → refused
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"serverHeldKey must be false"
                          (sm/transition-to-planned (merge scoped {"principal" "operator" "server_held_key" true}))))))
