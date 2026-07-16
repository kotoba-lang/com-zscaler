# kotoba_kawase

Religious-corp adherent-to-adherent multi-stable remittance Python facade
per **ADR-2605282200** (kawase-yui — 為替結).

`kotoba_kawase` is the **adherent-facing client surface** that sits above the
L6 settlement layer (`50-infra/etzhayyim-kawase-pool/src/KawaseYuiPool.sol`)
and the L4 match engine (`40-engine/kotoba/crates/kotoba-kotodama/cells/kawase_pool_match/`).
A yoro PWA or a kotoba LangGraph cell imports `kotoba_kawase` and calls
`send(...)` + `claim(...)`; the package validates pre-flight, emits the
intent CID, and returns audit-trail-compatible records.

## Placement (post ADR-2605282300)

Per ADR-2605282300, religious-corp downstream consumers of the kotoba
substrate live OUTSIDE the `40-engine/kotoba/` subrepo. `kotoba_kawase`
follows the `kotoba_murakumo` precedent and ships at
`40-engine/kotoba_kawase/` (sibling of the kotoba subrepo).

Original ADR-2605282200 referenced `40-engine/kotoba/py/kotoba_kawase`;
that path is superseded by this one. The G7 lint hook's guarded roots
include both paths so the structural enforcement covers either layout.

## Status

| Phase | Scope | State |
|---|---|---|
| **R0** | This README + `pyproject.toml` + `kotoba_kawase/__init__.py` (NotYetImplemented on every public call) + `kotoba_kawase/exceptions.py` (mirror Solidity error names) + `tests/test_r0.py` (verifies R0 honesty) | **landed** |
| **R1** | Real `send(...)` / `claim(...)` bodies: pre-flight Adherent SBT check + Chainlink mid-market oracle quote + Charter Rider §2(a)-(h) scan on memo + per-month cap check + intent CID emit + KawaseYuiPool.deposit() dispatch | post-Bootstrap-Council ratify + KawaseYuiPool R1 deploy |
| **R2** | + jurisdiction_compliance Pregel cell cross-actor read + chigiri.disputeMediation escalation path | post-R1 + 30-day public objection |
| **R3** | + multi-stable matrix (KRWO + GBPe + CHFe) + wakai cross-actor mutual-aid bundle composition | post-R2 |

## Constitutional invariants (mirrored from KawaseYuiPool)

The R1 implementation MUST honor these without exception:

- **G3 Adherent-only** — `send(to_did=...)` raises `NotAdherent` when either
  caller or recipient lacks the Adherent SBT.
- **G4 Mid-market band** — `send()` raises `OutOfBandFx` when the live
  Chainlink quote exceeds ±0.5% of the band const.
- **G5 No spread profit** — rate locked at intent creation; no spread
  accrues to religious-corp.
- **G7 No commercial remittance integration** — build-time enforced by
  `70-tools/scripts/lint/verify_no_commercial_remittance.py` which guards
  this package's source tree.
- **G9 Per-month cap** — `send()` raises `PerMonthCapBreached` when the
  Adherent's rolling 30-day USD-equivalent total exceeds the cap.
- **G11 No chargeback** — there is no `reverse()` or `unwind()` function.
- **G14 Per-jurisdiction Lv7+ unanimity** — `send()` raises
  `JurisdictionNotActivated` when sender ↔ recipient pair lacks a
  Council Lv7+ unanimity-attested `jurisdictionAttestation` Lexicon.

## Related

- `50-infra/etzhayyim-kawase-pool/` — Solidity L6 (KawaseYuiPool.sol)
- `00-contracts/lexicons/com/etzhayyim/kawase/` — 8 Lexicons
- `70-tools/scripts/lint/verify_no_commercial_remittance.py` — G7
- `40-engine/kotoba_murakumo/` — sibling package precedent (downstream
  consumer outside the kotoba subrepo)
- ADR-2605282200 — kawase-yui charter
- ADR-2605282300 — kotoba_murakumo relocation pattern (applies symmetrically here)
