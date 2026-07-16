# kumi 組 — community-entity graph + system-dynamics observatory

**The EXTERNAL-community sibling of kizuna** (internal actor-society graph). kumi graphs
political / religious / cultural / historical / sports / civic-neighborhood / labor
**communities as units** — never individual people — connected by
`:kumi/follows` / `:kumi/depends-on` / `:kumi/influences` ties, then reads off a junkan-style
loop regime (好循環 virtuous / 悪循環 vicious / neutral / transitioning) and a kaname-compatible
leverage read, computed on read only.

- **DID**: `did:web:etzhayyim.com:actor:kumi`
- **ADR**: ADR-2607101830 (R0 scaffold)
- **Manifest**: `20-actors/kumi/manifest.edn`

## Position in the ecosystem

```
kizuna 絆   — INTERNAL: etzhayyim's own actors' social graph (agent-only, person-excluded)
kumi 組     — EXTERNAL: community/organization-unit graph — public-role community nodes +
              follows/depends-on/influences edges
kaname 要   — meta-synthesis: leverage over the power-mirror lineage; kumi JOINs as a new
              domain-observatory member (R1+, Council-gated)
junkan 循環 — aggregate stock/flow dynamics of society at large (no named entities, ever)
keizu 系図  — sibling precedent for public-role-only, edge-primary, non-adjudicating
              relation graphs (government domain; kumi is its civil-society counterpart)
```

## Node type — `:kumi/community`

A **public-role entity only**: `:id` `:name` `:domain-class` ∈ `#{:political :religious
:cultural :historical :sports :civic-neighborhood :labor :other}`, `:jurisdiction-or-locale`,
optional `:public-charter-or-registry-ref`. Never a private individual (G1).

## Edge types

- **`:kumi/follows`** — a voluntarily published affiliation/membership-of-federation declaration.
- **`:kumi/depends-on`** — an observed structural/resource dependency; **≥2 public-source
  `:sources` citations required** (G2).
- **`:kumi/influences`** — an observed co-occurrence tie; MUST carry
  `:co-occurrence-observed true` — correlation only, never causal (G4).

## Run

```bash
# one beat over the synthetic seed
bb -cp 20-actors -m kumi.methods.kumi 20-actors/kumi/data/seed-communities.kotoba.edn

# tests
bb 20-actors/kumi/run_tests.clj
```

Seed run: 9 communities (spanning political/religious/sports/cultural/historical/civic/labor),
13 ties, 5 loops → regimes `{:virtuous 2 :vicious 1 :neutral 1 :transitioning 1}`, leverage
community (要) = **harborview-civic-renewal-local**.

## Gates

G1 person-excluded · G2 public-declaration-only sourcing (≥2 citations on depends-on) · G3
non-adjudicating/no belief-content · G4 no-causal-overclaim on influences · G5 no actuator
(analysis-only, stronger than kizuna/kaname) · G6 no stored per-community power score (read-only)
· G7 resilience-routing only, never a target-list · G8 Murakumo-only/no-server-key. Full table in
`CLAUDE.md` / ADR-2607101830.

## Honest R0

Design + data model + offline analyzer over a **synthetic, fictional** seed (11 tests / 76
assertions green). No live public-registry ingest yet (G2/G8-gated, R1 follow-up). kumi has no
actuator at any tier — findings are append-only, always.

## License

Apache-2.0 WITH etzhayyim Charter Compliance Rider (`/CHARTER-RIDER.md`).
