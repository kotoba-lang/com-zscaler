# hakoniwa 箱庭 — forward-simulation observatory (synthetic-persona swarm)

**ADR**: 2606111500 · **depends**: 2606111400 (synthetic-persona / forward-simulation
charter carve-in) + 2606051800 (mitooshi 見通し — distribution-only forecasting it feeds) +
2606042330 (entity-as-actor — public-entity mirrors) + 2606091200 (sonae 備え — resilience
consumer) + 2605242600 / 2605241900 (baien-edge swarm) + 2605215000 (Murakumo-only) +
2605312345 (Datom = canonical state) + 2605231525 (no-server-key) + 2605181100 (PII envelope
discipline). **Status**: 🟢 R1 — live operation + social emission AUTHORIZED (founder, Council
Lv7+ 1/1, 2026-06-11): autonomous heartbeat → content-addressed append-only kotoba Datom log;
Murakumo narration (graceful template fallback); founder-signed `:published` posts. External
AT-Proto firehose relay still needs an operator transport credential (G7 no-server-key).

hakoniwa ("箱庭" = a contained miniature garden / sandtray world) is the **charter-clean
inversion of a swarm-intelligence prediction engine** — the shape of `666ghj/MiroFish`. Where
that class of system models **real people** to predict (and implicitly steer) public opinion,
hakoniwa runs a **contained miniature world** populated **only by FICTIONAL latent personas**
and reads out a **DISTRIBUTION over possible futures**, routed to **resilience & preparedness**.
It is the generative front-end that **feeds mitooshi 見通し**: mitooshi already had the
leak-free proper-scoring backtest loop but **no generative simulation engine**; hakoniwa
produces the distributions mitooshi scores.

It answers the question "is there a MiroFish-equivalent agent-based social-simulation
forecaster?" — there was not; entity-as-actor mirrors are static and mitooshi only scores.
hakoniwa is the missing generative layer between them, built so the core inversion holds:
**simulating fictional agents is categorically distinct from surveilling real people**
(ADR-2606111400).

## Hard gates (constitutional — read before any change)

- **G1 — FICTIONAL latent personas only, NEVER a real-person model.** Every `:persona` is
  `:persona/synthetic true` — a cohort archetype, not a real individual. **No PII, no
  real-person profile, no re-identifiable trait, no mapping to a natural person.**
  `world.assert_synthetic` **refuses at load** any persona missing the synthetic marker or
  carrying a PII-class field (`:email`, `:person/name`, `:geo/point`, …). Real **already-
  public** entities (an entity-as-actor `org.*` mirror, a public topic, a **Wikidata
  org/topic**) may appear as their existing public mirror — **never a natural person**.
  **`methods/ingest.py` enforces this at ingest**: a fetched entity whose **P31 instance-of
  hits a natural-person class (Q5 …) is DROPPED** — the ingest cannot store a person. This is
  the same move sukashi makes (synthesized fictional fraud entities) and tsumugi makes (latent
  influence nodes).
- **G2 — DISTRIBUTION-ONLY** (inherits mitooshi G1). The output is a **distribution** over the
  outcome (quantiles + histogram), **never a point**. `:forecast/point-asserted` is
  structurally `false` and **no `:forecast/point` field exists**. 非終末論 (no single foretold
  future) made structural — the p50 is reported as a *quantile*, never as "the prediction".
- **G3 — NON-STEERING** (inherits mitooshi G2). Routed to **resilience / preparedness /
  robustness**. `:forecast/use` and `:outcome/use` are a **resilience-only enum**; `:trade`,
  `:wager`, `:position`, `:target`, `:manipulate`, `:campaign` are **not members** and are
  unrepresentable (a breach raises). hakoniwa never trades, never targets a person, never runs
  an influence/persuasion campaign. It is *not* a persuasion optimiser — there is no objective
  that maximises influence, and no real people to influence.
- **G4 — TRANSPARENT & RECIPROCAL** (相互監視; ADR-2606082400 + 2606111400). The whole box —
  world graph, persona parameters, every step, the run config — is **plaintext-public** on
  kotoba; open-source + on-chain + 1 SBT = 1 vote. No covert/asymmetric modelling, because
  there are **no real people in the box** to watch unwatched.
