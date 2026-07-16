# 20-actors/amime — CLAUDE.md

## What this is

**amime 網目** — the multi-site energy **MESH flow-network** solver. The system-of-systems
energy-flow layer the Energy Order suite (mio/tawami/okibi/toi/yudane/hikari, ADR-2606211200)
lacked: **hikari 光** designs ONE site, **mio 澪** verifies the org's aggregate Flowrate, but
nothing modelled how **ORDERED energy flows BETWEEN sites** across a mesh of capacity-bounded,
lossy links. amime is that layer.

A **COMMONS mesh, never a market** (no price, no trade). It rewards **ORDERED FLOW**, never
consumed energy (the mio PoUF stance). A **resilience MAP, never a target-list**.
**SIM ONLY — amime never dispatches**; hikari actuates under Council gate.

`did:web:etzhayyim.com:amime` · `com.etzhayyim.amime.*` · ADR-2606212020 · clj-native R0.

## The model

- **SITE** `{:gen :load :role}` → `net = gen − load` (signed injection).
- **LINK** `{:from :to :capacity :loss}` — undirected; `capacity` bounds the SENT kW;
  `delivered = sent · (1 − loss)`.
- **solve** (R0, deterministic single-hop transportation): each deficit pulls from adjacent
  surpluses over their links, sent bounded by remaining export AND link capacity, delivered net
  of loss. Multi-hop routing + AC power-flow are R1+.
- **N-1 contingency**: re-solve with each link removed → the link whose loss-of-service is
  largest is the **critical chokepoint** → routed to REDUNDANCY.
- **import-dependence**: per deficit site, the fraction of import over its single most-loaded
  link (1.0 = SPOF). This is the energy-flow 取 that kaname 要's `:energy` layer consumes.

On the synthetic seed the base mesh serves **100%** — yet N-1 exposes **`l-wb-ct` (wind→city)**
as critical (+258 kW stranded if lost). The SoS insight: *looks fine, is fragile* → map it.

## Hard invariants (proven by tests)

- **G1 commons-not-market** — `:amime/price` / `:amime/trade` unrepresentable (never emitted).
- **G2 ordered-flow** — rewards delivered useful flow, never consumed energy (mio PoUF).
- **G3 resilience-map** — chokepoints route to redundancy; report says "never a target-list".
- **G4 sim-only** — `:amime/dispatch` unrepresentable; hikari actuates under Council, never amime.
- **energy conservation** — `sent = delivered + loss`; no link exceeds capacity.

## Composition (system-of-systems)

```
hikari (one-site design) → amime (inter-site MESH flow) → mio (org Flowrate, PoUF)
                                      │
                                      └─ out/energy-sos.kotoba.edn  → kaname 要 :energy layer
                                                                       (ADR-2606212000)
chokepoints → hikari multi-site mesh redundancy (ADR-2606091800)
```

`out/energy-sos.kotoba.edn` is the **committed** kaname-facing `:energy` domain mirror — kaname
JOINs it via the `:amime` adapter (running a mirror = G7; joining its committed output = kaname's job).

## Files

```
methods/mesh.cljc      load + solve (transportation flow) + N-1 contingency + import-dependence + report
methods/emit.cljc      amime-native ledger EAVT datoms + kaname :energy mirror forms (writes out/energy-sos.kotoba.edn)
methods/kotoba.cljc    content-addressed append-only MESH-RESILIENCE LEDGER (tamper-evident commit-DAG)
methods/autorun.cljc   deterministic, idempotent-by-content heartbeat — solve → append ONLY on change
methods/test_*.cljc    flow/conservation/N-1/SPOF + G1 commons-not-market + ledger/heartbeat invariants
kotoba/ontology.amime.edn  EAVT schema + enums + negative space (price/trade/dispatch unrepresentable)
kotoba/seed.edn        synthetic 6-site / 7-link mesh spanning surplus + deficit + storage
out/energy-sos.kotoba.edn  committed kaname :energy mirror (JOINed by kaname, ADR-2606212000)
data/ (gitignored)     generated mesh-resilience ledger — never committed/hand-edited
manifest.edn           gates G1–G8 + non-goals N1–N5
```

## Run

```bash
./20-actors/amime/run_tests.sh                                       # 2 suites (11 tests / 52 assert)
bb --classpath 20-actors 20-actors/amime/methods/mesh.cljc           # print the mesh flow + N-1 report
bb --classpath 20-actors 20-actors/amime/methods/emit.cljc           # regenerate out/energy-sos.kotoba.edn
bb --classpath 20-actors 20-actors/amime/methods/autorun.cljc        # heartbeat → append to the ledger
```

## Pairs with

- **hikari 光** (one-site design + the actuator) · **mio 澪** (PoUF Flowrate) · **tawami/okibi/toi**
  (flexibility / heat / compute legs) — all ADR-2606211200.
- **kaname 要** (consumes `out/energy-sos.kotoba.edn` as the `:energy` domain layer, ADR-2606212000).
- Live-grid modelling + dispatch = hikari + Council Lv7+ (never amime).

## R0 → later

- **R1**: multi-hop routing (flow can transit a hub) + AC power-flow (reactive/voltage) + storage
  as a time-coupled state (charge/discharge across beats) + live-site ingest behind G7/G8.
- **R2**: kaname `:energy` join wired into the live multi-mirror SoS run; mio Flowrate bridge.
