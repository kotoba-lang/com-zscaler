# funamori 舫 — CLAUDE guidance

**DID**: `did:web:etzhayyim.com:actor:funamori` · **Tier**: B · **Status**: R0 · **ADR**: 2605265600
(sub-ADR of 2605264100 §4; closes the salinity-gradient membrane-IP gap)

## What this actor is

淡水化発電 — **marine-renewable salinity-gradient power**. Generates electricity from the
Gibbs free energy released when fresh river water mixes with seawater. Two open-membrane
methods, both modelled in `methods/salinity_gradient.cljc`:

- **PRO** (pressure-retarded osmosis) — fresh-water osmotic pull lifts brine pressure → turbine.
  `W = Jw·ΔP`, max at `ΔP = Δπ/2` ⇒ `W_max = A·Δπ²/4`. ADR band **1–3 W/m²**.
- **RED** (reverse electrodialysis) — salinity-driven ion flux across alternating
  cation/anion-exchange membranes → current. Stack EMF `= N·2α·(RT/F)·ln(C_draw/C_feed)`;
  closed-form power density `= E_cell²/(8·area-resistance)`. ADR band **0.5–2 W/m²**.

This is the previously-reserved `20-actors/funamori/cells/salinity_gradient_pro_red/` path
(ADR-2605265600 §5 R0), now instantiated as a runnable method.

## Layout

- `methods/salinity_gradient.cljc` — the physics + **Charter gates as throwing assertions**.
  Pure Clojure (`clojure.core` only), portable `.cljc`.
- `methods/stack_robotics.cljc` — install/maintenance robotics DESIGN layer (see below).
- `methods/plant.cljc` — tidal generation + grid-tie control layer (see below).
- `methods/test_{salinity_gradient,stack_robotics,plant}.cljc` — **46 tests / 115 assertions**
  (`./run_tests.sh`, babashka).
- `kotoba/{schema,seed}.edn` — kotoba EAVT Datoms (`:funamori.salinity.*` / `:funamori.robotics.*`);
  seed is `:representative`.
- `cells/*.edn` — 4 kotoba-native Pregel cell SPECS (sanae pattern) over the methods;
  `cells/test_cells.cljc` pins their invariants. `.solve()` is R1 (see `cells/README.md`).
- `lex/` — 3 lexicons (membraneAttestation / siteAttestation / silenSalinityGradientReview), ADR §6.
- `manifest.edn` — actor manifest + 15 gates + 3 methods + 4 cells.

## Robotics design integration (`methods/stack_robotics.cljc`)

The PRO/RED stack is a rectangular array of membrane modules deployed at a river mouth.
Two robotics problems, modelled as a thin domain layer over the **shared kuni-umi infra-robotics
substrate** (ADR-2606091800; its canonical Clojure port currently lives at
`hikari/methods/substrate.cljc`, required here as `sub`):

1. **Anti-fouling coverage sweep** (ADR §5 fouling concern) — `cleaning-path` generates a
   boustrophedon (serpentine) raster over each module face given head-width + overlap;
   full-coverage verified by interval-union. `plan-clean-pass` turns the path into a planar-arm
   IK trajectory and gates it through the motion-safety envelope. `plan-stack-clean` folds
   over all modules.
2. **EOL membrane-module swap** (D2 recyclable) — `plan-module-swap` does pick-and-place from
   module to recycle bin, and **reuses `salinity-gradient/assert-membrane-permitted`** so a
   commercial (Toray/…) or PFAS (Nafion) replacement membrane is refused at the robotics layer too.

Reused substrate primitives: `->planar-arm` / `ik2` / `reachable` / `joint-trajectory`
(kinematics) and `assert-civilian` / `require-member-signature` / `witness-quorum-ok` /
`->safety-envelope` / `check-trajectory` (safety). funamori's civilian-use allowlist is
`#{assemble service clean inspect swap}`.

**Robotics is design-only at R0**: every plan carries `:funamori.robotics/server-held-key false`
+ `:funamori.robotics/dry-run true` (G11 no-live-actuation). The methods move no real arm.

## Plant + grid-tie integration (`methods/plant.cljc`)

Completes the kuni-umi **3-layer infra-robotics pattern** (plant / control / kinematics) that
hikari/mizuho/kamado/noroshi follow — funamori already had kinematics (`stack_robotics`) and
physics (`salinity_gradient`); this adds plant + control:

- **Tidal resource model** — a river-mouth intake's draw-salinity oscillates with the tide
  (M2 semidiurnal, T≈12.42 h): high tide → seawater intrusion → high Δsalinity → more power.
  `tidal-source-pair` / `instantaneous-power-kw` / `generation-series` (+ capacity factor).
