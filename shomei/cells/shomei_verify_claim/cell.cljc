(ns shomei.cells.shomei-verify-claim.cell
  "shomei_verify_claim — verify a subject-signed identityClaim. R0 scaffold. ADR-2606072100.
  1:1 Clojure port of `cells/shomei_verify_claim/cell.py`.

  Wires proofKind → kotoba-auth (eth/btc/cacao + Ed25519) per methods/verify PROOF_ROUTING.
  Live verification against the real kotoba-auth surface + writing the verified claim to the kotoba
  Datom log is outward-gated (G11); the cell solve raises at R0. The verification POLICY (challenge
  binding, single-use nonce, gov gate, subject-sig) is fully implemented + tested in methods/verify.

  Python `RuntimeError` → `(throw (ex-info ...))`; `VerifyClaimCell.solve` → the stateless `solve` fn.")

(defn solve
  "R0 scaffold: runs the verification policy offline; live verify + Datom write is gated (G11)."
  [_input-state]
  (throw (ex-info
          (str "shomei R0 scaffold: shomei_verify_claim runs the verification policy offline "
               "(methods/verify.py); live kotoba-auth verification + Datom write is outward-gated (G11).")
          {:scaffold true})))
