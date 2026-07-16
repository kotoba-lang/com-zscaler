# oil-refining — SUPERSEDED by kamado 竈 (ADR-2606051500)

**Status**: legacy · superseded-not-deleted · do not extend.

This actor (`actor-manifest.jsonld`) is a pre-kotoba **observation/intel** actor. It is
charter-**non-compliant** on two counts and carries no robotics, plant, or construction:

1. **Canonical-state violation** — every pipeline step drives `graph.query` / `graph.write`
   Cypher (`MATCH (r:Refinery) …`, `MERGE (c:ActorCoverageSnapshot …)`), the RisingWave /
   graph-DB pattern prohibited by **ADR-2605262130** (the kotoba Datom log is canonical state).
2. **Legacy identity stack** — `did:web:oil-refining.etzhayyim.com`, `legacyExecutionTier:T1`,
   `operator: etzhayyim.co.jp`, `kyumei-shinka` standard. Not in the Tier-B roster.

## Replacement

**`kamado` 竈** (`20-actors/kamado/`, ADR-2606051500, DID
`did:web:etzhayyim.com:actor:kamado`) is the kotoba-native successor. It keeps the observation
surface (refinery / unit / outage registry + transition-readiness, as an as-of history on the
Datom log — a **resilience+transition map, never a target-list**) and adds the two faces this
legacy actor lacked:

- **§2(d) decommission / transition robotics** for existing fossil assets (wind down / remediate /
  convert → hikari / synthesis plant / hodoki+kanayama);
- **closed-loop synthetic refining** on biogenic / captured-CO₂ / recycled carbon only
  (net atmospheric carbon Δ ≤ 0; `:fossil-virgin-crude` is unrepresentable by construction).

## Field mapping (legacy Cypher → kamado kotoba EAVT)

| legacy (`oil-refining`) | kamado |
|---|---|
| `(:Refinery)` node | `:refinery/*` (`com.etzhayyim.kamado.refineryAsset`) |
| `(:RefineryUnit)` node | `:unit/*` (`com.etzhayyim.kamado.refineryUnit`) |
| `(:RefineryOutage)` node | `:outage/*` (as-of history, non-mutating) |
| `(:ActorCoverageSnapshot)` `MERGE` | derived datoms via `methods/analyze.py` (`:derived`) |
| `com.etzhayyim.apps.oilRefining.*` XRPC | superseded by kamado cells + offline analyzer |

## Executable migration bridge (legacy graph → kotoba EAVT)

The ETL is implemented (watari `ingest.py` precedent):

```
20-actors/kamado/methods/ingest.py            # legacy node export → kotoba EAVT + kg.ingest_batch
20-actors/kamado/data/ingest/legacy-oil-refining-export.sample.json   # Cypher-node-shaped sample
```

Run (offline, `:representative`):

```
cd 20-actors/kamado/methods
python3 ingest.py --export ../data/ingest/legacy-oil-refining-export.sample.json
# → out/refinery-graph.migrated.kotoba.edn   (kotoba EDN, dedup vs seed — seed identity wins)
# → out/oil-refining-kotoba-batch.json       (kg.ingest_batch body: 12 entities/claims/relations)
```

A real RisingWave dump (`apoc.export.json` / cypher-shell of the `MATCH (r:Refinery)…` nodes)
drops into `--export` unchanged. The bridge enforces **G4** (operator must be an `org.corp.*` id —
a refinery is never a person; person fields are refused), **G1** (migrated assets are
`:observed-fossil` — observation, not a `:synthesis` record, so `feedstock_guard` is never
bypassed), and **G7** (`:representative`).

## Promotion to live KV / kotoba (operator-gated, G8)

### Substrate boundary (CRITICAL — why the canonical write is NOT etzhayyim)