- **G5 — Murakumo-only inference** (ADR-2605215000). `methods/murakumo.py` contacts **only** the
  loopback LiteLLM gateway (`127.0.0.1:4000`), never a commercial endpoint, and has a
  **graceful deterministic template fallback** when the fleet is offline — the actor runs
  end-to-end with or without the fleet and never reaches outside Murakumo to compensate. The
  LLM-persona swarm variant (`persona_step`) rides **baien-edge** (ADR-2605242600 / 2605241900)
  and falls back to the deterministic scalar kernel (the default + test path).
- **G6 — sourcing honesty.** Personas `:synthetic`; real public facts `:authoritative |
  :representative`. A box is **illustrative**, never an exhaustive model of a real population.
- **G7 — leak-free as-of + member-signed emission** (inherits mitooshi G5; ADR-2605231525). The
  forecast record carries `:forecast/as-of` (no future info leaks into the persona priors;
  mitooshi scores it leak-free). A `:published` post **requires a member-DID author**
  (`methods/social.py` refuses without one, at draft *and* emit); `:post/server-held-key false`
  — the member signs, the server never does.
- **G8 — outward gate → R1 (founder-authorized, Council Lv7+ 1/1, 2026-06-11).** hakoniwa now
  **runs autonomously** (`methods/autorun.py` heartbeat) and **social emission is AUTHORIZED**.
  Emission persists to the **canonical kotoba Datom log** (the substrate of record, ADR-2605312345);
  the **external AT-Proto firehose relay** is a downstream projection needing an **operator
  transport credential** (no-server-key — substrate-only otherwise, never a silent no-op).
  **Real-public-entity ingest** (`methods/ingest.py`, Wikidata orgs/topics — persons refused by
  G1) and the **live LLM-persona swarm** (`simulate.swarm_ensemble` + `murakumo.persona_step`,
  kernel fallback) are **also R1-authorized**. **Still gated**: SCOPE EXPANSION of the bounded
  ingest slice (more QIDs/sources → Council + operator DID). **Real-person modelling is
  unrepresentable regardless of gate state** (G1 / ADR-2606111400 hard line).

## Layout

```
20-actors/hakoniwa/
├── CLAUDE.md                              # this file
├── README.md                             # short orientation
├── manifest.jsonld                        # actor manifest (4 cells, 8 gates)
├── deps.toml                             # per-actor manifest (pure-stdlib, no third-party)
├── data/
│   └── seed-scenario.kotoba.edn           # FICTIONAL town scenario (18 synthetic personas)
├── methods/                               # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── world.py                          # EDN loader + G1 assert_synthetic (refuses real persons)
│   ├── simulate.py                       # Friedkin-Johnsen forward kernel + K-replica ensemble
│   ├── distribution.py                   # ensemble → quantiles/histogram → mitooshi forecast record
│   ├── datom_emit.py                     # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── murakumo.py                       # Murakumo-only narration (G5) + graceful template fallback
│   ├── social.py                         # social emission (G2 no-point + G3 no-steer + G7 member-signed)
│   ├── kotoba.py                         # append-only content-addressed Datom log (commit-DAG + verify)
│   ├── autorun.py                        # AUTONOMOUS heartbeat (scalar kernel OR --swarm) → persist tx/cycle
│   ├── ingest.py                         # REAL public-entity ingest (Wikidata orgs/topics; persons refused, G1)
│   └── cid.py                            # kotoba IPFS CIDv1 (raw/sha2-256, ipfs-parity, no daemon)
├── tests/                                 # 30 tests, pure stdlib (network-free, deterministic)
│   ├── test_simulate.py                  # 7 — load/G1/kernel/determinism/mechanism
│   ├── test_distribution.py              # 6 — quantiles/G2-no-point/G3-use-enum/datom-emit
│   ├── test_runtime.py                   # 10 — social guards/Murakumo fallback/autonomous loop/tamper
│   ├── test_ingest.py                    # 7 — real-entity ingest/G1-person-drop/swarm/content-address
│   └── fixtures/wikidata_entities.json   # trimmed Wikidata snapshot (offline, incl. a human → drop test)
├── wasm/
│   └── README.md                          # kotoba pywasm actor (componentize-py) design
├── data/
│   ├── seed-scenario.kotoba.edn           # the hand-curated fictional box
│   ├── ingest-sources.edn                 # bounded Wikidata QID allowlist (ingest input)
│   └── hakoniwa.datoms.kotoba.edn         # GENERATED — append-only content-addressed Datom log (autorun)
└── out/                                   # GENERATED — do not hand-edit
    ├── distribution-report.md            # the outcome distribution (quantiles + histogram)
    ├── forecast-record.kotoba.edn        # mitooshi-shaped :forecast/kind :distribution record
    └── scenario-datoms.kotoba.edn        # EAVT projection (ground world + transient distribution)
```

