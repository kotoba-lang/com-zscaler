(ns shomei.cells.shomei-challenge.cell
  "shomei_challenge — issue a single-use verificationChallenge nonce. R0 scaffold. ADR-2606072100.
  1:1 Clojure port of `cells/shomei_challenge/cell.py`.

  Live issuance (writing a com.etzhayyim.shomei.verificationChallenge to the kotoba Datom log) is
  outward-gated (G11): the cell solve raises at R0. The offline nonce/binding logic is exercised
  by methods/ (analyze + tests). G7: the server only issues + records consumption, never signs.

  Python `RuntimeError` → `(throw (ex-info ...))`; `ChallengeCell.solve` → the stateless `solve` fn.")

(defn solve
  "R0 scaffold: issues nonces offline only; live issuance is outward-gated (G11)."
  [_input-state]
  (throw (ex-info
          (str "shomei R0 scaffold: shomei_challenge issues nonces offline only; live issuance to the "
               "kotoba Datom log is outward-gated (G11). See methods/verify.py for the binding policy.")
          {:scaffold true})))
