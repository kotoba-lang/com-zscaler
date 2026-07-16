# himawari (向日葵) — Solar PV Module Manufacturing Tier-B Actor

**DID**: `did:web:etzhayyim.com:himawari`
**Namespace**: `com.etzhayyim.himawari.*`
**ADR**: ADR-2606021200 (R0 scaffold)
**Status**: R0.1 (2026-06-02) — all 7 cell solvers + 7 lexicons **implemented** (88 pure-logic tests green; import smoke clean). NOT operationally activated: no Pregel/Murakumo runtime wiring, no sim, no live kotoba entity materialization, deterministic-digest CIDs (not real IPFS/Base-L2 anchors). Gated upstream by the R1 activation conditions below.
**Parent ADR**: ADR-2605261000 (Liberation Ladder — feeds L2 Sustenance energy gate via hikari)
**Tightest sibling**: hikari (ADR-2605261100 — generation/install actor)

## Overview

Solar-grade **crystalline-silicon** PV module manufacturing actor — polysilicon feedstock QA → ingot/wafer → cell process → module assembly → flash/EL test — **plus** finished-module loading robotics, outbound logistics handoff, and feedstock/consumable procurement.

himawari makes the panels that **hikari** installs. Together with the already-landed loading (sarutahiko F10 LoaderRobot), transport (kami-autodrive GNC), and procurement (SBOM↔kotoba + okaimono) substrate, the energy supply chain is end-to-end first-party:

```
製造 (himawari) → 積込 (sarutahiko F10) → 輸送 (kami-autodrive) → 設置 (hikari) → L2 Sustenance energy
```

## Why this actor exists (the constitutional gap it closes)

hikari **§G2** forbids XUAR forced-labor polysilicon and conflict minerals, with per-lot Council audit. Satisfying that by *purchasing* certified modules is fragile (provenance-laundering, audit opacity) and routes value through the commercial market the charter routes around. himawari closes §G2 **structurally** via first-party, on-chain feedstock provenance (G2 below) — vertical integration, not vendor self-attestation.

It also fills the only manufacturing gap left in the energy chain: the substrate already has factory actors for trucks (sarutahiko), generic plant (giemon), megacasting (igata), shipbuilding (funadaiku), and pharma (yakushi) — but **no PV manufacturing actor** until now.

## Distinct from the silicon (iwakura) track

`silicon` (iwakura/fuigo/tsukuru, ADR-2605242500) is **logic/compute** ternary-ASIC fab (sky130 GDSII). himawari is **solar-grade** silicon — different purity (6N vs 9N+ EG-Si), different downstream (wafer→cell→module vs lithography). Shared metallurgy heritage only (N1).

## Robotics Classes (R0–R2 compose landed, tests-green classes)

| Class | Role | Lineage | Status |
|---|---|---|---|
| sarutahiko **F10 LoaderRobot** | 積込 — `panel_loading` palletize + carrier load | ADR-2606013100 | 🟢 LANDED (14 tests) |
| giemon **AGV** | intra-fab transport | ADR-2606010030 | 🟢 LANDED (13 tests) |
| kami-autodrive **GNC** | `outbound_logistics` (truck/ship) | ADR-2606010600 | 🟢 LANDED (9 tests) |
| kuni-umi **Otete** | cell handling / stringing / framing | kuni-umi | inherited |
| kuni-umi **Mimi** | flash IV + EL imaging + thermal-IR | kuni-umi | inherited |
| **Hinata (日向)** (R2+) | autonomous lamination-press + stringer tending | new class | separate mech-design ADR (hanami precedent) |

himawari **composes** these; it does not re-implement them (DRY + honest R0).

## Pregel Cells (7 — solvers implemented, R0.1)

All 7 cells now have real `solve()` logic (R0 RuntimeError stubs removed) + a pure-logic standalone test file (`python3 test_*.py`; **88 tests total, all green**). Each emits its lexicon record and writes `:himawari.*` / per-namespace EAVT datoms to the kotoba host (`datalog.transact`); with no host binding (local dev) it degrades to compute-only / no-op and **never fakes a write**. CIDs are deterministic tamper-evident digests standing in for real IPFS CIDv1 / Base-L2 anchors (produced by the substrate at operator-gated anchor time). Cells **compose** the landed robotics/helpers below; they do not re-implement them.

