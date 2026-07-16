# mst-projector — Phase 3 indexed materialized views

Indexed materialized view service for kotoba actors. Replaces O(N) collection scans with O(log N) text search and O(1) aggregate lookups.

## Overview

Phase 2 kotoba actors emit `truncated: boolean` flags when data exceeds scan limits:
- `kiyo.searchPapers` — 10k+ papers, O(N) text match → >500ms latency
- `hanrei.coverageStats` — 3-collection scan → O(3N), p50 >200ms
- `ipaddress.searchProviders` — 50k+ ASNs, O(N) name/slug match → p99 >5s

**Phase 3** maintains indexed views server-side:
1. **Text search** — IVF embedding vectors (LanceDB) for O(log N) similarity
2. **Inverted attributes** — attribute → [record DID] mapping (DuckDB hash) for O(1) filtering
3. **Aggregates** — pre-computed counts (by-status, by-language, etc.) for O(1) stats

Update latency: p50 ≤1s, p99 ≤10s from PDS commit.

## Architecture

```
┌─ PDS firehose (com.atproto.sync.subscribeRepos)
│
├─ mst-projector (K8s pod, TypeScript)
│  ├─ LanceDB (vector index for text search)
│  ├─ DuckDB (attribute inverted index + aggregates)
│  └─ PDS subscription cursor (checkpointing)
│
└─ Clients (CF Workers, browser)
   ├─ e.queryView({ collection, viewName, params })  [Phase 3]
   └─ fallback to e.read() + local scan if projector unavailable [Phase 2]
```

## Per-actor configuration

Each actor declares its indexes via `ProjectorConfig`:

```ts
import type { ProjectorConfig } from "@etzhayyim/mst-projector";

export const actorProjector: ProjectorConfig = {
  actorDid: "did:web:actor.etzhayyim.com",
  collections: {
    recordType: {
      collection: "com.etzhayyim.actor.recordType",
      
      // Text search: concatenate fields, embed, index in LanceDB
      textIndex: {
        fields: ["title", "abstract", "tags"],
        model: "all-MiniLM-L6-v2"
      },
      
      // Inverted attributes: field → [record DIDs]
      attributes: ["status", "language", "category"],
      
      // Pre-computed aggregates: group value → count
      aggregates: ["status", "language"]
    }
  }
};
```

## Example configurations

See `src/kiyo-config.ts` for the Phase 3 reference implementation (kiyo papers + reviews).

Additional per-actor configs (TBD):
- `hanrei` — cases, laws, gazette entries; aggregates by jurisdiction/court
- `ipaddress` — providers, scans; text search on provider name; aggregates by country
- `narou`, `manga`, `anime` — novel/manga/anime titles; text search; aggregates by status

## Query interface (Phase 3 SDK v0.2)

```ts
// Text search: O(log N) via IVF embedding
const papers = await e.queryView({
  actor: "did:web:kiyo.etzhayyim.com",
  viewName: "paperTextSearch",
  params: { query: "machine learning", limit: 100 }
});
// Returns: { items: [...], truncated: false }

// Attribute filter: O(1) hash lookup
const providers = await e.queryView({
  actor: "did:web:ipaddress.etzhayyim.com",
  viewName: "providerByCountry",
  params: { attribute: "countryIso3", value: "US", limit: 1000 }
});

// Aggregates: O(1) disk cache
const stats = await e.queryView({
  actor: "did:web:kiyo.etzhayyim.com",
  viewName: "paperCounts",
  params: { groupBy: "status" }
});
// Returns: { counts: { "published": 1234, "draft": 567 }, total: 1801 }
```

## Phase 3 implementation scope

This scaffold is a stub (types + class skeleton + kiyo example config).

**Phase 3 work** (implementation pending):
1. LanceDB client + embedding model loader
2. DuckDB cursor for incremental aggregates
3. Firehose subscription loop + checkpoint cursor
4. Query methods implementation
5. Materialization back to PDS as `com.etzhayyim.projector.<actor>View` records

## Storage

- **LanceDB** — `<actor>-vectors.db` (local file, vector indexes)
- **DuckDB** — `<actor>-attributes.db` (local file, aggregates + inverted index)
- **Checkpointing** — PDS subscription cursor (recover via firehose replay)

Both are stateless and recoverable.

## Related

- **ADR-2605212000** — Architecture decision (this scaffold + Phase 3 spec)
- **ADR-2605210000** — Phase E reference impl completion (25 actors, truncated flags)
- **ADR-2605203000** — kotoba write-target options (Option B = PDS XRPC foundation)
- **ADR-2605111200** — CF Worker edge-only (projector runs in K8s pod)

## Roadmap

- [ ] Scaffold (types + class skeleton) — this PR
- [ ] LanceDB integration + embedding model loading — Phase 3 work
- [ ] DuckDB aggregate materialization — Phase 3 work
- [ ] Firehose subscription + cursor checkpoint — Phase 3 work
- [ ] kiyo reference impl deploy + latency verification — Phase 3 work
- [ ] Per-actor configs for all 25 actors — Phase 3 completion