- **Peak-power cap** — `assert-plant-cap` reuses `salinity-gradient/assert-site-cap` so the
  tidal PEAK output ≤50 kW (§1.9), not just the mean.
- **Grid-tie smoothing** — `grid-tie` buffers the tidal swing in a battery and delivers a steady
  output, with an **honest shortfall** when the battery is too small (ADR §4 + ADR-2605264200 §3).
- **`couple-to-microgrid`** — hands the smoothed output to **hikari** (ADR §4 cross-actor sink);
  `:funamori.plant/model-only true` at R0 (no live grid-tie).

## Hard rules (ADR-2605265600 gates — enforced IN CODE, not just documented)

The defining property of this actor: the ADR's constitutional constraints are **executable
assertions** that throw `ex-info {:error :charter-gate}`, proven by tests.

- **G1 open-membrane mandatory** — `assert-membrane-permitted`: license ∈ {in-house,
  open-publication, openmta, apache-2.0} required (§1.1).
- **G2 no-commercial-membrane** — Toray / Hydranautics / GE-Power / Statkraft **absolutely
  prohibited** (`prohibited-membranes`, §2). Do NOT add a commercial membrane vendor.
- **G3 no-PFAS** — Nafion-class perfluorinated chemistry prohibited (`pfas-membranes`,
  Charter §2(c) + §1.2). Do NOT relax to "Nafion is the industry standard."
- **G4 salinity-floor ≥30 g/L** — `assert-salinity-difference`; brackish ≤15 g/L → DEFER R4+ (§1.4).
- **G5 power-density-floor ≥1 W/m²** — `assert-r3-power-density`; below = re-design or DEFER (§1.6).
- **G6 site-cap** — `assert-site-cap` ≤50 kW, `assert-site-count` ≤1 site through R3 (§1.9 / parent §4).
- **G7 mizuho-attested-site** — site MUST be `mizuho.waterSupplySourceRegistry` attested +
  Council Lv6+ ≥3 estuary baseline (§1.3; cross-actor, R2+).
- **G11 no-server-key** — methods are pure compute, build no real stack. R0 stops at design intent.
- **G13 civilian-robotics** — `assert-civilian` closed allowlist; forbidden-force uses unrepresentable.
- **G14 witness-quorum** — ≥2 independent robot DIDs per actuation (`witness-quorum-ok`).
- **G15 motion-safety-envelope** — `check-trajectory` joint-rate ceiling (lower near humans).

## Cross-actor mesh (ADR §4)

- **mizuho** R2+ — river-mouth site qualification + waterSupplySourceRegistry + pretreatment.
- **hikari** R2+ — electrical output → microgrid + diurnal-smoothing storage pairing.
- **chigiri** R1+ — estuarine ecosystem regulatory cross-jurisdictional.

## Build / test

```sh
./run_tests.sh          # babashka; 24 tests / 57 assertions green
# or directly:
cd .. && bb -cp . -e "(require '[clojure.test :as t] 'funamori.methods.test-salinity-gradient) \
                      (t/run-tests 'funamori.methods.test-salinity-gradient)"
```

## Roadmap (ADR §5)

R0 = this method + gates + schema + lexicons (design only). **R1** = post-Council +
≥1 membrane-chemist on Council + mizuho R2 river-mouth attested + bench ≤1 kW PRO / ≤500 W RED
single-stack pilot. **R2** = ≤10 kW + power-density ≥1 W/m² demonstrated + PRO-vs-RED selection.
**R3** = full §1 cap (50 kW, 1 site).

## Do not

- Do not add a commercial proprietary membrane vendor (G2 — `assert-membrane-permitted` throws).
- Do not add a PFAS/Nafion membrane chemistry (G3 — same).
- Do not weaken the ≥30 g/L salinity floor or the ≥1 W/m² power-density floor (G4/G5 are §1 conditions).
- Do not raise the 50 kW / 1-site cap without an ADR-2605265600 amendment + Council Lv7+ unanimity.
- Do not treat the gate numbers as tunable parameters — they are Tier-1 derived from the ADR.
- Do not call any robotics plan against real hardware — R0 is dry-run; `:dry-run` / `server-held-key`
  are structural invariants (G11), and `require-member-signature` refuses a server signature.
- Do not add a robotics `use` outside `#{assemble service clean inspect swap}` (G13 — `assert-civilian` throws).
- Do not re-port the kuni-umi substrate into funamori — reuse `hikari.methods.substrate` (the shared port).