The kotoba **engine** is etzhayyim's own open-source (`github.com/etzhayyim/kotoba`,
`40-engine/kotoba`). `kotobase.net` is **etzhayyim's commercial hosted deployment** of that engine
(`did:web:kotobase.net`; verified live 2026-06-05, `/health` ok), and its `kg.ingest` requires a
**etzhayyim-AUTHN JWT** from `authn.etzhayyim.com`. Routing etzhayyim's **canonical religious-corp state**
through a vendor's auth service would violate the **Ownership invariant** (意思決定権・payoff =
etzhayyim only — a revocable vendor JWT must not gate canonical state) and the **Murakumo-only
consent-capability boundary** (religious-corp functions do not route through vendor commercial
paths, ADR-2605215000). So:

- **CANONICAL write = etzhayyim's OWN kotoba endpoint + etzhayyim DID-bound auth** (member/operator
  signature, no-server-key). State stays content-addressed (CID commit-DAG) + Base L2 anchored, so
  it is verifiable from any IPFS gateway and re-hostable anywhere.
- **etzhayyim kotobase = OPTIONAL availability MIRROR only** (a content-addressed copy; etzhayyim can host but
  cannot alter/own the data — `datomic.transact` is operator-only there, CIDs immutable). A
  commodity vendor (Pinata-class), never the canonical auth root.

### Commands

1. **Domain data** (refinery/unit/outage) → etzhayyim's refining graph — CANONICAL:
   ```
   KOTOBA_ENDPOINT=<etzhayyim kotoba node> KOTOBA_AUTH=<etzhayyim DID-bound bearer> \
     python3 20-actors/kamado/methods/ingest.py --push
   ```
   (refused with a G8 message if either is unset — there is NO hardcoded vendor default). Live
   legacy *read* from a RisingWave dump is the separate `ingest.py --live` path
   (refused unless `KAMADO_OPERATOR_GATE=1`).
   OPTIONAL mirror (copy only): `KOTOBA_JWT=<etzhayyim-jwt> python3 …/ingest.py --mirror-etzhayyim`.
2. **Actor-profile identity** → point the Worker's tier-2 at **etzhayyim's own kotoba**
   (`KOTOBA_ENDPOINT`), then let resolution self-serve. **CF KV is NOT a required step** — see below.
   `node 50-infra/etzhayyim-did-web/scripts/publish-actor-records.mjs --actor kamado --ingest-kotoba`
   (point `--ingest-kotoba` at etzhayyim's own kotoba, not etzhayyim). The publisher's internal
   `com.etzhayyim.apps.kotobase.*` nsid is the etzhayyim self-host contract.

### Why KV is NOT needed (verified 2026-06-06, against `worker.ts:711` `resolveActorRecordTiered`)

The apex resolver is 3-tier fail-open **KV → kotoba → compiled `INFRA_ACTORS`**, but tier-1 KV is
**only a 300 s auto-populated cache of the kotoba pull** (tier-2 writes it with `expirationTtl: 300`),
not a source of truth. Both KV and `KOTOBA_ENDPOINT` are optional — if the `ACTOR_KV` binding is
absent the resolver falls straight through to kotoba → compiled and `/actor/<h>/did.json` stays live.
So the **sovereign 2-tier is sufficient**: canonical = etzhayyim's own kotoba (content-addressed +
Base L2 anchored) → fallback = compiled `INFRA_ACTORS` (etzhayyim repo). KV is a CF-edge accelerator
on etzhayyim-managed infra, never canonical.

Manually pre-seeding KV (`scripts/put-actor-kv.sh`) is therefore **unnecessary and mildly
anti-pattern**: its PUT writes a **permanent, no-TTL** entry that would *shadow* future kotoba/compiled
updates until hand-deleted — the opposite of the 300 s ephemeral cache the resolver itself maintains.
The script is retained only for the documented general KV-promotion case (ADR-2606013800); it is **not
part of the kamado/oil-refining migration** and requires a `Workers KV Storage:Edit`-scoped CF token
(the etzhayyim 1Password CF tokens authenticate but lack that scope, so PUT returns 401 code 10000 anyway).

Until tier-2 kotoba is wired, the apex Worker serves kamado from the compiled `INFRA_ACTORS`
fallback (tier-3), so `/actor/kamado/did.json` resolves today.

The legacy manifest is retained for reference until a dedicated cutover PR removes it (watari
precedent); no new work should target it.
