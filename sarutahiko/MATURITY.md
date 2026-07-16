# sarutahiko 猿田彦 — Maturity

**Stage: R0** (scaffold) — ADR-2605252500. Civilian Class-8 cargo truck manufacturing
(wadachi's mfg-side sibling). Open-source ECU, fuel-transition phase-gate, ≤SAE L4, EoL
recyclability ≥90% (closes loop with kanayama). Civilian only (military trucks N1 excluded).

| Dimension | State |
|---|---|
| Lexicons | ✅ 9 under `com.etzhayyim.sarutahiko.*` (frame/powertrain/cabBody/marriage/paint/electrical/roadTest/emissionsAudit/silenVehicleReview) — rich const ledger |
| Cells | ✅ 9 path-reserved; state-machine tests now pass (syntax bug fixed, below) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ **3 suites green** — `methods/test_charter_gates.cljc` (**7**, added 2026-06-16) + `cells/test_state_machines.py` + `py/test_agent.py`; `./run_tests.sh` aggregates all |
| Methods | 🟡 cells R0 (`.solve()` Council-gated); offline engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G1/N8 open firmware** — `electricalAttestation` const `g1Enforcement="active"` +
  `n8Enforcement="active"` + `proprietaryNdaPresent=false`; requires `openSourceVerification`.
- **G7 fuel transition** — `powertrainAttestation` const `g7Enforcement="active"`;
  `powerTrainType` is exactly {B100-biodiesel-hybrid, diesel-hybrid, LFP-battery, H2/NH3/methanol-fuel-cell}
  (no pure-fossil R2+); requires `fuelGuard`.
- **G8 emissions + VOC** — `emissionsAuditRecord` cites Euro 7; `paintAttestation` const
  `vocLimitGPerL=100`.
- **G12 speed cap** — `roadTestRecord` const `maxSpeedLimitKmh=90` + `g12KpiCheck`.
- **frame** — const `specStraightnessLimitMmPerM=1.0`; grade is HSLA-only {590,780,980}.
- **G4 witness quorum** — `frameAttestation` + `marriageAttestation` require `attestingRobots`.

## Bug fixed this iteration (2026-06-16)

`cells/vin_attestation_binder/{state_machine,cell}.py` had a **broken Python identifier**
from a bad global rename: `kotoba-datomicAnchor` / `transition_to_kotoba-datomic_anchored`
(hyphen = `SyntaxError: illegal target for annotation`), which made
`cells/test_state_machines.py` fail at import. Fixed the 4 broken **identifier** sites
(field / function def / 2 attribute accesses) + 2 in `cell.py` (import + call) → underscore
form. String literals / enum values / the record key left unchanged (no semantic change).
Both files now `ast.parse` OK; cells suite green.

> ⚠️ **Same hyphenated-identifier bug also exists** in `kanayama/cells/mass_balance_binder/`,
> `watatsumi/cells/class_certification_binder/`, `yamabiko/cells/homologation_binder/`
> `state_machine.py` — follow-up fixes for those actors.

## R0 → R1 gate

silenVehicleReview `r1-benchtop-prototype` + Council Lv6+ + automotive SME; cell `.solve()`
stays R0-gated.

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `sarutahiko.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
