(ns mitsuho.cells.aquaculture.cell
  "AquacultureCell — mitsuho R0 scaffold per ADR-2605261015.

  R0 scaffold. Freshwater only (N9 excludes ocean factory-fishing — separate
  Funamori marine actor scope). N10 excludes protected/critical-habitat waters.

  Freshwater aquaculture — fish + shellfish + aquatic plants.")

(defn solve
  "R0 scaffold: raises until Council activation (no live actuation)."
  [_state]
  (throw (ex-info (str "mitsuho R0 scaffold: aquaculture cell not activated. "
                       "Requires ADR-2605261015 Council ratify + parcelEnergyAttestation "
                       "for water-source biodiversity-no-harm confirmation.")
                  {:cell :aquaculture :status :r0-scaffold})))
