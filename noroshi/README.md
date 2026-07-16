# noroshi 烽 — 光電融合 comms chip · ISAC · photonic packaging robotics

> **DID** `did:web:etzhayyim.com:actor:noroshi` · Tier-B · R0 · ADR-2606051600

烽 (狼煙, *beacon-fire*) is the oldest optical telecom: a watchtower **senses** a distant fire and
**relays** a coded message. One emission, two functions — which is exactly **ISAC** (Integrated
Sensing And Communication). noroshi is the **photonics-electronics convergence (光電融合)**
communication-chip actor: the silicon-photonic / co-packaged-optics (CPO) sibling of the **electronic**
`silicon`/`iwakura`/`fuigo` line and the RF `tsutae` comms device, and the transceiver-chip end of the
`watatsuna` submarine-cable medium.

## Three faces (each a verifiable `methods/` core)

| Face | What | Core | Result |
|---|---|---|---|
| **chip** | silicon-photonic / CPO comms-chip design + optical link budget | `methods/link_budget.py` | CPO closes a 2 km/100G link at **+10 dB margin** + **3.96× less energy/bit** than a pluggable; receiver sensitivity from a target BER (Q-factor + thermal-noise), PIN **and** APD (avalanche gain vs excess noise) |
| **isac** | one OFDM-JCAS waveform → communication capacity **and** range-Doppler sensing | `methods/isac_sim.py` | recovers a civilian object's range+velocity (single + **multi-target CLEAN** + **CA-CFAR detection** + **Pd-vs-SNR** characterisation); sweeps the **comms↔sensing power-split** tradeoff |
| **packaging** | photonic assembly robotics: fibre↔grating active alignment + laser safety | `methods/active_alignment.py` | two-stage (raster OR early-stop **spiral** acquisition → Hooke-Jeeves refine) finds the coupling peak to **<1 dB**, robust to a far/narrow-lobe start; IEC 60825 + civilian-use interlock |

## Charter shape (why this is charter-clean, not just a chip project)

- **Civilian by construction (G3/N1)** — optical power and ISAC sensing are civilian only.
  Weaponisation (directed-energy weapon, laser dazzler, fire-control/targeting radar) is
  **structurally unrepresentable** in the schema, lexicons, and `active_alignment.PERMITTED_USES`
  (the iwakura/nusa `:class` precedent).
- **Sensing ≠ surveillance (G4/N2)** — an ISAC estimate is an **object's** range+velocity; there is no
  `:person` target class, no biometric, no pattern-of-life (the watari invariant).
- **Clean-room open-EDA (G1/N5)** — open photonic + digital EDA only (GDSFactory / Meep / KLayout /
  Verilator / yosys / OpenLane + open PDK), extending the verified iwakura RTL→sky130 GDSII flow to
  photonics. No Cadence/Synopsys/Lumerical/Ansys, no NDA foundry PDK, no decompile/trademark.
- **Laser-safety soft-gate (G5/N3)** — IEC 60825 enclosure-interlock + class gate, best-effort
  soft-safety, **not** a certified controller (kotoba-os N2 precedent).
- **no-server-key (G7)** + **outward-gated (G8)** — tapeout / mask order / robot actuation are
  member/operator-signed; live fab / live laser / live actuation is Council Lv6+ (Class-3B/4 Lv7+).
- **displacement-dividend coupling (G2)** — packaging robots displace fibre-alignment technicians, so a
  live fleet requires a funded tenure-weighted cohort (ADR-2606032130).
- **kotoba-EAVT canonical state (G9)** + **`:representative` honesty (G10)** — every device, budget,
  waveform, estimate, and job is a Datom; no silicon exists, sims are arithmetic/DSP.

## R1 integrations (this session)

The three follow-ups, each a verifiable bridge that composes noroshi with an existing actor/engine:

| Bridge | Wires | Core | Result |
|---|---|---|---|
| **(c) optical-network resilience** | noroshi CPO chips ↔ **watatsuna** submarine-cable medium | `methods/cable_endpoint.py` | sizes the CPO-transceiver fleet at every cable's landings → per-chokepoint demand by **station-tag** AND **authoritative `:cable.seg/traverses` physical-crossing** views (luzon-strait top in both). Resilience, **never a target-list** (inherits watatsuna G2 / watatsumi N8) |
| **(a) ISAC sensor in the GNC loop** | noroshi ISAC ↔ **kami-autodrive** (ADR-2606010600) | `methods/kami_isac_bridge.py` + `wit/kami-isac.wit` | drives the ISAC estimator from a moving-object scenario → per-object range/velocity tracks (the `IsacSensor` plant). Civilian objects only (N1/N2) |
| **(b) PIC layout → budget loop** | noroshi chip face ↔ **open-EDA** (GDSFactory-shaped) | `methods/pic_layout.py` | emits neutral ModelOp layout plans for the **transmitter AND receiver** PIC (sumitsubo pattern); both waveguide lengths feed the end-to-end `link_budget.py`; real GDS write gated behind an optional `gdsfactory` import (G1/G8) |

