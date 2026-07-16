(ns tsutae.cells.tsutae-recycling-intake.test-state-machine
  "Tests for the tsutae recycling_intake state machine (ADR-2605261300 port; the EOL→kanayama
  routing cell). Drives dismantled → materials_sorted → kanayama_routed → certificate_emitted:
  phase/pct progression, non-destructive hand-tool dismantle, per-material sort + routes, the G10
  recovery guard (recovered-mass fraction vs ≥80% target, parity-pinned to Python), and the emitted
  recyclingCertificate. Top-level totalMassG re-supplied across the threading."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-recycling-intake.state-machine :as sm]))

(defn- run-all [total]
  (-> {"recycling_state" {"phase" "init" "serial" "SN-9" "completionPct" 0}}
      sm/transition-to-dismantled
      sm/transition-to-materials-sorted
      (cond-> total (assoc "totalMassG" total))
      sm/transition-to-kanayama-routed
      sm/transition-to-certificate-emitted))

(deftest test-full-progression
  (let [s0 {"recycling_state" {"phase" "init" "serial" "SN-9" "completionPct" 0}}
        s1 (sm/transition-to-dismantled s0)
        s2 (sm/transition-to-materials-sorted s1)
        s3 (sm/transition-to-kanayama-routed s2)
        s4 (sm/transition-to-certificate-emitted s3)]
    (is (= "dismantled" (get-in s1 ["recycling_state" "phase"])))
    (is (= [25 55 80 100] (mapv #(get-in % ["recycling_state" "completionPct"]) [s1 s2 s3 s4])))
    (is (= false (get-in s1 ["recycling_state" "dismantle" "destructive"])))   ; non-destructive
    (is (= 5 (count (get-in s2 ["recycling_state" "sort"]))))
    ;; aluminum + pcb routed to kanayama
    (is (= 2 (count (filter #(= "kanayama" (get % "route")) (get-in s2 ["recycling_state" "sort"])))))
    (is (= "end" (get s4 "next_node")))))

(deftest test-g10-recovery-guard-parity
  ;; default total 200 → 173/200 = 86.5% ≥ 80 → accept (parity-pinned to Python)
  (let [rg (get-in (run-all nil) ["recycling_certificate" "recoveryGuard"])]
    (is (= 86.5 (get rg "recoveredMassPct")))
    (is (= true (get rg "accept"))))
  ;; total 250 → 69.2% < 80 → flagged (accept false, loss surfaced not hidden)
  (let [rg (get-in (run-all 250.0) ["recycling_certificate" "recoveryGuard"])]
    (is (= 69.2 (get rg "recoveredMassPct")))
    (is (= false (get rg "accept")))
    (is (re-find #"below 80.0% target \(flagged, not hidden\)" (get rg "reason"))))
  ;; total == recovered → 100% accept
  (is (= 100.0 (get-in (run-all 173.0) ["recycling_certificate" "recoveryGuard" "recoveredMassPct"]))))

(deftest test-certificate-fields
  (let [rec (get (run-all nil) "recycling_certificate")]
    (is (= "com.etzhayyim.tsutae.recyclingCertificate" (get rec "$type")))
    (is (= "SN-9" (get rec "serial")))
    (is (= 5 (count (get rec "materialBalance"))))
    (is (= true (get rec "accept"))))
  ;; below-target run → certificate accept false
  (is (= false (get-in (run-all 250.0) ["recycling_certificate" "accept"]))))