| Cell | Node | Phase | Input → Output (lexicon) | Composes | Tests |
|---|---|---|---|---|---|
| `polysilicon_refine` | judah | solar-grade polysilicon QA + on-chain provenance (XUAR-exclusion) | feedstockLot → `polysiliconProvenanceAttestation` → `ingot_wafer` | — | 12 🟢 |
| `ingot_wafer` | issachar | ingot growth → wafer slicing + kerf recovery (mass-balance) | `polysiliconProvenanceAttestation` → `waferBatchRecord` → `cell_process`; recovered kerf-Si → `polysilicon_refine` (recycled-kerf) | — | 13 🟢 |
| `cell_process` | benjamin | texture → diffusion/PECVD → metallization → flash test (G3 gas abatement, G6 Ag→Cu) | `waferBatchRecord` → `cellBatchRecord` → `module_assembly` | kuni-umi Otete + Mimi | 14 🟢 |
| `module_assembly` | asher | stringing → lamination → framing → J-box → flash + EL | `cellBatchRecord`, bom → `moduleAttestation` → `panel_loading` | kuni-umi Otete + Mimi | 14 🟢 |
| `panel_loading` | gad | 積込ロボット palletize + carrier load (G12 internal-only) | `moduleAttestation` → `loadingRecord` → `outbound_logistics` | **sarutahiko F10 LoaderRobot** (LoadPhase mirror) | 10 🟢 |
| `outbound_logistics` | dan | transport handoff → hikari site (G13 own-module→hikari only) | `loadingRecord` → `outboundManifest` | **kami-autodrive GNC** VehicleClass + **open-customs-clearance BPMN** + funadaiku ship class (R3+) | 9 🟢 |
| `supply_procurement` | simeon | 調達 — commons-first + SBOM↔kotoba; §2(g) per-lot audit | demand → `polysiliconProvenanceAttestation` (per-lot) + CycloneDX SBOM | **okaimono** ring ordering + SBT settlement intent + TitheRouter; **giemon** CycloneDX→kotoba bridge | 16 🟢 |

## Composition wiring (procurement / loading / outbound)

These three cells are the explicit "compose-not-clone" seams; the helpers/actors they call are already landed and tests-green:

- **`supply_procurement` → okaimono + giemon (調達).** Routes each feedstock/consumable need through okaimono's commons-first ring ordering: `recycled-kerf` → commons (closed-loop), internal Ring-1 producers (kanayama/hikari) → internal via okaimono `check_sbt_eligibility` + `build_settlement_intent` (so the SBT↔SBT carve-out and the exact 10% TitheRouter split `gross == tithe + payout` are inherited; intent-only until an operator ref is present), else external operator-gated purchase handoff (no internal value inflow, no tithe). Emits a CycloneDX 1.5 SBOM projected to kotoba `:cdx/*` via the giemon `cyclonedx_to_ingest` bridge (ADR-2605312330, purl = CVE/recall join key) and a per-lot `polysiliconProvenanceAttestation` (XUAR-exclusion + §2(g) audit). G2 feedstock guards (N1 solar-grade-only, N6 no-XUAR) refuse before any order is built. Helper import is path-resolved relative to the cell file (cwd-independent); degrades to inline-equivalent logic if okaimono/giemon are absent.
- **`panel_loading` → sarutahiko F10 (積込).** Mirrors the authoritative Rust `LoadPhase` enum (`ToPick/Carry/Lower/Done`) from the sarutahiko factory engine, consumes the loader's reported terminal phase, rejects invented phases, palletizes serials at tray capacity. Default `loaderRobotDid` points at the sarutahiko F10 lineage (does not mint a himawari one). G12 refuses non-internal carriers; G7 always content-addresses the displaced-manual-task manifest.
- **`outbound_logistics` → kami-autodrive + customs + funadaiku (輸送).** 5-node pipeline `init → bind_carrier → customs_clear → plan_route → emit_manifest`. Selects a real kami-autodrive `VehicleClass` (`car/ship/drone/aircraft`); marine maps to the `ship` class (funadaiku/funamori R3+ seam). Cross-border legs build the input to the existing `com.etzhayyim.apps.customsClearance.lodgeDeclaration` against the `open-customs-clearance` BPMN (engine reused, not forked); domestic legs record `required: false`. G13/G12/N10: refuses any consignee not under `did:web:etzhayyim.com:hikari*`; sets `telemetryEncrypted=true`, `weaponizationPayload=false`.

