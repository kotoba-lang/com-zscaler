# 20-actors/kanayama — CLAUDE.md

## Identity

- **Name**: kanayama (金山 / かなやま — Shinto Kanayamahiko-no-mikoto 金山彦命 + Kanayamabime-no-mikoto 金山姫命, Kojiki-recorded deities of metal and mining; elemental complement to watatsumi 水)
- **DID**: `did:web:etzhayyim.com:kanayama`
- **ADR**: ADR-2605252400 (R0 scaffold, 2026-05-25)
- **Status**: R0 scaffold — all cells import-time RuntimeError on `.solve()`
- **Parent actor**: etzhayyim religious-corp (circular metallurgy Tier-B)
- **Wave 1 reference**: closed-loop aluminum UBC recycling (Novelis Latchford / Constellium Neuf-Brisach / Speira Grevenbroich class)

## Architecture

9 Pregel cells implementing 5-layer closed-loop process (L1 → L2 → L3 → L4 → L5) + 2 cross-cutting:

```
intake_qa → decoating_separation → melting_furnace → dc_casting → hot_rolling → cold_rolling_finishing
 (L1, naphtali)   (L2, zebulun)      (L3, joseph)    (L4, simeon)  (L5a, dan)       (L5b, dan)
                                          ↓
                                    dross_recovery (cross, joseph)
                                          ↓
   air_emissions_audit (cross, levi) ←──────┘
                                          ↓
   mass_balance_binder (terminal, judah) — kotoba-datomic anchor
```

## Robotics Fleet (R0 reservation only)

| Robot | Class | Status | Function |
|---|---|---|---|
| Kamado (竈) | Refractory furnace tender | R1+ reservation | Slag scoop, temperature probe, refractory inspection |
| Yokin (溶金) | Molten-metal pour manipulator | R1+ reservation | Wave 1 Al ~720°C; Wave 2 steel ~1600°C thermal upgrade |
| Migaki (磨き) | Rolled-coil surface inspector | R1+ reservation | Visual + eddy-current + UT thickness |
| Otete-thermal | kuni-umi Otete high-temp variant | R1+ reservation | High-temp manipulator |
| Mimi-thermal | kuni-umi Mimi high-temp metrology | R1+ reservation | Pyrometer + thermocouple metrology |
| Funamori | kuni-umi class reuse | R3 reuse | UBC bulk surface logistics (international maritime) |

**G1 + G7**: All firmware open-source (Apache 2.0 + Charter Rider).

## Constitutional Gates (G1–G14)

**IMMUTABLE R0–R3.** Stored in `manifest.jsonld` under `kanayama:constitutionalGates`. Changes require Council Lv6+ supermajority + new ADR.

See `ADR-2605252400` §4 for definitions. Key enforcement:

- **G1 + G7**: All firmware (furnace, casting, rolling, robotics) open-source
- **G2**: **Mass-balance audit** — `input_mass = output_metal + dross + emission` ≥98% closure, kotoba-datomic anchor
- **G3**: Per-batch + per-coil IPFS-pinned photo + video
- **G4**: Every pour signed by witness quorum ≥2 distinct robots
- **G8**: Air emissions ≤ EU IED 2010/75/EU + 日本大気汚染防止法 + EN 12457 leachate
- **G12**: Recovery rate ≥95%; energy ≤6 kWh/kg for Wave 1 Al
- **G13**: Renewable + grid-balanced energy only (captive coal / petcoke prohibited)
- **G14**: Waste outputs IPFS lot-tracked + quarterly §2(h) report

## Non-Goals (N1 = scope boundary; N2–N8 excluded)

**N2–N8 EXCLUDED from R0–R3 scope** (amendment requires Council Lv6+ supermajority + new ADR). **N1 amended by ADR-2606161700**: primary mining is NOT constitutionally forbidden — the Charter gates extraction by a multi-generational (子・孫) × wellbecoming RISK assessment (Rider §2(l) v3.2), not by a blanket ban. N1 is a **scope boundary** (kanayama is the recovery actor, recovery-first by preference), not an immutable recycling-only invariant.

