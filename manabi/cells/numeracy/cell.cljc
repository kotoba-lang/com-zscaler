(ns manabi.cells.numeracy.cell
  "NumeracyCell — manabi R0 scaffold per ADR-2605261045.

  R0 scaffold. Counting → arithmetic → algebra → geometry → calculus.
  Inquiry-based (G13). No examination-as-coercion (G10).

  Faithful 1:1 cljc port of cells/numeracy/cell.py.")

(defn solve
  "Numeracy curriculum (counting → calculus).
  R0 scaffold — raises until ADR-2605261045 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi R0 scaffold: numeracy cell not activated. "
                "Requires ADR-2605261045 Council ratify + numeracy starter "
                "curriculum Council-reviewed.")
           {:cell :numeracy
            :status :r0-scaffold})))
