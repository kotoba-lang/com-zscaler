# 20-actors/himawari — CLAUDE.md

## Identity

- **Name**: himawari (向日葵 — "sunflower / sun-turning"; heliotropic echo of solar trackers + the manufacture of light-capturing surfaces; deliberate sibling resonance with hikari 光)
- **DID**: `did:web:etzhayyim.com:actor:himawari` (canonical actor form per ADR-2606013800; resolves at `/actor/himawari/did.json`)
- **ADR**: ADR-2606021200 (R0 scaffold, 2026-06-02)
- **Parent ADR**: ADR-2605261000 (Liberation Ladder — feeds L2 Sustenance via hikari)
- **Tightest sibling**: hikari (ADR-2605261100 — generation/install)
- **Status**: R0.1 — all 7 cell solvers + 7 lexicons **implemented**, now as `.cljc` state machines (75 tests / 155 assertions green via `run_tests.sh`; the original Python `cell.py`/`state_machine.py` per cell have been pruned — cljc is canonical, 2026-07-14). NOT operationally activated (no Pregel/Murakumo runtime wiring, no sim, no live kotoba entity materialization; deterministic-digest CIDs). Gated upstream by the R1 activation triggers below.

## What himawari is (and is not)

himawari **manufactures** the solar PV modules that hikari **installs**. It is the manufacturing half of the energy chain:

```
製造 (himawari) → 積込 (sarutahiko F10) → 輸送 (kami-autodrive) → 設置 (hikari)
```

- It is **NOT** the silicon iwakura/fuigo/tsukuru track (that is logic/compute ASIC fab, ADR-2605242500). himawari is **solar-grade** c-Si only (N1).
- It does **NOT** re-implement loading/transport robotics — it **composes** the already-landed, tests-green sarutahiko F10 LoaderRobot + kami-autodrive GNC + giemon AGV.

## Architecture

7 Pregel cells, manufacture → loading → outbound, fed by procurement:

```
supply_procurement (調達) ──feedstock──┐
                                       ▼
polysilicon_refine → ingot_wafer → cell_process → module_assembly
                                                        │
                                                        ▼
                                                 panel_loading (積込)
                                                        │
                                                        ▼
                                                 outbound_logistics (輸送) ──→ hikari install
```

Each cell = 1 Pregel graph. R0.1: every cell's `.solve()` is implemented (no RuntimeError stubs); cell→lexicon and cell→composed-actor wiring is real (see below). kotoba write-back via `datalog.transact` degrades to compute-only/no-op without a host binding (local dev) — never a fake write.

## Structural anchors (CRITICAL gates)

### G2: Feedstock provenance on-chain — closes hikari §G2
- NO XUAR / forced-labor polysilicon, EVER. No conflict-mineral In/Ga.
- Full polysilicon→module chain-of-custody CID-anchored per lot.
- This is the *structural* fix for hikari §G2 (which otherwise relies on vendor self-attestation of purchased modules). Vertical integration is the point of this actor.

### G4: Renewable-only process heat (inherits hikari G4/G5)
- Fab process heat + power from hikari renewable only. NO fossil, NO nuclear, at any tier.
- Net-positive lifecycle energy: EPBT < module service life with margin.
- Couples himawari R2 throughput to hikari R2 energy budget — a PV fab is ~MW-scale; mitigation is batch / lower-duty-cycle operation (mirrors silicon Wave 2 mitigation in hikari ADR).

### G7: Labor-liberation transparency
- Every human task removed by automation is logged to the Liberation Metric (ADR-2605261000).
- PV manufacture is highly automatable; this gate makes the automation **accounted 労働解放**, never opaque displacement. This is the mission tie-in — automation here is the *point*, but it must be measured and transparent.

### G12: No external commercial PV sale
- Modules are for **internal hikari install only** (SBT↔SBT internal carve-out, ADR-2605192115 §3). Surplus → community-benefit, never market.

## Robotics Fleet (compose, do not re-implement)

