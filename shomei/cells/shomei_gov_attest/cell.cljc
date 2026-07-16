(ns shomei.cells.shomei-gov-attest.cell
  "shomei_gov_attest — IAL-4 government-verified elevation handoff. R0 scaffold. ADR-2606072100.
  1:1 Clojure port of `cells/shomei_gov_attest/cell.py`.

  This is the ONLY shomei path that touches a government factor (gov-mynumber/passport/license). It
  does NOT itself read the card or query any state database (§0.4 non-registration, G6); it hands the
  member's local NFC-read + XChaCha20-encrypted payload to the EXISTING Council-gated gov_auth cells
  (magatama.cells.gov_auth_mynumber_bind + gov_auth_trust_attestation, ADR-2605260000) and ingests the
  resulting didTrustAttestation as a gov-class factor.

  Doubly gated: gov_auth itself requires COUNCIL_ATTESTATION_TX_HASH (Council Lv6+ ≥3 multisig); this
  cell solve raises until ADR-2605260000 R1 activation (see ADR-2606072300). methods/verify marks
  nfc-jpki as a GATED_PROOF so even the offline path refuses it unless the gate is explicitly opened.

  Python `RuntimeError` → `(throw (ex-info ...))`. The Python class `GovAttestCell` with one
  instance method `.solve(input_state)` → the `solve` fn (the cell is stateless).")

(defn solve
  "R0 scaffold: hands gov-ID elevation to the Council-gated gov_auth cells; raises until R1."
  [_input-state]
  (throw (ex-info
          (str "shomei R0 scaffold: shomei_gov_attest hands gov-ID elevation to the Council-gated "
               "gov_auth cells (ADR-2605260000); blocked until R1 activation "
               "(COUNCIL_ATTESTATION_TX_HASH, ADR-2606072300). No state-database query (§0.4/G6).")
          {:scaffold true})))
