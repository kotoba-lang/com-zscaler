(ns tsutae.cells.tsutae-firmware-load.test-state-machine
  "Tests for the tsutae firmware_load state machine (ADR-2605261300 port). Drives image_verified →
  blob_ratio_checked → bootloader_unlock_confirmed → flashed → attestation_emitted: phase/pct
  progression, the open firmware stack + baseline passthrough, the G7 blob-ratio guard (≤5%), the
  G2 bootloader-unlock guard, and the firmwareAttestation accept (blobGuard ∧ bootloaderGuard).
  Top-level blobRatioPct / bootloaderUnlockable re-supplied across the standalone threading."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-firmware-load.state-machine :as sm]))

(defn- run-all [{:keys [blob unlock baseline] :or {blob 2.0 unlock true baseline "ameno"}}]
  (-> {"firmware_state" {"phase" "init" "deviceId" "dev-1" "completionPct" 0} "baseline" baseline}
      sm/transition-to-image-verified
      (assoc "blobRatioPct" blob)
      sm/transition-to-blob-ratio-checked
      (assoc "bootloaderUnlockable" unlock)
      sm/transition-to-bootloader-unlock-confirmed
      sm/transition-to-flashed
      sm/transition-to-attestation-emitted))

(deftest test-full-progression
  (let [s0 {"firmware_state" {"phase" "init" "deviceId" "dev-1" "completionPct" 0}}
        s1 (sm/transition-to-image-verified s0)
        s2 (sm/transition-to-blob-ratio-checked s1)
        s3 (sm/transition-to-bootloader-unlock-confirmed s2)
        s4 (sm/transition-to-flashed s3)
        s5 (sm/transition-to-attestation-emitted s4)]
    (is (= "image_verified" (get-in s1 ["firmware_state" "phase"])))
    (is (= [15 35 55 80 100] (mapv #(get-in % ["firmware_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= ["coreboot" "linux-mainline-6.x" "graphene-class-userspace"]
           (get-in s1 ["firmware_state" "image" "stack"])))
    (is (= true (get-in s1 ["firmware_state" "image" "openSourceChain"])))
    (is (= true (get-in s2 ["firmware_state" "blobGuard" "accept"])))     ; default 2.0 ≤ 5
    (is (= true (get-in s3 ["firmware_state" "bootloaderGuard" "accept"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-baseline-passthrough
  (is (= "mitate" (get-in (sm/transition-to-image-verified
                           {"firmware_state" {"phase" "init" "deviceId" "d" "completionPct" 0} "baseline" "mitate"})
                          ["firmware_state" "image" "baselineOption"]))))

(deftest test-g7-blob-ratio-guard
  ;; over the 5% limit → reject
  (let [bg (get-in (run-all {:blob 7.5}) ["firmware_attestation" "blobGuard"])]
    (is (= false (get bg "accept")))
    (is (= 7.5 (get bg "blobRatioPct"))))
  ;; exactly 5.0 → accept (≤)
  (is (= true (get-in (run-all {:blob 5.0}) ["firmware_attestation" "blobGuard" "accept"]))))

(deftest test-g2-bootloader-guard
  (let [bg (get-in (run-all {:unlock false}) ["firmware_attestation" "bootloaderGuard"])]
    (is (= false (get bg "accept")))
    (is (= "locked bootloader rejected (§2(b) N2 invariant)" (get bg "reason")))))

(deftest test-attestation-accept-reflects-guards
  (is (= true (get-in (run-all {}) ["firmware_attestation" "accept"])))
  (let [rec (get (run-all {:blob 9.0}) "firmware_attestation")]
    (is (= false (get rec "accept")))           ; blob guard fails
    (is (= "com.etzhayyim.tsutae.firmwareAttestation" (get rec "$type")))
    (is (= "dev-1" (get rec "deviceId"))))
  (is (= false (get-in (run-all {:unlock false}) ["firmware_attestation" "accept"]))))  ; bootloader fails
