(ns manabi.cells.lifelong-inquiry.cell
  "LifelongInquiryCell — manabi R0 scaffold per ADR-2605261045.

  R0 scaffold. Open-ended questioning + critical analysis + research
  methodology. No fixed curriculum; learner-led. G11 (lifelong, no age-out)
  + G13 (inquiry-based ≥30% learner-led) constitutional anchors.

  Faithful 1:1 cljc port of cells/lifelong_inquiry/cell.py.")

(defn solve
  "Open-ended lifelong inquiry.
  R0 scaffold — raises until ADR-2605261045 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi R0 scaffold: lifelong_inquiry cell not activated. "
                "Requires ADR-2605261045 Council ratify + R3 phase (post-R2 + "
                "Yutori telepresence inheritance from hagukumi).")
           {:cell :lifelong-inquiry
            :status :r0-scaffold})))
