(ns hikari.cells.storage-battery.cell
  "StorageBatteryCell — hikari R0 scaffold per ADR-2605261100.
  1:1 port of cells/storage_battery/cell.py.

  R0 scaffold. Battery bank install + BMS config + safety attestation.
  G3 chemistry safety: LFP / NMC restricted / sodium-ion preferred; no
  lead-acid stationary R2+; thermal runaway containment mandatory.
  .solve() raises at R0 (Council-gated R1 implementation).")

(defn solve
  [_input-state]
  (throw (ex-info (str "hikari R0 scaffold: storage_battery cell not activated. "
                       "Requires ADR-2605261100 Council ratify + G3 battery chemistry "
                       "safety attestation framework Council-ratified.")
                  {:scaffold true :cell :storage-battery})))