- N1: Primary mining is **out of kanayama scope** (bauxite / iron ore / copper ore / etc.) — recovery-first by preference, **not extraction-forbidden**; primary extraction → own actor + §2(l) multi-gen risk gate (ADR-2606161700)
- N2: Hall-Héroult primary Al electrolysis + equivalent primary smelting
- N3: Munitions casing / brass cartridge recovery
- N4: Nuclear-decontamination metal recovery
- N5: Proprietary alloy NDA
- N6: E-waste integrated PCB/IC co-processing
- N7: Deep-sea polymetallic nodule feedstock
- N8: Conflict-mineral feedstock without source attestation (3TG sourcing transparency mandate)

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.kanayama`

**Records (8 types, R0 stubs)**:

1. `com.etzhayyim.kanayama.intakeRecord` — L1 UBC bale weighing + QA
2. `com.etzhayyim.kanayama.decoatingAttestation` — L2 de-coating + shred + sort
3. `com.etzhayyim.kanayama.meltingAttestation` — L3 twin-chamber melt + alloy
4. `com.etzhayyim.kanayama.dcCastingAttestation` — L4 DC slab cast + homogenization
5. `com.etzhayyim.kanayama.rollingAttestation` — L5a hot rolling pass log
6. `com.etzhayyim.kanayama.coilQualificationRecord` — L5b final coil QA
7. `com.etzhayyim.kanayama.airEmissionsAuditRecord` — cross-cutting stack monitoring
8. `com.etzhayyim.kanayama.silenRecyclingReview` — Council 5-of-7 Safe attestation

**Deferred to R1+**: Full schema definitions.

## Pregel Cells (Detailed)

### intake_qa (L1)
- **Murakumo node**: naphtali
- **Input**: UBC bale (weight + ID)
- **Output**: `intakeRecord`
- **Key checks**: Cl residue, moisture %, magnetic impurity (Fe), non-Al non-magnetic contamination

### decoating_separation (L2)
- **Murakumo node**: zebulun
- **Input**: `intakeRecord`
- **Output**: `decoatingAttestation`
- **Process**: ~500°C rotary de-coater (lacquer/paint burnoff captured + filtered), rotary shredder, magnetic + eddy-current separation

### melting_furnace (L3)
- **Murakumo node**: joseph
- **Input**: `decoatingAttestation`
- **Output**: `meltingAttestation`
- **Process**: Twin-chamber Al furnace ~720°C, N₂ / Cl₂ degas, salt-flux refining, 3xxx / 5xxx alloy adjust

### dross_recovery (L3 cross-cutting)
- **Murakumo node**: joseph
- **Input**: melt dross stream
- **Output**: secondary Al recovery record (G14)
- **Process**: Salt-cake processing → K-salt + secondary Al; never standalone disposal (G14 + §2(g))

### dc_casting (L4)
- **Murakumo node**: simeon
- **Input**: `meltingAttestation`
- **Output**: `dcCastingAttestation`
- **Process**: DC (Direct Chill) slab casting (1m × 2m × 8m), homogenization 540–580°C × 12–24h

### hot_rolling (L5a)
- **Murakumo node**: dan
- **Input**: `dcCastingAttestation`
- **Output**: `rollingAttestation`
- **Process**: Hot rolling ~500°C, multi-pass reduction

### cold_rolling_finishing (L5b)
- **Murakumo node**: dan
- **Input**: `rollingAttestation`
- **Output**: `coilQualificationRecord`
- **Process**: Cold rolling + temper, 0.27 mm gauge for can-stock, surface inspection (Migaki)

### air_emissions_audit (cross-cutting)
- **Murakumo node**: levi
- **Output**: continuous compliance record vs EU IED + 大気汚染防止法
- **Monitored**: PFC, SO₂, NOx, particulate, dioxin, VOC

### mass_balance_binder (terminal)
- **Murakumo node**: judah
- **Input**: all prior records
- **Output**: mass-balance kotoba-datomic anchor (G2 + G14, ≥98% closure)

## Build & Deploy (R0 → R1)

**R0 status**: Scaffold only. No physical recycling. All cells raise `RuntimeError("kanayama R0 scaffold: activate via Council ADR-2605252415 post-ratification")` on `.solve()` call.

**R1 activation trigger**:
1. ADR-2605252415 authored + Council Lv6+ vote
2. Certified metallurgist SME onboarded (Council attestation gate)
3. Benchtop ≤1 kg pot melt + manual rolling PoC demonstrated
4. Cell source replaces RuntimeError with LangGraph stub bodies

## Testing (clj-port)

Per ADR-2606160842, the 9 cell state machines have been ported 1:1 from Python to
`.cljc` (`cells/<cell>/state_machine.cljc` + `test_state_machine.cljc`) and the Python
cell tree (LangGraph `cell.py` R0 wrappers + `state_machine.py` + `__init__.py`) has been
pruned — the cljc state machines are now the canonical logic. The LangGraph graph-building
was an R0 framework leg (`.solve()` raised `RuntimeError` until R1) and was not ported.

**Run the suite** (bb / clojure.test):

```bash
bash 20-actors/kanayama/run_tests.sh
```

Covers `methods.test-charter-gates`, `py.test-agent`, and all 9 cell
`cells.<cell>.test-state-machine` namespaces (46 tests / 145 assertions green).

## Related Files

- `/20-actors/kanayama/manifest.jsonld` — DID + cell registry + gates + non-goals
- `/90-docs/adr/2605252400-kanayama-circular-metallurgy-r0.md` — Master ADR
- `/20-actors/watatsumi/README.md` — Elemental sibling (水)
- `/CLAUDE.md` — Religious-corp status table row 46
