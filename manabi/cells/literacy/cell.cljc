(ns manabi.cells.literacy.cell
  "LiteracyCell — manabi R0 scaffold per ADR-2605261045.

  R0 scaffold. Reading + writing across languages (EN + JA + adherent-native
  at ≥10-adherent-per-language threshold per G5). G3 (no addictive UX) +
  G6 (zero data collection on minors) + G7 (anti-credentialism) enforced.

  Faithful 1:1 cljc port of cells/literacy/cell.py.")

(defn solve
  "Reading + writing curriculum.
  R0 scaffold — raises until ADR-2605261045 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi R0 scaffold: literacy cell not activated. "
                "Requires ADR-2605261045 Council ratify + initial literacy "
                "curriculum library Council-reviewed for G4 charter alignment.")
           {:cell :literacy
            :status :r0-scaffold})))
