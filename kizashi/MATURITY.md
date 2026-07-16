# kizashi 兆 — Maturity

**Stage: R0** (scaffold) — ADR-2605312700. Non-invasive body-scan / sign-sensing instrument
layer (L4 Care). UPSTREAM of clinical adjudication: kizashi senses → mitate diagnoses →
iyashi treats. Non-diagnostic, anti-pseudoscience, encrypted, consent-bound.

| Dimension | State |
|---|---|
| Lexicons | ✅ 6 under `com.etzhayyim.kizashi.*` (scanSession/modalityObservation/modalityCapability/attributionReport/triageReferral/wellbecomingTrajectory) |
| Cells | 🟡 6 path-reserved (import-time RuntimeError per G2 privacy invariant) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ `methods/test_charter_gates.cljc` — **7 tests, green** (added 2026-06-17); `./run_tests.sh` |
| Methods | 🟡 offline engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G3 non-diagnostic** — `attributionReport` const `disclaimer` ("確定原因ではありません…受診を推奨") +
  required `consultRecommendation`; NO `diagnosis`/`prescription`/`treatment`/`medication`/`icd*`
  field in any kizashi lexicon (sign-sensing only; mitate/iyashi own diagnosis).
- **G10 anti-pseudoscience** — `modalityCapability` requires `canDetect` + `cannotDetect` +
  `councilAttestationCid` + `evidenceGrade` (incl. `X-excluded-pseudoscience`) + `regulatoryClass`.
- **G4 medical-device boundary** — `regulatoryClass` ∈ {non-regulated-wellness, samd-software,
  regulated-medical-device, ionizing-licensed-facility-only}.
- **G2 encrypted + consent** — scan session requires `encryptedPayloadCid` + `scanConsentCid`;
  observation requires `encryptedPayloadCid`.
- **referral routing** — `triageReferral.targetActor` ∈ {mitate, iyashi, kokoro} (sensing is
  upstream; clinical adjudication is delegated, never self-performed).

## R0 → R1 gate

Council Lv6+ + modalityCapability ledger Council-attested (evidence grades) + PMDA/SaMD
boundary review; cells import-gated until then.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `kizashi.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
