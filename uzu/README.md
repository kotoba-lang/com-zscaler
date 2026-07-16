# uzu 渦 — a dissipative information-energy organism

> *存在とは、エネルギー流が情報構造を作り、その情報構造がエネルギー流を再配線するプロセスである。*
> Being is the process by which energy flow forms an information structure that re-wires the
> next energy flow. **uzu 渦** ("vortex") is that pattern made runnable — the eddy in the flow,
> not the water.

`uzu` is an **artificial organism** designed from the information-energy coupled view of life
(ADR-2606211500). It is a **Markov-blanketed active-inference agent** on the append-only kotoba
Datom log, and a **measurement + visualization layer** that grounds the abstract "energy" in
real-world flows.

## The loop

```
 perceive(s) ──▶ infer(μ)  ──▶ plan(EFE) ──▶ act + metabolize ──▶ world ──▶ next perceive
   signal       belief upd.    choose by       pay energy,         (regime
                (min VFE,       expected        draw intake from     bends
                information)    free energy     the TRUE regime      back)
```

- **μ (belief)** is a *fold* over the perception log — the information structure is the fold.
- **inference** minimizes **variational free energy** (information, nats).
- **planning** minimizes **expected free energy** (pragmatic + epistemic), **vetoed by what the
  energy ledger can afford**.
- **acting** spends from the **metabolic energy ledger** (conserved); intake comes from the world.
- **death** when energy ≤ 0 — *self-maintenance is earned, not assumed.*

## Two ledgers, never the same unit

The design's central caveat is enforced in code: information and energy are **coupled but not
identical**. `uzu` keeps them apart —

| ledger | property | where |
|---|---|---|
| **energy** | conserved, depletes | `ledger.cljc`, `:uzu.beat/energy` |
| **information** | copyable, append-only | `model.cljc` + `kotoba.cljc`, `:uzu.beat/free-energy` |

## Meaning is subject-dependent (run the seed and see)

Three organisms live the **same** 12-step world tape; only their preference `C` (= *what
matters to them* = meaning) differs:

| organism | meaning `C` | behaviour | fate |
|---|---|---|---|
| **kurage 海月** | values nutrient, threat-averse | forages safety, flees hostility | **survives** (final energy ≈ 6.6) |
| **meial 迷い** | threat-*seeking* pathology | forages *into* hostility | **dies** (~beat 6) |
| **gyoja 行者** | ascetic, indifferent to nutrient | retreats from everything | **dies** (under-draws) |

Same perceptions, three meanings, three lives. Survival is the fit between meaning and world.

## Seasons & niches — fitness is *joint*

`metabolism/live-epochs` lets an organism live the tape repeatedly (seasons, carrying energy
across), and `world.cljc` generates niches (abundant / mixed / scarce). `landscape.cljc` sweeps
**meaning × niche** into a survival matrix that says more than either organism or world alone —
fitness is the **joint** property of a meaning *and* a niche:

```
meaning       abundant        mixed       scarce
kurage             ✓84          ✗-0          ✗-8
meial              ✓84          ✗-4          ✗-1
gyoja              ✗-0          ✗-8          ✗-9      (✓=alive ✗=dead, n=final energy, 3 seasons)
```

- A good meaning still needs a **net-positive niche**: kurage *accumulates* energy season over
  season in an abundant world (36 → 84 → 132) but starves in a scarce one.
- A **pathology is only exposed by a punishing niche**: meial's threat-seeking is harmless in an
  abundant world (no hazard to walk into) yet lethal where hostile steps exist.
- **Asceticism starves even in plenty**: gyoja never forages, so abundance can't feed it.

## Self-reflection & self-audit (autonomy + maturity)

The colony reasons about *itself*. `digest.cljc` reads the lived history + measured field and
reports who self-maintained, the energy economy (drawn/spent/net), the best-fitted meaning, and
the field's physical waste-heat — folded into the `autorun` heartbeat and persisted as
`:uzu.digest/*` datoms (Murakumo narration of it is the gated step). `scorecard.cljc` reads the
manifest, tallies the inventory, and **audits that every declared method/suite/lexicon file
exists** (manifest↔filesystem drift caught structurally). `validate.cljc` checks the seed against
the ontology (I1–I5).

## Measuring & visualizing the real coupled system

`measure.cljc` grounds the idea in **measured real-world flows** — physical power, the economy,
information, and human attention/meaning — as one open dissipative system, in **four
incommensurable units that are never summed across classes**:

| class | unit | example flow |
|---|---|---|
| physical | W | world primary energy ~19.6 TW; solar influx ~173,000 TW |
| economic | USD/yr | gross world product ~$105T/yr |
| informational | bit/s | global IP traffic ~1.27 Pbit/s |
| experiential | index | human waking attention; collective meaning intensity |

The **experiential class has no joule conversion** by design — converting meaning into joules
is the philosophy soup the design rejects. `viz.cljc` renders the whole field + the circulation
loop + the organism trajectories to a self-contained canvas (`out/energy-field.html`, generated
— nothing hand-copied).

## Run it

```bash
# live the organisms + measure the field + self-reflect, append to the information log
bb --classpath 20-actors 20-actors/uzu/methods/autorun.cljc

# the colony reasons about itself / audits itself / maps its viability envelope
bb --classpath 20-actors 20-actors/uzu/methods/digest.cljc       # colony self-reflection
bb --classpath 20-actors 20-actors/uzu/methods/scorecard.cljc    # maturity self-audit (manifest↔fs)
bb --classpath 20-actors 20-actors/uzu/methods/landscape.cljc    # meaning × niche survival matrix
bb --classpath 20-actors 20-actors/uzu/methods/world.cljc        # niche presets + richness
bb --classpath 20-actors 20-actors/uzu/methods/validate.cljc     # seed↔ontology integrity

# generate the visualization
bb --classpath 20-actors 20-actors/uzu/methods/viz.cljc          # → out/energy-field.html

# tests (94 tests / 216 assertions, 15 suites)
./20-actors/uzu/run_tests.sh
```

clj-native, pure stdlib, babashka-runnable, no-server-key, content-addressed + resume-safe.
Live data ingest from the observatory siblings is a Council/operator step (G7). See
`CLAUDE.md` and ADR-2606211500.
