# kumi 組 — community/organization-unit dependency-influence-follow graph + system-dynamics observatory

**DID**: `did:web:etzhayyim.com:actor:kumi` · **Tier**: B · **Status**: R0 · **ADR**: 2607101830

**Read the root `/CLAUDE.md` Charter first.** kumi-specific invariants below OVERRIDE nothing
in the Charter; they make it concrete for this actor.

## What kumi is

kumi (組 — a bounded, named human group: neighborhood mutual-aid, guild, labor union, sports
team, or any registered association) is the **EXTERNAL-community** sibling of kizuna (internal
actor-society graph) and keizu (government public-role graph). It graphs political / religious /
cultural / historical / sports / civic-neighborhood / labor **communities as units** — never
individual people — connected by `:kumi/follows` / `:kumi/depends-on` / `:kumi/influences` ties,
and reads off a **junkan-style loop regime** (好循環 virtuous / 悪循環 vicious / neutral /
transitioning) plus a **kaname-compatible leverage read** (concentration × versatility, computed
on read, never stored).

kumi fills the one node-type gap none of kizuna/junkan/kaname/keizu covers: kaname's existing
"politics / religion / organization / ideology / …" axes had no community-organization node type
feeding them. kumi is designed to be later JOINed by kaname as a new domain-observatory member,
the same way kizuna already projects into kaname's `:actor-society` layer.

## Where kumi sits among its siblings (no overlap)

```
kizuna 絆   — INTERNAL: etzhayyim's own actors' social graph (agent-only, person-excluded)
kumi 組     — EXTERNAL: community/organization-unit graph (political/religious/cultural/
              sports/historical/civic groups) — public-role community nodes +
              follows/depends-on/influences edges
kaname 要   — meta-synthesis: leverage over the power-mirror lineage; kumi JOINs as a new
              domain-observatory member (R1+, Council-gated)
junkan 循環 — aggregate stock/flow dynamics of society at large (no named entities, ever)
moyoshi 催し — convening: mints social capital from validated real-world gathering ties
keizu 系図  — sibling precedent for public-role-only, edge-primary, non-adjudicating
              relation graphs (government domain; kumi is its civil-society counterpart)
```

## The pipeline (one beat — `kumi.methods.kumi/beat`)

```
parse(seed)  → refuse G1 (person node) / G2 (under-sourced :depends-on) / G4 (uncited :influences)
             → graph   community nodes + typed ties, deterministic sorted order
             → loop-classify   junkan-style regime over dyad/triad cycles (好循環/悪循環/neutral/transitioning)
             → leverage-read   kaname-compatible L_i = C_i · (V_i / D), computed on read only (G6)
             → append-only findings (G5 — no actuator cell anywhere in this namespace)
```

Pure + deterministic (sorted node order; no wall clock / randomness). Portable `.cljc` (bb).

## Gates (in code + tests) — G1–G4 are the load-bearing set

Political/religious influence-graphing is adjacent to opposition-research/surveillance risk if
built carelessly — kumi must be at least as conservative as kizuna + danjo + kosatsu + kaname
combined on sourcing and non-adjudication, never less.

- **G1 person-excluded** — a `:person/*` / `:sev/human` node is refused at parse. A community is
  a public-role entity, never a private individual or leader (mirrors kizuna G3, keizu G1, kaname
  G1). No membership roster is ever ingested or representable.
- **G2 public-declaration-only sourcing** — edges are sourced ONLY from voluntarily published
  affiliation/charter/registry declarations; `:kumi/depends-on` ties require **≥2** independent
  `:sources` citations, refused (not silently dropped) at parse if under-sourced (mirrors keizu
  G3, danjo G3, sonae "OPEN feeds only").
- **G3 non-adjudicating / no belief-content** — kumi never encodes doctrinal/ideological content
  and never asserts a belief-verdict; political and religious communities appear only as
  structural nodes (mirrors kaname G5, danjo G4).
- **G4 no-causal-overclaim on `:kumi/influences`** — every influences tie MUST carry
  `:co-occurrence-observed true`; a bare/implicit causal claim is refused at parse, and no
  `:causal-claim` field is ever representable (mirrors junkan G5).
- **G5 PROPOSE-not-act / no actuator** — kumi has **no actuator at all** — like junkan
  ("分析するだけ"), stronger than kizuna/kaname (which at least propose via ossekai): `beat`
  returns append-only findings data only. Any follow-up intervention routes through
  kaname → ossekai, never directly from kumi.
- **G6 edge-primary, no stored per-community power score** — `leverage-read` is a pure function
  computed ON READ over the graph; no community record anywhere carries a stored
  leverage/power-score attribute (mirrors keizu G4).
- **G7 resilience-routing only, never a target-list** — findings carry no priority/target/rank
  field (mirrors kabuto/busshi/abaki's target-list prohibition).
- **G8 Murakumo-only inference, no-server-key** — standard cross-actor invariant; this R0
  namespace performs no LLM call and holds no key.

## Run

```bash
# one beat over the synthetic seed
bb -cp 20-actors -m kumi.methods.kumi 20-actors/kumi/data/seed-communities.kotoba.edn

# tests (11 tests / 76 assertions)
bb 20-actors/kumi/run_tests.clj
```

Seed run: 9 communities, 13 ties, 5 loops (3 dyad + 2 triad) → regimes
`{:virtuous 2 :vicious 1 :neutral 1 :transitioning 1}`, leverage community (要) =
**harborview-civic-renewal-local** (bridges political + labor + religious + cultural neighbors —
the highest cross-domain versatility, the kaname discriminator).

## Layout

| File | Purpose |
|---|---|
| `methods/kumi.cljc` | parse / graph / loop-classify / leverage-read / beat (R0 core) |
| `tests/test_kumi.cljc` | invariant + gate tests |
| `data/seed-communities.kotoba.edn` | synthetic, fictional community seed (political/religious/sports/cultural/historical/civic/labor) |
| `manifest.edn` | actor manifest |
| `run_tests.clj` | babashka test runner |

## Do not

- Do not add a `:person/*` / `:sev/human` node or any membership-roster field — G1.
- Do not accept a `:kumi/depends-on` tie with <2 `:sources` — G2.
- Do not add a doctrinal/ideological/belief-verdict field — G3.
- Do not let a `:kumi/influences` tie omit `:co-occurrence-observed true`, and do not add a
  `:causal-claim` field anywhere — G4.
- Do not add a dispatch/post/mention/execute cell — G5 (kumi has no actuator, full stop).
- Do not add a stored `:kumi/power-score` (or similarly named) attribute to a community record —
  leverage is computed on read only — G6.
- Do not add a priority/target/rank field to findings — G7.

## R1 follow-up

kaname `:community-graph` domain JOIN (Council-gated, mirrors kizuna's `:actor-society` JOIN);
live public-registry ingest (G2/G8-gated, offline dry-run first, same pattern as
watari/kosatsu/danjo).

## References

- ADR-2607101830 (this actor)
- ADR-2606232200 (kizuna — the internal-actor sibling this pattern mirrors)
- ADR-2606172100 (kaname — the meta-synthesizer kumi is designed to be joinable by)
- ADR-2605290927 (junkan — the system-dynamics idiom kumi's loop classification reuses)
- ADR-2605264000 (ossekai — the consent-bound actuator any future intervention routes through)
- `20-actors/keizu/` (public-role-only, edge-primary, non-adjudicating precedent)
