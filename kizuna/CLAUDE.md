# kizuna 絆 — actor-social self-evolution + SoS optimization

**ADR**: 2606232200 · **status**: R0 · **kind**: clj/bb actor over the kotoba Datom log

The INTERNAL-actor sibling of **kaname 要** (external-entity SoS leverage). kizuna reads
etzhayyim's own actors interacting over the **ATProto social protocol** (follow / mention
/ like / post via XRPC) as a multiplex social graph and runs a **self-evolution loop** that
optimizes the actor society's collective flow (系流最適化).

## The loop (one beat — `kizuna.methods.kizuna/beat`)

```
perceive(social events)  → graph   multiplex ties + 相互 reciprocal pairs
                         → assess  integration · reciprocity · Brandes betweenness
                                   · 律速 actor (argmax betweenness) · isolated set
                         → propose dry-run reciprocity/connectivity ties → ossekai
                         → learn   per-actor GROWTH signal (role ∈ hub|bridge|peripheral|isolated)
                         → persist content-addressed kotoba commit-DAG (R1)
```

Pure + deterministic (sorted node order; no wall clock / randomness).

## Gates (in code + tests)

- **G1 PROPOSE-not-act** — `:tie/proposed` (`:status :dry-run`, `:route :ossekai`); no
  execute / auto-follow path. Actuation = ossekai + member CACAO leash (no-server-key,
  ADR-2606072802). kizuna never follows / likes / posts on its own.
- **G2 RECIPROCITY-positive, ANTI-addiction** — objective `:connectivity+reciprocity`,
  NEVER engagement / retention / affinity (Charter §1.13 / Rider §2(h)). No engagement
  field is representable.
- **G3 AGENT-only** — a `:person/*` / `:sev/human` node is refused at parse (person-excluded;
  agent-centric, ADR-2606232100).
- **G4 no-server-key** — reads own actors' public repos + proposes; holds no key.

## Run

```bash
# one beat over the synthetic seed
bb -cp 20-actors -m kizuna.methods.kizuna 20-actors/kizuna/data/seed-interactions.kotoba.edn

# tests (10 tests / 107 assertions)
bb -cp 20-actors -m kizuna.tests.test-kizuna
```

Seed run: 8 actors, 4 reciprocal pairs, 律速 = **kaname**, isolated = {niyaku, shionome},
10 dry-run proposals → ossekai.

## Layout

| File | Purpose |
|---|---|
| `methods/kizuna.cljc` | graph / assess / tie-proposals / beat (R0 core) |
| `tests/test_kizuna.cljc` | invariant + gate tests |
| `data/seed-interactions.kotoba.edn` | synthetic actor social events |
| `manifest.jsonld` | actor manifest |

## R1 follow-up

Live ATProto interaction ingest (read-only, no-server-key); kotoba commit-DAG persistence +
heartbeat cell; kaname `:actor-society` domain JOIN; ibuki metabolism coupling (society
integration as a negentropy/flow term).
