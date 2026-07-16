# 20-actors/mitsuho — CLAUDE.md

## Identity

- **Name**: mitsuho (瑞穂 — "abundant rice ears", ancient honorific name for Japan)
- **DID**: `did:web:etzhayyim.com:mitsuho`
- **ADR**: ADR-2605261015 (R0 scaffold, 2026-05-26)
- **Parent ADR**: ADR-2605261000 (Liberation Ladder — L2 Sustenance Tier gate)
- **Status**: R0 scaffold — all cells import-time RuntimeError
- **Sibling actors**: yakushi (drugs), mitate (diagnosis), hagukumi (care), manabi (education), hikari (energy), tatekata (construction), wadachi (mobility), silicon (chips)

## Architecture

5 Pregel cells arranged in linear production sequence:

```
field_cultivation ──┐
                    ├─→ harvest_robotics → food_preservation → (distribution to hagukumi/adherents)
aquaculture ────────┤
                    │
alt_protein_fermentation ─┘
    (naphtali)    (zebulun)    (levi)      (joseph)            (simeon)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on MST (`com.etzhayyim.mitsuho.*`).

## Robotics Fleet (R0 uses kuni-umi inherited)

| Robot | Class | Function | Firmware |
|---|---|---|---|
| Giemon | crawler + arm | tractor-equivalent, harvest | `kuni-umi.giemon.firmware` (open-source WASM) |
| Otete | chem-resist arm | seeding, pruning, fine harvest | `kuni-umi.otete.firmware` (open-source Rust) |
| Mimi | metrology | crop health, soil sampling | `kuni-umi.mimi.firmware` (open-source) |
| Sora | drone | aerial survey, spot treatment | `kuni-umi.sora.firmware` (open-source) |
| Tsumugi (R2+) | greenhouse / vertical-farm | precision tending | deferred mech-design ADR (parallel to hanami precedent ADR-2605260230) |
| Kusawake (R1+) | 4WD/4WS electric autonomous wheeled platform ≤300 kg | weed control / livestock herd / perimeter scouting | open Apache 2.0 + Charter Rider; ARM64/RISC-V SoC; mfg = suki Wave 2 (ADR-2605261500); sim = e7m-sim (ADR-2605261600); swagbot methodology reference; SAE J3016 Level 3 ceiling per ADR-2605252615 |

**CRITICAL**: All firmware open-source (Apache 2.0 + Charter Rider) per G1. No proprietary control loops.

## Constitutional Gates (G1–G14) — IMMUTABLE R0–R3

See ADR-2605261015 for full definitions. Key enforcement notes:

- **G2** (Seed sovereignty): All varietals from open-source seed banks (Svalbard / NAVDANYA / national gene banks). Schema rejects any `seedSource` field referencing patented commercial lines.
- **G4** (Soil regeneration): Annual soil-carbon + microbial-diversity assay. `harvestAttestation` schema requires `soilCarbonDeltaTonsCo2Eq` field; negative value → halt + Council review.
- **G6** (No synthetic pesticides): `cropPlanAttestation.pesticideManifest` rejects neonicotinoid / glyphosate / paraquat / organochlorine class.
- **G7** (No GMO without attestation): `cropPlanAttestation.varietalAttestationCid` references Council Lv6+ ≥3 sig if any CRISPR/transgenic varietal.

## Non-Goals (N1–N10) — EXCLUDED from R0–R3

Critical for future implementers:

- **N1** (No animal slaughter): R0-R3 plant + aquaculture + alt-protein only. R4+ requires Council ethics gate (Lv7 unanimity).
- **N5** (GMO Council-gated): Not absolute prohibition; case-by-case attestation.
- **N9** (No ocean factory-fishing): Freshwater aquaculture only. Marine = separate Funamori-class actor.

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.mitsuho`

5 records (R0 stubs; full schemas R1+):

1. `parcelAttestation` — soil + water + climate baseline + biodiversity-no-harm
2. `cropPlanAttestation` — per-season plan: varietals + rotation + organic confirmation
3. `harvestAttestation` — yield + quality + witness sigs + IPFS photo CID + soil delta
4. `foodLotAttestation` — preserved food lot: kJ/kg + macros + shelf-life + handling
5. `silenAgricultureReview` — Council attestation scope

## Pregel Cells (R0 stub bodies)

All R0 cells raise `RuntimeError("mitsuho R0 scaffold: activate via Council ADR post-ratification")` on first super-step. Live cell logic deferred to R1.

### R1 activation trigger
1. ADR-2605261015 Council Lv6+ ratify
2. ≥1 agronomist on Council technical advisory
3. ≥1 LANDS.md parcel registered (≥0.01 ha for R1; ≥1 ha for R2)
4. Charter Rider §2 scanner cleared for crop-plan + seed-source manifest schema

## Build & Deploy

**R0 status**: Scaffold only. No live agriculture. All cells raise on `solve` (no live actuation).

mitsuho is fully ported py→cljc (ADR-2605261015 / 2606160842): the canonical impl is
clojure-on-babashka — the agent handlers (`methods/agent.cljc`), the constitutional-gate
suite (`methods/test_charter_gates.cljc`), and the 6 R0 cell scaffolds
(`cells/<name>/cell.cljc`, each `solve` raises until Council activation). The legacy
Python (`py/agent.py`, the 6 `cells/<name>/cell.py`, the `test_*.py`) was pruned once
the clj port reached parity (py↔clj deep-parity was verified before removal).

**Test**:
```bash
cd 20-actors/mitsuho
./run_tests.sh           # cljc-only: methods.test-agent + methods.test-charter-gates + cells.test-cells
```

## Related Files

- `/20-actors/mitsuho/manifest.jsonld` — DID + cell registry
- `/90-docs/adr/2605261015-mitsuho-food-agriculture-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — Liberation Ladder (L2 gate)
- `/20-actors/kuni-umi/README.md` — Robotics class lineage
- `/CLAUDE.md` — Religious-corp status table
