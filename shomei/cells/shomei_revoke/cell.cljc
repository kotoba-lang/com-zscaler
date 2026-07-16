(ns shomei.cells.shomei-revoke.cell
  "shomei_revoke — process a subject-signed bindingRevocation. R0 scaffold. ADR-2606072100.
  1:1 Clojure port of `cells/shomei_revoke/cell.py`.

  G5 owner-only + G10/Tier-0 append-only (a revocation never deletes the claim's history, 永久記憶).
  Logic in methods/revoke. Live append of a com.etzhayyim.shomei.bindingRevocation to the kotoba
  Datom log is outward-gated (G11) and member-signed (G7); the cell solve raises at R0.

  Python `RuntimeError` → `(throw (ex-info ...))`; `RevokeCell.solve` → the stateless `solve` fn.")

(defn solve
  "R0 scaffold: validates revocations offline; live append-only Datom write is gated (G7/G11)."
  [_input-state]
  (throw (ex-info
          (str "shomei R0 scaffold: shomei_revoke validates revocations offline (methods/revoke.py); "
               "live append-only Datom write is outward-gated + member-signed (G7/G11).")
          {:scaffold true})))
