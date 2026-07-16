(ns watatsumi.cells.pressure-test.state-machine
  "Pressure-test state machine — ADR-2605252200 L5b. 1:1 cljc port of
  `cells/pressure_test/state_machine.py`.

  1.25× design-depth water-pressure test, continuous Hibiki acoustic-emission
  monitoring during pressurization. G12 KPI: design depth ≤6500 m civilian cap.
  The transitions are deterministic constant assignments; string keys mirror the
  Python dataclass `__dict__` surface so the emitted record is byte-identical.")

;; ── Phase enum (Python value identities preserved) ────────────────

(def phases
  {:init "init"
   :design-depth-verified "design_depth_verified"
   :dock-lowering "dock_lowering"
   :pressurization "pressurization"
   :hold "hold"
   :depressurization "depressurization"
   :record-emitted "record_emitted"})

(def design-depth-civilian-cap-m
  "G12 KPI: design depth ≤6500 m civilian cap."
  6500)

;; ── init (mirrors cell.py `_init`) ────────────────────────────────

(defn init
  "Fresh pressure_test_state from an input state. Port of `PressureTestCell._init`."
  [state]
  {"pressure_test_state"
   {"phase" (:init phases)
    "craftId" (get state "craftId" "WATATSUMI-RESEARCH-0001")
    "completionPct" 0}})

;; ── Transitions ───────────────────────────────────────────────────

(defn transition-to-design-depth-verified [state]
  (let [pt (-> (get state "pressure_test_state" {})
               (assoc "designDepthM" design-depth-civilian-cap-m
                      "testDepthEquivalentM" (int (* 6500 1.25))   ;; 8125 m
                      "testPressureDbar" 8125                       ;; 812.5 bar gauge, deci-bar
                      "phase" (:design-depth-verified phases)
                      "completionPct" 15))]
    {"pressure_test_state" pt "next_node" "dock"}))

(defn transition-to-dock-lowering [state]
  (let [pt (-> (get state "pressure_test_state" {})
               (assoc "phase" (:dock-lowering phases) "completionPct" 25))]
    {"pressure_test_state" pt "next_node" "pressurize"}))

(defn transition-to-pressurization
  "Pressurize at ≤5 bar/min ramp; Hibiki AE continuous monitoring."
  [state]
  (let [pt (-> (get state "pressure_test_state" {})
               (assoc "hibikiAEStream"
                      [{"timestamp" "T+00:00"  "barGauge" 0     "aeEventsPerMin" 0}
                       {"timestamp" "T+30:00"  "barGauge" 150   "aeEventsPerMin" 2}
                       {"timestamp" "T+60:00"  "barGauge" 300   "aeEventsPerMin" 4}
                       {"timestamp" "T+120:00" "barGauge" 600   "aeEventsPerMin" 6}
                       {"timestamp" "T+160:00" "barGauge" 812.5 "aeEventsPerMin" 7}]
                      "phase" (:pressurization phases)
                      "completionPct" 60))]
    {"pressure_test_state" pt "next_node" "hold"}))

(defn transition-to-hold
  "Hold at test pressure ≥60 min, leak-rate check."
  [state]
  (let [pt (-> (get state "pressure_test_state" {})
               (assoc "holdDurationMinutes" 60
                      "leakRateMicrolitrePerMin" 0
                      "phase" (:hold phases)
                      "completionPct" 80))]
    {"pressure_test_state" pt "next_node" "depressurize"}))

(defn transition-to-depressurization [state]
  (let [pt (-> (get state "pressure_test_state" {})
               (assoc "phase" (:depressurization phases) "completionPct" 95))]
    {"pressure_test_state" pt "next_node" "record"}))

(defn transition-to-record-emitted [state]
  (let [pt0 (get state "pressure_test_state" {})
        design-depth (get pt0 "designDepthM")
        accept (boolean
                (and (< (or (get pt0 "leakRateMicrolitrePerMin") 0) 1000)   ;; < 1.0 mL/min
                     (some? design-depth)
                     (<= design-depth design-depth-civilian-cap-m)))        ;; G12
        pt (assoc pt0 "overallAccept" accept
                  "phase" (:record-emitted phases) "completionPct" 100)
        record {"$type" "com.etzhayyim.watatsumi.pressureTestRecord"
                "craftId" (get pt "craftId")
                "designDepthM" (get pt "designDepthM")
                "testDepthEquivalentM" (get pt "testDepthEquivalentM")
                "testPressureDbar" (get pt "testPressureDbar")
                "hibikiAEStream" (get pt "hibikiAEStream")
                "holdDurationMinutes" (get pt "holdDurationMinutes")
                "leakRateMicrolitrePerMin" (get pt "leakRateMicrolitrePerMin")
                "overallAccept" (get pt "overallAccept")
                "g12KpiCheck" {"maxCivilianDepthM" 6500 "accept" true}
                "recordedAt" "2026-05-26T17:00:00Z"}]
    {"pressure_test_state" pt "pressure_test_record" record "next_node" "end"}))

;; ── Full deterministic chain (init → … → record_emitted) ──────────

(defn run-chain
  "Run the full L5b transition chain from an input state to the emitted record.
  Mirrors the cell.py StateGraph init→verify→dock→pressurize→hold→depressurize→record."
  [input-state]
  (-> (init input-state)
      transition-to-design-depth-verified
      transition-to-dock-lowering
      transition-to-pressurization
      transition-to-hold
      transition-to-depressurization
      transition-to-record-emitted))
