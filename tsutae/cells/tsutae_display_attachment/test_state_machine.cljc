(ns tsutae.cells.tsutae-display-attachment.test-state-machine
  "Tests for the tsutae display_attachment state machine (ADR-2605261300 port). Drives panel_verified
  → laminated → touch_calibrated → attestation_emitted: phase/pct progression, the gasket-clip
  (zero-adhesive, G3-respecting) lamination, the touch calibration, and the displayAttachedRecord
  accept flag (touch.accept ∧ adhesive ≤ 5 g). Also checks the panelType passthrough."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-display-attachment.state-machine :as sm]))

(defn- run-all [panel-type]
  (-> {"display_state" {"phase" "init" "chassisId" "chassis-1" "completionPct" 0} "panelType" panel-type}
      sm/transition-to-panel-verified
      sm/transition-to-laminated
      sm/transition-to-touch-calibrated
      sm/transition-to-attestation-emitted))

(deftest test-full-progression
  (let [s0 {"display_state" {"phase" "init" "chassisId" "chassis-1" "completionPct" 0}}
        s1 (sm/transition-to-panel-verified s0)
        s2 (sm/transition-to-laminated s1)
        s3 (sm/transition-to-touch-calibrated s2)
        s4 (sm/transition-to-attestation-emitted s3)]
    (is (= "panel_verified" (get-in s1 ["display_state" "phase"])))
    (is (= [25 55 80 100] (mapv #(get-in % ["display_state" "completionPct"]) [s1 s2 s3 s4])))
    (is (= "LCD" (get-in s1 ["display_state" "panel" "type"])))     ; default panel type
    (is (= true (get-in s1 ["display_state" "panel" "replaceable"])))
    (is (= "gasket-clip" (get-in s2 ["display_state" "lamination" "method"])))
    (is (= 0.0 (get-in s2 ["display_state" "lamination" "adhesiveGrams"])))
    (is (= true (get-in s3 ["display_state" "touch" "accept"])))
    (is (= "end" (get s4 "next_node")))))

(deftest test-panel-type-passthrough
  (is (= "OLED" (get-in (sm/transition-to-panel-verified
                         {"display_state" {"phase" "init" "chassisId" "c" "completionPct" 0} "panelType" "OLED"})
                        ["display_state" "panel" "type"]))))

(deftest test-attestation-accepts-on-clean-lamination
  (let [rec (get (run-all "LCD") "display_attached")]
    (is (= "com.etzhayyim.tsutae.displayAttachedRecord" (get rec "$type")))
    (is (= "chassis-1" (get rec "chassisId")))
    (is (= true (get rec "accept")))))    ; touch.accept ∧ adhesive 0.0 ≤ 5.0

(deftest test-attestation-rejects-on-excess-adhesive
  ;; tamper the lamination adhesive above the 5 g limit before emitting → reject
  (let [s3 (-> {"display_state" {"phase" "init" "chassisId" "c" "completionPct" 0}}
               sm/transition-to-panel-verified sm/transition-to-laminated sm/transition-to-touch-calibrated)
        tampered (assoc-in s3 ["display_state" "lamination" "adhesiveGrams"] 9.0)
        rec (get (sm/transition-to-attestation-emitted tampered) "display_attached")]
    (is (= false (get rec "accept")))))
