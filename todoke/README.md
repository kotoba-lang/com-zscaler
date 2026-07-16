# todoke 届け — Last-Mile (one-mile) Autonomous Delivery Tier-B Actor

**DID**: `did:web:etzhayyim.com:actor:todoke`
**Namespace**: `com.etzhayyim.todoke.*`
**ADR**: ADR-2606042300 (R0 scaffold)
**Status**: R0 scaffold (2026-06-04) — cells raise `RuntimeError` on `.solve()`; `route_sequencing` + `handoff_proof` fully coded; `todoke-route` Rust crate green.

## Overview

todoke closes the **last metre** of the etzhayyim transport stack. wadachi (轍) does inter-site
ground autonomy (≤30 km/h, ADR-2605242000); the heavy actors move freight (sarutahiko Class-8
trucks, funadaiku cargo ships). Nothing carried a parcel from the **curb to the recipient's
door** — that one-mile gap is todoke.

It is the **no-gig inversion** of the gig-economy courier: no piece-rate, no payroll, cash to
adherents ≡ 0 (G5), and the freed courier role is owed the tenure-weighted **Displacement
Dividend** (G2, ADR-2606032130). It is the delivery limb of **okaimono**'s provisioning commons
(no-gig fulfilment, ADR-2606012100) and consumes the **kami-autodrive** GNC crate (ADR-2606010600)
for perception → planning → control.

**Input**: `deliveryJob` (payload class, curb origin, door destination, recipient consent)
**Output**: `missionCompleteRecord` (route, energy, safety flags, on-device hand-off proof, CID)

## The Rust "one-mile" core — `route/` (`todoke-route`)

A pure, zero-dependency, deterministic Rust crate (`cargo test` in `route/`, **13 tests green** —
7 route + 6 R1 sim):

- **stop sequencing** — nearest-neighbour seed + 2-opt local search over curb/door stops.
- **safety envelope (G7)** — `plan_last_mile` *refuses* (returns `Err`) rather than yields a
  route whenever the run would exceed the per-zone sidewalk-speed cap (sidewalk 1.8 / bike-lane
  4.2 m/s), enter a vehicular road (outside the ODD, N2), or assume SAE level > 4 (N2). The
  charter is enforced by construction — no caller can obtain an unsafe route.

A faithful Python mirror lives in `methods/last_mile.py` (one model, two runtimes — the sumitsubo
pattern, ADR-2606033600); `methods/test_last_mile.py` pins the two implementations to the same
visiting order `[0, 4, 2, 3, 1]` on the shared fixture.

## 5 Pregel Cells

```
parcel_intake → route_sequencing → autonomous_run → handoff_proof → telemetry_log
   (naphtali)       (joseph)          (judah)         (simeon)         (levi)
```

- **parcel_intake** (受付) — accept a job; refuse contraband/regulated classes (N3). *cell .edn at R0.*
- **route_sequencing** (順路) — `deliveryJob` → safety-validated `lastMileRoute` via `todoke-route`. **fully coded + tested.**
- **autonomous_run** (走行) — execute the route on kami-autodrive (SAE-L4 sidewalk variant). *cell .edn at R0; live actuation G9-gated, Transparent-Force bound.*
- **handoff_proof** (受渡証) — on-device proof-of-delivery + the privacy spine. **fully coded + tested.**
- **telemetry_log** (記録) — assemble the kotoba `missionCompleteRecord` + IPFS CID. *cell .edn at R0.*

## 15 Gates (G1–G15) + 6 Non-Goals (N1–N6)

See `manifest.edn` for the authoritative list. Highlights:

- **G5 no-gig / cash≡0** — the constitutional inversion of gig-courier exploitation.
- **G7 SAE-L4 sidewalk envelope** — speed/zone/level *refusal by construction* (`todoke-route`).
- **G8 privacy-by-construction** — on-device proof only; cloud imagery / facial-recognition / biometric recipient ID are **unrepresentable** (N5).
- **G12 no-server-key** + **G13 consent-bound** — recipient signs the hand-off; no doorstep drop without recorded consent.
- **G2 displacement-dividend coupling** — no live displacement without a funded courier cohort.

**Non-goals**: N1 no military/weaponized/surveillance payload · N2 no SAE L5 / no road autonomy (wadachi R2+) · N3 no regulated/contraband payloads · N4 no gig labour · N5 no cloud/biometric recipient ID · N6 no aerial drone delivery at R0.

## Labour mapping

ISIC **H53** (postal/courier) · ISCO **9621** (messengers/parcel deliverers), **8322** (drivers) ·
UNSPSC **78** (transportation/storage/mail). The last mile is one of the largest gig-economy toil
pools — the natural target of the labour-liberation mission.

## Testing (R0)

```bash
# Rust core + R1 sim (13 tests) + the curb-to-door demo
cd 20-actors/todoke/route && cargo test && cargo run --example curb_to_door

# Python — run each suite from inside its own dir (the cells subpackage resolves its
# sibling imports that way); plugin autoload off dodges an unrelated langsmith/pydantic clash.
cd 20-actors/todoke/methods && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q   # 7
cd 20-actors/todoke/cells   && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -q   # 12
```

## R1 — curb-to-door simulation (`route/src/sim.rs`)

`simulate()` drives a single rover along the safety-validated route, leg by leg, from the drop
curb to the recipient's door. It is a **longitudinal kinematic stand-in for kami-autodrive**
(ADR-2606010600, `VehicleClass::Car` / sidewalk variant — that crate lives in the
`40-engine/kami-engine` submodule; when populated, `drive_leg` is replaced by an `Autopilot`
step and the `simulate` surface is unchanged). It faithfully models: per-leg speed capped at
the destination zone (G7), accelerate/cruise/brake-to-stop per waypoint, obstacle →
emergency-stop + hold + resume (a never-clearing blockage means the mission does **not** report
arrival — safety-first), and a rolling-resistance energy budget. `cargo run --example
curb_to_door` plans `[0,4,2,3,1]`, drives ~30.7 m, arrives, 1 emergency stop, ~0.067 Wh.

## Roadmap

- **R0** (2026-06-04): charter + scaffold + Rust route core + 26 tests green.
- **R1** (2026-06-04, **landed**): curb-to-door rover sim wired to `todoke-route` (6 sim tests;
  32 total). Real kami-autodrive GNC wiring deferred to submodule population.
- **R2**: pilot route (private campus) + displacement cohort sized & Public-Fund-voted.
- **R3**: community-scale + live displacement WITH dividend active (G2), Transparent-Force authorized.
