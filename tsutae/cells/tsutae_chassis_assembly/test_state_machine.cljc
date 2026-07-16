(ns tsutae.cells.tsutae-chassis-assembly.test-state-machine
  "Tests for the tsutae chassis_assembly state machine (ADR-2605261300 port). Drives components_staged
  → mic_killswitch_verified → repair_modularity_checked → chassis_assembled → attestation_emitted:
  phase/pct progression, the G6 mic-hardware-kill-switch guard, the G3 repair-modularity guard
  (adhesive ≤5 g, all 8 slots replaceable, no parts-pairing), and the chassisAttestation accept
  flag (micGuard ∧ repairGuard). Top-level inputs (micKillSwitch/adhesiveGrams/partsPairing) are
  re-supplied across the standalone threading, mirroring langgraph persistent state."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-chassis-assembly.state-machine :as sm]))

(defn- run-all [{:keys [mic adhesive pairing] :or {mic true adhesive 0.0 pairing false}}]
  (-> {"chassis_state" {"phase" "init" "chassisId" "chassis-1" "completionPct" 0}}
      sm/transition-to-components-staged
      (assoc "micKillSwitch" mic)
      sm/transition-to-mic-killswitch-verified
      (assoc "adhesiveGrams" adhesive "partsPairing" pairing)
      sm/transition-to-repair-modularity-checked
      sm/transition-to-chassis-assembled
      sm/transition-to-attestation-emitted))

(deftest test-full-progression-accepts
  (let [s0 {"chassis_state" {"phase" "init" "chassisId" "chassis-1" "completionPct" 0}}
        s1 (sm/transition-to-components-staged s0)
        s2 (sm/transition-to-mic-killswitch-verified s1)
        s3 (sm/transition-to-repair-modularity-checked s2)
        s4 (sm/transition-to-chassis-assembled s3)
        s5 (sm/transition-to-attestation-emitted s4)]
    (is (= "components_staged" (get-in s1 ["chassis_state" "phase"])))
    (is (= [15 35 55 80 100] (mapv #(get-in % ["chassis_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= 9 (count (get-in s1 ["chassis_state" "components"]))))
    (is (= true (get-in s2 ["chassis_state" "micGuard" "accept"])))          ; killswitch default true
    (is (= true (get-in s3 ["chassis_state" "repairGuard" "allModulesReplaceable"])))
    (is (= true (get-in s3 ["chassis_state" "repairGuard" "accept"])))
    (is (= false (get-in s4 ["chassis_state" "fasteners" "pentalobe"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-g6-mic-killswitch-required
  (let [mg (get-in (run-all {:mic false}) ["chassis_attestation" "micGuard"])]
    (is (= false (get mg "accept")))
    (is (= "missing hardware mic kill switch (§2(c) N4 invariant)" (get mg "reason"))))
  ;; missing killswitch → whole attestation rejected
  (is (= false (get-in (run-all {:mic false}) ["chassis_attestation" "accept"]))))

(deftest test-g3-repair-guard
  ;; excess adhesive (>5 g) → repair guard rejects
  (let [rg (get-in (run-all {:adhesive 7.5}) ["chassis_attestation" "repairGuard"])]
    (is (= false (get rg "accept")))
    (is (= 7.5 (get rg "adhesiveGrams"))))
  ;; parts-pairing → rejected
  (is (= false (get-in (run-all {:pairing true}) ["chassis_attestation" "repairGuard" "accept"])))
  ;; exactly at the 5 g limit → accepted (≤)
  (is (= true (get-in (run-all {:adhesive 5.0}) ["chassis_attestation" "repairGuard" "accept"]))))

(deftest test-attestation-accept-and-fields
  (let [rec (get (run-all {}) "chassis_attestation")]
    (is (= "com.etzhayyim.tsutae.chassisAttestation" (get rec "$type")))
    (is (= "chassis-1" (get rec "chassisId")))
    (is (= true (get rec "accept"))))                ; mic ∧ repair both pass on the happy path
  ;; any single guard failing fails the attestation
  (is (= false (get-in (run-all {:adhesive 9.0}) ["chassis_attestation" "accept"]))))
