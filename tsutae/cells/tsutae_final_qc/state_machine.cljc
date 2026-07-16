(ns tsutae.cells.tsutae-final-qc.state-machine
  "1:1 port of cells/tsutae_final_qc/state_machine.py (ADR-2605261300 phase `qc`). Sensor/display
  calibration → RF conformance (cellular OFF default; Wi-Fi/BT) → G8 anti-addiction UX audit →
  functional self-test → internal qcRecord (consumed by device_attestation).

  Constitutional guard: G8 (§2(d)) — calm-default OS: notification batching ≥15 min, no infinite-
  scroll OS primitive, no dopamine-loop API, no auto-play on lock screen; an addictive-UX build is
  rejected. QcState dataclass → string-keyed map under \"qc_state\" (all fields present, nil unset).")

(def ^:private G8-NOTIFY-BATCH-MIN 15)

(defn- s* [state]
  (merge {"calibration" nil "rf" nil "uxGuard" nil "functional" nil} (get state "qc_state" {})))

(defn transition-to-calibrated [state]
  {"qc_state" (assoc (s* state)
                     "calibration" {"display" "ok" "imu" "ok" "camera" "ok" "touch" "ok"}
                     "phase" "calibrated" "completionPct" 15)
   "next_node" "rf"})

(defn transition-to-rf-tested [state]
  {"qc_state" (assoc (s* state)
                     "rf" {"cellularDefault" "off" "wifi" "pass" "bt" "pass"
                           "imeiBroadcastWhileDisconnected" false "accept" true}
                     "phase" "rf_tested" "completionPct" 35)
   "next_node" "ux_guard"})

(defn transition-to-addiction-ux-audited [state]
  (let [batch-min (int (get state "notificationBatchMin" G8-NOTIFY-BATCH-MIN))
        infinite-scroll (boolean (get state "infiniteScrollApi" false))
        autoplay-lock (boolean (get state "autoplayLockScreen" false))
        accept (and (>= batch-min G8-NOTIFY-BATCH-MIN) (not infinite-scroll) (not autoplay-lock))]
    {"qc_state" (assoc (s* state)
                       "uxGuard" {"gate" "G8" "notificationBatchMin" batch-min
                                  "infiniteScrollApi" infinite-scroll "autoplayLockScreen" autoplay-lock
                                  "accept" accept
                                  "reason" (if accept "calm-default UX verified"
                                               "addictive-UX primitive present (§2(d) N5)")}
                       "phase" "addiction_ux_audited" "completionPct" 60)
     "next_node" "functional"}))

(defn transition-to-functional-tested [state]
  {"qc_state" (assoc (s* state)
                     "functional" {"boot" "pass" "battery" "pass" "audio" "pass" "sensors" "pass"}
                     "phase" "functional_tested" "completionPct" 82)
   "next_node" "record"})

(defn transition-to-qc-record-emitted [state]
  (let [s (assoc (s* state) "phase" "qc_record_emitted" "completionPct" 100)]
    {"qc_state" s
     "qc_record" {"deviceId" (get s "deviceId")
                  "calibration" (get s "calibration")
                  "rf" (get s "rf")
                  "uxGuard" (get s "uxGuard")
                  "functional" (get s "functional")
                  "accept" (boolean (and (get (or (get s "uxGuard") {}) "accept")
                                         (get (or (get s "rf") {}) "accept")))
                  "recordedAt" "2026-05-26T13:00:00Z"}
     "next_node" "end"}))
