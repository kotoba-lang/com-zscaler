(ns magatama.cells.suimin-disclaimer-gate.cell
  "SuiminDisclaimerGateCell — non-diagnostic disclaimer + red-flag screen (architectural invariant).
  Per ADR-2606072800 §Decision 3 G3 (disclaimer invariant) + G5 (red-flag escalation) + §Decision 5.

  ALL patient-facing suimin output MUST pass through this cell. It stamps the active disclaimer
  reference (G3) and screens red-flag signals (G5) → mitate emergency path. Bypass-forbidden
  architectural invariant. R0 scaffold — .solve() raises until the Council activation gate is
  satisfied (1:1 port of suimin_disclaimer_gate/cell.py import-time RuntimeError).")

(defn solve
  [_input-state]
  (throw (ex-info
          (str "suimin_disclaimer_gate cell scaffold-only — Council has not (a) attested the "
               "suimin master charter ADR-2606072800, or (b) ratified the disclaimer baseline "
               "(G3 — every patient-facing output carries '医師の診断・治療の代替ではない / 睡眠専門医・"
               "地元医療機関へ'), or (c) ratified the red-flag escalation protocol (G5 — routes severe "
               "signals to the mitate emergency path). This cell is a bypass-forbidden architectural "
               "invariant. Do not deploy.")
          {:scaffold true :cell :suimin-disclaimer-gate})))
