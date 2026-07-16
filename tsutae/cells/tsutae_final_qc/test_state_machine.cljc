(ns tsutae.cells.tsutae-final-qc.test-state-machine
  "Tests for the tsutae final_qc state machine (ADR-2605261300 port). Drives calibrated → rf_tested
  → addiction_ux_audited → functional_tested → qc_record_emitted: phase/pct progression, RF (cellular
  off-default), the G8 anti-addiction UX guard (batch ≥15 min, no infinite-scroll, no autoplay-lock),
  and the qcRecord accept (uxGuard ∧ rf). Top-level UX inputs re-supplied across the threading."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-final-qc.state-machine :as sm]))

(defn- run-all [{:keys [batch scroll autoplay] :or {batch 15 scroll false autoplay false}}]
  (-> {"qc_state" {"phase" "init" "deviceId" "dev-1" "completionPct" 0}}
      sm/transition-to-calibrated
      sm/transition-to-rf-tested
      (assoc "notificationBatchMin" batch "infiniteScrollApi" scroll "autoplayLockScreen" autoplay)
      sm/transition-to-addiction-ux-audited
      sm/transition-to-functional-tested
      sm/transition-to-qc-record-emitted))

(deftest test-full-progression
  (let [s0 {"qc_state" {"phase" "init" "deviceId" "dev-1" "completionPct" 0}}
        s1 (sm/transition-to-calibrated s0)
        s2 (sm/transition-to-rf-tested s1)
        s3 (sm/transition-to-addiction-ux-audited s2)
        s4 (sm/transition-to-functional-tested s3)
        s5 (sm/transition-to-qc-record-emitted s4)]
    (is (= "calibrated" (get-in s1 ["qc_state" "phase"])))
    (is (= [15 35 60 82 100] (mapv #(get-in % ["qc_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= "off" (get-in s2 ["qc_state" "rf" "cellularDefault"])))
    (is (= false (get-in s2 ["qc_state" "rf" "imeiBroadcastWhileDisconnected"])))
    (is (= true (get-in s3 ["qc_state" "uxGuard" "accept"])))     ; defaults are calm
    (is (= "end" (get s5 "next_node")))))

(deftest test-g8-ux-guard
  ;; batch below 15 min → reject
  (is (= false (get-in (run-all {:batch 5}) ["qc_record" "uxGuard" "accept"])))
  ;; infinite-scroll API → reject
  (is (= false (get-in (run-all {:scroll true}) ["qc_record" "uxGuard" "accept"])))
  ;; autoplay on lock screen → reject
  (is (= false (get-in (run-all {:autoplay true}) ["qc_record" "uxGuard" "accept"])))
  ;; exactly 15 min, no addictive primitives → accept
  (let [ug (get-in (run-all {:batch 15}) ["qc_record" "uxGuard"])]
    (is (= true (get ug "accept")))
    (is (= "calm-default UX verified" (get ug "reason")))))

(deftest test-qc-record-accept-reflects-guards
  (is (= true (get-in (run-all {}) ["qc_record" "accept"])))
  ;; addictive UX fails the whole qc record
  (is (= false (get-in (run-all {:scroll true}) ["qc_record" "accept"])))
  (is (= "dev-1" (get-in (run-all {}) ["qc_record" "deviceId"]))))