## Constitutional Gates (G1–G14)

See ADR-2606021200. **IMMUTABLE** per R0. Structural anchors:

- **G2**: feedstock provenance on-chain per lot — **no XUAR/forced-labor polysilicon ever** (closes hikari §G2)
- **G4**: fab process heat + power from **hikari renewable only** — no fossil/nuclear (inherits hikari G4/G5); net-positive lifecycle energy
- **G7**: **labor-liberation transparency** — every human task removed by automation logged to the Liberation Metric (ADR-2605261000); no opaque displacement
- **G12**: **no external commercial PV sale** — modules for internal hikari install only (SBT↔SBT carve-out)
- G1 open firmware · G3 high-GWP gas abatement · G5 ≥90% circular · G6 Ag→Cu low-tox metallization · G8 full SBOM on-chain · G9 brownfield-only siting · G11 Ed25519 module provenance · G13 transport bound by kami-autodrive gates · G14 §2(h) Wellbecoming

## Non-Goals (N1–N10)

N1 no logic-fab (silicon track) · N2 no CdTe · N3 no Pb-perovskite · N4 no external commercial sales · N5 no proprietary firmware · N6 no XUAR feedstock · N7 no high-GWP venting · N8 no greenfield siting · N9 no fossil/nuclear process heat · N10 no external logistics carriage

## Roadmap

| Phase | Timeline | Scope | Gate |
|---|---|---|---|
| **R0** | 2026-06-02 | Scaffold. 7 cells RuntimeError. Composes landed robotics. | — |
| **R0.1** | 2026-06-02 | All 7 cell solvers + 7 lexicons implemented (88 logic tests green). Procurement composes okaimono+giemon; loading composes sarutahiko F10; outbound composes kami-autodrive+customs+funadaiku ship class. Logic-only — no runtime/sim/live-kotoba, deterministic-digest CIDs. | (still gated on R1 conditions before operational activation) |
| **R1** | post-Council | Benchtop **module-assembly** line PoC (lowest capex) + panel_loading + outbound PoC; feeds hikari R1. **Design landed → [ADR-2606022300](../../90-docs/adr/2606022300-himawari-solar-pv-r1-benchtop-module-assembly-poc.md)** (activation gated A1 Council ∧ A2 PV-engineer ∧ A3 brownfield parcel) | future ADR **(✅ drafted)** + PV-process engineer + LANDS brownfield parcel |
| **R2** | post-R1 | Pilot **cell + wafer** lines, ~MW/yr, hikari-R2-powered; supplies hikari R2 install | **L2 coupling** + 30-day comment + hikari R2 deployed |
| **R3** | post-R2 | **Polysilicon** vertical integration — closes hikari §G2; multi-line + full outbound mesh | 60-day review + multi-domain vote + hodoki EOL contract |

## Integration

- **Sibling**: hikari (consumes himawari modules for install)
- **Loading**: sarutahiko F10 LoaderRobot · **Transport**: kami-autodrive (+ funadaiku marine R3) · **Procurement**: SBOM↔kotoba + okaimono
- **End-of-life**: hodoki (ELV-style module recovery, G5)
- **Land**: LANDS.md brownfield/industrial parcel required R1+

## References

- `/90-docs/adr/2606021200-himawari-solar-pv-manufacturing-r0.md` — Master ADR
- `/90-docs/adr/2605261100-hikari-energy-tier-b-actor-r0.md` — Sibling (generation/install)
- `/90-docs/adr/2606013100-sarutahiko-truck-factory-full-robotics-and-loader.md` — F10 LoaderRobot
- `/90-docs/adr/2606010600-kami-autodrive-gnc-autonomy-layer.md` — outbound transport
- `/90-docs/adr/2605312330-giemon-part-graph-sbom-kotoba-fleet-cve-svelte.md` — SBOM procurement
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — L2 gate + G7 coupling
- `/CLAUDE.md` — Religious-corp status table
