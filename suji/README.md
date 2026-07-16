# suji 筋 — musculoskeletal posture-load biomechanics simulator

> **ADR-2606061900 · R0 · Tier-B · L4 Care (instrument/simulation layer)**
> DID: `did:web:etzhayyim.com:actor:suji`
> 筋 = muscle / sinew **and** line-of-force / reasoning — the double meaning of a force-balance engine.

Answers **「ノートパソコンの姿勢が人体にどういった緊張・強張りを作るか」** — what tension (緊張)
and stiffness (強張り) a laptop posture builds in the body — with **runnable, validated physics**,
using `kami-engine` (the kami-genesis articulation solver) and `kotoba` (the Datom log) under the
constitutional **non-diagnostic** boundary.

It is the **physics-simulation sibling of `kizashi` 兆** (which *senses* the body) and **upstream of
`mitate`** (which *diagnoses*):

```
kizashi senses  →  suji simulates the loads  →  mitate diagnoses  →  iyashi treats
```

## What it computes

```
laptop workstation ──▶ posture (joint angles)        posture.py
                   ──▶ static inverse dynamics        load.py     ← kami-genesis PlanarChain
                       (RNEA gravity term)                          Featherstone statics
                   ──▶ cervical compressive load      load.py     ← VALIDATED vs Hansraj 2014
                   ──▶ muscle %MVC  (緊張 / tension)   muscle.py   ← Hill-type moment-arm
                   ──▶ stiffness index (強張り)        strain.py   ← Rohmert sustained dose
                   ──▶ A/B/C ergonomic comparison      analyze.py
```

The **skeleton** is a sagittal articulated segment chain (head → cervical → thorax → lumbar + arm)
built from de Leva / Winter anthropometry — exactly the `PlanarChain` articulation kami-genesis
solves (ADR-2605311500/1800). The **bones load** is the static special case of Featherstone RNEA
(the gravity term), computable in stdlib and independently checkable. The **muscles** are a Hill-type
moment-arm model (force = moment / arm; %MVC = force / F_max). **強張り** is the Rohmert
sustained-isometric dose accumulated over a work session.

### The answer (`python3 methods/analyze.py`)

| workstation | head flexion | neck load | ×head-weight | worst-muscle stiffness |
|---|---|---|---|---|
| laptop-on-lap | 44° | **23.6 kgf** | 4.2× | cervical-extensors **1.00** (very-high) |
| laptop-on-desk | 27° | 17.9 kgf | 3.2× | cervical-extensors 1.00 |
| external-monitor + keyboard @ eye level | 5° | **8.1 kgf** | 1.4× | anterior-deltoid **0.05** (low) |

→ raising the screen to eye level cuts the cervical compressive load **−66%** and drops every
muscle to *low* stiffness. (Self-referenced Wellbecoming, G3 — the same body across setups, not a
ranking of people. Mechanism only; a clinician owns any health interpretation.)

## Empirical anchor — Hansraj (2014)

The cervical leg reproduces the published forward-head-posture loads of Hansraj, *Surgical
Technology International* 25 (the "60-lb tech-neck" study): neutral ≈ head weight, rising to ~5× at
60° flexion. `methods/test_load.py::test_reproduces_hansraj_table` asserts the multipliers track the
published table (0°→1× … 60°→5×) within 10%.

## Isaac Sim / kami-genesis

`wit/kami-biomech.wit` is the articulation contract a kami-genesis `PlanarChain` / nv-compat
`isaacsim.core.api` `Articulation` would implement; `methods/kami_biomech_bridge.py` builds the
link/joint/gravity spec and returns the same static joint moments the full RNEA backend would.
**Honest R0**: the `40-engine/kami-engine` submodule is unpopulated here, so this is the WIT
contract + Python reference, not a compiled backend (the `noroshi` pattern). No live actuation — the
body model is passive.

## Constitutional discipline (CRITICAL)

- **G1 NON-DIAGNOSTIC (医師法 §17)** — every output is a *mechanical* quantity (moment, force, %MVC,
  kgf, stiffness dose). No `diagnosis`/`disease`/`prescription`/`treatment`/`condition` field is
  representable in the schema, the lexicons, **or** the `load_solve` cell (`assert_nondiagnostic`
  refuses a clinical key by construction — the nusa/tazuna/kamado pattern). A licensed clinician
  (`mitate`/`iyashi`) owns any diagnosis.
- **G2 simulation-only / not-a-medical-device (薬機法/SaMD)** — no sensing hardware, no biometric
  capture; inputs are posture *parameters* (`kizashi` owns sensing).
- **G3 self-referenced Wellbecoming** — `as-of` stiffness trajectory, same-member comparison only;
  no population ranking (非終末論, no final state).
- **G4 encrypted envelope on real scan** — a body built from a real `kizashi` scan carries 要配慮 PII
  → `encryptedPayloadCid`. suji's own bodies are `:representative` averages.
- **G5 Murakumo-only · G6 no-server-key · G7 sourcing-honest · G8 outward-gated · G9 kotoba-EAVT ·
  G10 anti-pseudoscience** (no 経絡/気/波動 — Hill-model muscles only).

## Layout

```
methods/   segment · posture · load · muscle · strain · analyze · datoms · kami_biomech_bridge  (+ tests)
cells/     segment_build · posture_resolve · load_solve(coded) · strain_accumulate(coded) · ergonomic_compare
lex/       bodyModel · postureScenario · jointLoad · muscleTension · strainReport · ergonomicComparison
kotoba/    schema.edn · seed.edn      wit/  kami-biomech.wit      out/  posture-report.md · posture-datoms.edn
```

## Run

```bash
./run_tests.sh                 # 45 tests (31 methods + 14 cells) + analyze smoke
python3 methods/analyze.py     # the laptop-posture report (writes out/posture-report.md)
```

**Honest R0**: design + runnable physics + a validated cervical model. Anthropometry / muscle /
endurance parameters are `:representative` (G7); the cervical leg is validated, the muscle %MVC and
Rohmert strain legs are mechanistically grounded but illustrative. No hardware, no live member scan,
no live kami-genesis backend. Cells `.solve()` raise at R0; `load_solve` transitions are unit-tested.
