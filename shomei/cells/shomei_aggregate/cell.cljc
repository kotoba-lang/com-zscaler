(ns shomei.cells.shomei-aggregate.cell
  "shomei_aggregate — roll verified claims into a personhoodCredential. R0 scaffold. ADR-2606072100.
  1:1 Clojure port of `cells/shomei_aggregate/cell.py`.

  Aggregation logic (IAL ladder + proof-of-personhood + W3C VC) is implemented + tested in
  methods/aggregate. Live issuance of a com.etzhayyim.shomei.personhoodCredential to the kotoba
  Datom log is outward-gated (G11) and member-signed (G7 no-server-key); the cell solve raises at R0.

  Python `RuntimeError` → `(throw (ex-info ...))`; `AggregateCell.solve` → the stateless `solve` fn.")

(defn solve
  "R0 scaffold: computes credentials offline; live member-signed issuance is gated (G7/G11)."
  [_input-state]
  (throw (ex-info
          (str "shomei R0 scaffold: shomei_aggregate computes credentials offline "
               "(methods/aggregate.py); live member-signed issuance is outward-gated (G7/G11).")
          {:scaffold true})))