**Honest integration state (G10)**: the `40-engine/kami-engine` submodule is unpopulated and
`gdsfactory` is not installed in this checkout, so (a) ships as a Python bridge + WIT contract (not a
compiled crate) and (b) as a ModelOp plan + gated GDS backend — the sumitsubo "op-list now, live tool
binding follow-up" pattern. (c) is a full offline join over the present watatsuna seed.

## Coded cells, wave 2: `device_design` + `reliability_qual` (this maturity pass)

Two of the six cells were pure `.edn` scaffolds with zero implementation (`:cell/entry` pointing at a
`cell.py` that didn't exist) until this pass. Both are now coded (`:cell/coded true`), joining
`active_alignment`:

| Cell | Core | Result |
|---|---|---|
| **`device_design`** (chip) | `methods/device-design.cljc` | NL-intent → civilian-gate (G1/G3/N1) → open-EDA ModelOp plan (delegates to `methods/pic-layout` for assembled `:cpo-module`/`:pic-link` kinds; a minimal one-op plan for a single discrete component) → a `:representative` photonicDevice record (G10) |
| **`reliability_qual`** (packaging) | `methods/reliability-qual.cljc` | a real Telcordia GR-468-SHAPE PASS/FAIL engine — 4 test types (thermal cycling / damp heat / mechanical shock / fibre pull), judged against caller-supplied results (never live chamber I/O, G8). Every acceptance threshold is `:representative` (G10) — publicly-cited engineering-literature figures, **not** verified citations to the licensed GR-468-CORE text |

`methods/active-alignment.cljc` also gained `classify-laser-class` — an IEC 60825 class ground-truth
recompute from power-mw/wavelength-nm (again `:representative` AEL thresholds, G10), which
`cells/active_alignment/state_machine.cljc` now independently verifies against a caller-claimed
`laser_class` when those two optional fields are supplied (backward compatible — every prior caller,
including every prior test, supplies neither and is unaffected).

**Deliberate architecture deviation**: unlike `active_alignment`/`fibre_loop` (whose state machines
never call their `methods/` sibling — they take a pre-computed numeric result as a state-dict input
field), `device_design`'s and `reliability_qual`'s state machines DO call their `methods/` cores
directly. A real compliance-judgment ENGINE, not just a job-lifecycle gate, is the point of this pass.

`.solve()` itself is unchanged on both cells — still an R0 stub (`RuntimeError`) pending Council
activation (G8); no live chamber, no live laser measurement, no live tapeout exists regardless.

## Layout

```
noroshi/
├── manifest.edn                     # actor SSoT (gates / non-goals / cells / lex / EPDA tiers)
├── methods/                         # 3 faces + 3 R1 bridges + 2 wave-2 cores + charter-invariants (.cljc, stdlib)
│   ├── link_budget.cljc · isac_sim.cljc · active_alignment.cljc         # the 3 faces
│   ├── cable_endpoint.cljc · kami_isac_bridge.cljc · pic_layout.cljc    # R1 bridges (c/a/b)
│   ├── device_design.cljc · reliability_qual.cljc                      # wave-2 coded cells' cores
│   ├── test_charter_invariants.cljc # structural civilian-only / no-server-key / open-EDA guard
│   └── _edn.cljc · test_*.cljc
├── cells/                           # 6 langgraph→WASM cells; 3 coded (14+ tests each)
│   ├── active_alignment/{cell.cljc,state_machine.cljc}
│   ├── device_design/{cell.cljc,state_machine.cljc}       # wave-2
│   ├── fibre_loop/{cell.cljc,state_machine.cljc}          # bonus, not in manifest
│   └── reliability_qual/{cell.cljc,state_machine.cljc}    # wave-2
├── lex/                             # 5 com.etzhayyim.noroshi.* lexicons
├── wit/kami-isac.wit                # ISAC-sensor WIT contract (kami-autodrive plant)
├── kotoba/{schema.edn,seed.edn}     # EAVT vocab (incl. :qual/* wave-2) + :representative seed
├── data/seed-photonic-fleet.kotoba.edn   # packaging robotics fleet (G2 dividend-coupled)
└── out/                             # generated link-budget / isac / alignment / bridge reports (gitignored)
```

## Test

```sh
bash run_tests.sh   # 233 tests / 751 assertions, 0 failures — bb, all cljc namespaces (see the script for the exact list)
```

`methods/*.py` were pruned after the py→cljc port (`.cljc` is the sole canonical implementation);
`cells/fibre_loop`'s legacy `state_machine.py` remains as a live py↔clj parity check inside its own
`.cljc` test and degrades gracefully (`[skip]`) when `python3`/its `safety` import isn't available —
this does not affect the 0-failures result.

**R0 = design + simulation only.** No foundry tapeout, no measured device, no live laser, no live
robot. See `90-docs/adr/2606051600-noroshi-photonic-electronic-convergence-comms-chip-isac.md` and
its wave-2 follow-up ADR.