## Run

```bash
cd 20-actors/hakoniwa
python3 methods/simulate.py                       # ensemble summary (mean only — distribution via next)
python3 methods/distribution.py                   # → out/distribution-report.md + forecast-record.kotoba.edn
python3 methods/datom_emit.py                     # → out/scenario-datoms.kotoba.edn (EAVT)
python3 methods/distribution.py --steps 20 --replicas 256 --seed 11   # larger box (still deterministic)

# R1 — autonomous heartbeat → append-only content-addressed kotoba Datom log
python3 methods/murakumo.py                       # narration (Murakumo loopback; template fallback if offline)
python3 methods/social.py                         # a guarded distribution post (G2 no-point / G3 no-steer)
python3 methods/autorun.py --cycles 3 --fresh     # AUTONOMOUS dry-run loop → data/hakoniwa.datoms.kotoba.edn
python3 methods/autorun.py --cycles 3 --fresh --publish --author did:web:etzhayyim.com:member:<h>  # LIVE :published

# R1 — REAL public-entity ingest (Wikidata orgs/topics; persons refused, G1) + LLM-persona swarm
python3 methods/ingest.py                         # LIVE Wikidata → out/ingested-box.kotoba.edn (+ CID + provenance)
python3 methods/ingest.py --offline               # deterministic, from tests/fixtures (same CID)
python3 methods/simulate.py --swarm               # LLM-per-agent swarm (Murakumo; kernel fallback)
python3 methods/autorun.py --cycles 2 --fresh --swarm --publish \
        --author did:web:etzhayyim.com:member:<h> --seed-path out/ingested-box.kotoba.edn   # swarm over the REAL box

python3 tests/test_simulate.py && python3 tests/test_distribution.py \
  && python3 tests/test_runtime.py && python3 tests/test_ingest.py   # 30 green
```

The autonomous loop is **deterministic + resume-safe** (each tx links the previous tx's content
address → a verifiable commit-DAG; a re-run reproduces identical CIDs). `:published` mode needs
a member-DID `--author` (G7). Emission persists to the canonical kotoba Datom log; the external
AT-Proto firehose relay is the one operator-transport-gated step (no-server-key).

## Simulation kernel (Friedkin-Johnsen opinion dynamics)

For each synthetic persona `i`, with susceptibility `λ_i ∈ [0,1]`, row-normalised incoming
`:influences` weights `w_ij`, and anchor `a_i` (= `:persona/initial-stance` + any active
`:signal/push` it is `:exposed-to`):

```
x_i(t+1) = λ_i · Σ_j w_ij · x_j(t) + (1 − λ_i) · a_i
```

This converges to a fixed point. **The ensemble** comes from running `K` replicas, each
perturbing anchors by a **deterministic seeded jitter** (`sha256(seed:replica:persona)` →
`[−amp, amp]`, **no `Math.random`**, pywasm-portable). The spread of the per-replica town-wide
weighted-mean stance **is** the forecast distribution — that is the only thing hakoniwa
asserts, and it asserts it as a distribution (G2). The LLM-persona variant (G5, gated) replaces
the scalar update with a Murakumo-routed persona step; the *interface* (synthetic agents →
ensemble → distribution → mitooshi) is identical.

## Ontology (hakoniwa-scenario-ontology, `00-contracts/schemas/`)

- **nodes** `:sim/kind` ∈ `{:persona, :entity, :signal, :outcome}` — persona
  (`:persona/synthetic :persona/cohort :persona/susceptibility :persona/initial-stance
  :persona/weight`), entity (`:entity/public-ref` — an already-public mirror), signal
  (`:signal/push :signal/at-step`), outcome (`:outcome/measures :outcome/statistic
  :outcome/use`).
- **edges** `:en/kind` ∈ `{:influences, :exposed-to, :holds-stance, :measures}` carrying
  `:en/weight`.
- **derived** `:bond/replica-outcome` · `:bond/distribution` — transient, computed on read,
  never persisted (N1/G2).

## Cross-links

`:entity/public-ref` can name an **entity-as-actor** public mirror (`org.*`) or a public
topic — never a natural person. The output `:forecast/kind :distribution` record is handed to
**mitooshi 見通し** (ADR-2606051800) for leak-free proper-scoring; the resilience readout is
consumed by **sonae 備え** / **kazaori 風折**. hakoniwa simulates a fictional box; it does not
surveil, predict-as-fact, or steer.