| Robot | Class | Function | Lineage | Status |
|---|---|---|---|---|
| F10 LoaderRobot | straddle loader | `panel_loading` 積込 | sarutahiko (ADR-2606013100) | 🟢 14 tests |
| AGV | floating-base cart | intra-fab transport | giemon (ADR-2606010030) | 🟢 13 tests |
| GNC | autonomy layer | `outbound_logistics` truck/ship | kami-autodrive (ADR-2606010600) | 🟢 9 tests |
| Otete | precision arm | cell handling / stringing / framing | kuni-umi | inherited |
| Mimi | metrology | flash IV + EL imaging + thermal-IR | kuni-umi | inherited |
| Hinata (日向) (R2+) | lamination-press + stringer | autonomous module assembly | new class | separate mech-design ADR |

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.himawari`

7 records (full atproto-style `record` defs, `key: "tid"`; validated by validate-lexicons.py; materialize to kotoba Datom/EAVT):

1. `polysiliconProvenanceAttestation` — feedstock lot provenance (XUAR-exclusion + §2(g) audit, on-chain) — emitted by `polysilicon_refine` + `supply_procurement`
2. `waferBatchRecord` — ingot/wafer batch + kerf recovery + yield — emitted by `ingot_wafer`
3. `cellBatchRecord` — cell process params (open) + flash IV + bin — emitted by `cell_process`
4. `moduleAttestation` — finished-module BOM + flash + EL image CID + EPBT block — emitted by `module_assembly`
5. `loadingRecord` — 積込 robot cycle + pallet + carrier (F10 lineage) — emitted by `panel_loading`
6. `outboundManifest` — transport handoff (carrier DID, route, kami-autodrive class) — emitted by `outbound_logistics`
7. `silenHimawariReview` — Council attestation scope (provenance + chemistry + circularity + liberation-metric across all R-stage + gate axes)

## Pregel Cells (R0.1 — solvers implemented)

All 7 cells' logic is a `state_machine.cljc` `solve` fn (ported off the original
`cell.py`/`state_machine.py`, since pruned) + a `test_state_machine.cljc` suite
(75 tests / 155 assertions total, all green via `run_tests.sh`). Cell→lexicon +
cell→composed-actor wiring:

| Cell | Emits | Routes to | Composes (not re-implemented) | Tests |
|---|---|---|---|---|
| `polysilicon_refine` | polysiliconProvenanceAttestation | ingot_wafer | — | 15 |
| `ingot_wafer` | waferBatchRecord | cell_process (+ kerf → polysilicon_refine) | — | 5 |
| `cell_process` | cellBatchRecord | module_assembly | kuni-umi Otete + Mimi | 5 |
| `module_assembly` | moduleAttestation | panel_loading | kuni-umi Otete + Mimi | 17 |
| `panel_loading` | loadingRecord | outbound_logistics | sarutahiko F10 LoaderRobot (LoadPhase mirror) | 5 |
| `outbound_logistics` | outboundManifest | hikari site (G13) | kami-autodrive GNC + open-customs-clearance BPMN + funadaiku ship class (R3+) | 13 |
| `supply_procurement` | polysiliconProvenanceAttestation (per-lot) + CycloneDX SBOM | poly/cell feedstock | okaimono (ring + SBT settlement + TitheRouter) + giemon CycloneDX→kotoba bridge | 15 |

**Honest R0.1 caveats** (not overclaiming): solvers are logic-complete and tested, but NOT yet wired into the himawari Pregel/Murakumo runtime topology; no sim physics integration; CIDs are deterministic tamper-evident digests (real IPFS CIDv1 / Base-L2 anchoring is operator-gated substrate work); kotoba entity materialization is compute-only / no-op without a host `datalog` binding. Module signature is a deterministic content-binding HMAC standing in for the off-cell Ed25519 device key (substrate-boundary). The okaimono/giemon LangGraph stack is broken in this env, so cell_process/outbound use an in-process sequential super-step driver fallback shaped like the StateGraph DAG (swaps to canonical StateGraph automatically when LangGraph is fixed).

### R1 activation triggers
**R1 design landed → ADR-2606022300** (benchtop module-assembly PoC + Parcel Requirement Spec; activation gated A1∧A2∧A3, all pending as of 2026-06-02).
1. ADR-2606021200 Council Lv6+ ratify
2. ≥1 PV-process engineer on Council technical advisory
3. ≥1 LANDS.md brownfield/existing-industrial parcel registered
4. G2 feedstock provenance audit framework operational (on-chain chain-of-custody)
5. G3 high-GWP gas abatement framework Council-ratified

## Build & Deploy

**R0.1 status**: All 7 cell solvers implemented (logic-only, no runtime/sim/live-kotoba) as `.cljc` state machines. No stubs remain.

**Tests** (bb/clj, wired into the fleet green-check; 75 tests / 155 assertions green):
```bash
20-actors/himawari/run_tests.sh
```

**WASM build** (`deploy/agent.cljc` → `deploy/agent.wasm` via kotoba-clj, ADR-2606222100 — see `deploy/README.md` for the superseded prior componentize-py build this replaced):
```bash
bb 20-actors/himawari/deploy/build_wasm.clj
```

## Related Files

- `/20-actors/himawari/manifest.jsonld`
- `/90-docs/adr/2606021200-himawari-solar-pv-manufacturing-r0.md` — Master ADR
- `/90-docs/adr/2605261100-hikari-energy-tier-b-actor-r0.md` — Sibling (generation/install)
- `/90-docs/adr/2606013100-sarutahiko-truck-factory-full-robotics-and-loader.md` — F10 LoaderRobot
- `/90-docs/adr/2606010600-kami-autodrive-gnc-autonomy-layer.md` — outbound transport
- `/90-docs/adr/2605312330-giemon-part-graph-sbom-kotoba-fleet-cve-svelte.md` — SBOM procurement
- `/20-actors/kuni-umi/README.md` — Otete/Mimi class lineage
- `/CLAUDE.md` — Religious-corp status table
