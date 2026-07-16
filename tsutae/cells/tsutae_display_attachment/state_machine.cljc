(ns tsutae.cells.tsutae-display-attachment.state-machine
  "1:1 port of cells/tsutae_display_attachment/state_machine.py (ADR-2605261300 phase `display`).
  Laminates the (replaceable) display panel onto the chassis and calibrates the touch digitizer.
  Lamination uses a re-openable gasket/clip mount (NOT permanent adhesive) so G3 repair-rightful
  holds. Emits com.etzhayyim.tsutae.displayAttachedRecord. DisplayState dataclass → string-keyed
  map under \"display_state\" (all fields present, nil for unset).")

(defn- s* [state]
  (merge {"panel" nil "lamination" nil "touch" nil} (get state "display_state" {})))

(defn transition-to-panel-verified [state]
  {"display_state" (assoc (s* state)
                          "panel" {"type" (get state "panelType" "LCD") "sizeIn" 6.1 "deadPixels" 0
                                   "lotId" "DISP-2026-05-LOT-0007" "replaceable" true}
                          "phase" "panel_verified" "completionPct" 25)
   "next_node" "laminate"})

(defn transition-to-laminated [state]
  {"display_state" (assoc (s* state)
                          "lamination" {"method" "gasket-clip" "adhesiveGrams" 0.0
                                        "robot" "robot:hitogata" "bubbleCount" 0}
                          "phase" "laminated" "completionPct" 55)
   "next_node" "calibrate"})

(defn transition-to-touch-calibrated [state]
  {"display_state" (assoc (s* state)
                          "touch" {"points" 5 "linearityErrPx" 1.2 "specLimitPx" 3.0 "accept" true}
                          "phase" "touch_calibrated" "completionPct" 80)
   "next_node" "attestation"})

(defn transition-to-attestation-emitted [state]
  (let [s (assoc (s* state) "phase" "attestation_emitted" "completionPct" 100)]
    {"display_state" s
     "display_attached" {"$type" "com.etzhayyim.tsutae.displayAttachedRecord"
                         "chassisId" (get s "chassisId")
                         "panel" (get s "panel")
                         "lamination" (get s "lamination")
                         "touch" (get s "touch")
                         "accept" (boolean (and (get (or (get s "touch") {}) "accept")
                                                (<= (get (or (get s "lamination") {}) "adhesiveGrams" 99) 5.0)))
                         "recordedAt" "2026-05-26T11:00:00Z"}
     "next_node" "end"}))
