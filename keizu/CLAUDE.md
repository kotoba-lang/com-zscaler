# keizu (系図) — government power-relations knowledge graph

**DID**: `did:web:etzhayyim.com:actor:keizu` (canonical; `alsoKnownAs did:web:keizu.etzhayyim.com`) — **REGISTERED** in did-web (`50-infra/etzhayyim-did-web/public/actor/keizu/{did,profile}.json`, per ADR-2606013800 + ADR-2606272355) · **Tier**: B · **Status**: R0 · **ADR**: 2606066000; **ADR-2606272355** (self-publication seed on the kotoba mesh, 2026-06-27)

**Read the root `/CLAUDE.md` Charter + substrate rules first.** keizu-specific invariants below
OVERRIDE nothing in the Charter; they make it concrete for this actor.

## The one-sentence identity

系図 (keizu = a genealogy / relation chart) traces government **money flows** from PUBLIC
information, analyzes the **composition of committees / advisory councils** (審議会・委員会の
構成員), and weaves **procurement · money · statements · human/network relationships** into
ONE kotoba Datom relation-graph — then narrates aggregate findings as **dry-run social posts**.
The distinctive object vs the sibling accountability actors is the **relation graph of public
roles**: who sits on which committee, who funds whom, who awarded whom, who said what — woven
together. An accountability **MAP**, never a target-list; **non-adjudicating**, never a verdict.

## Where keizu sits among its siblings (no overlap)

| actor | object | keizu's relation |
|---|---|---|
| **danjo** 弾正 | non-adjudicating discrepancy observations over the state's published corpus | keizu reuses the non-adjudicating discipline + can cite danjo observations; its object is the **relation graph**, not the discrepancy |
| **kanae** 鼎 | renders fiscal flows (Sankey/treemap) | keizu emits the relation/`:money` datoms kanae visualizes |
| **tsumugi** 紡ぎ | power-entity 縁 in general (spirit-in-physics) | keizu is the **government-specific** committee+money+statement weave (disjoint object, shared edge-primary discipline) |
| **tadori** 辿 | on-chain crypto tracing | disjoint domain |
| **ooyake** 公 | government structural atlas | keizu binds its relation graph onto ooyake's gov-unit structure |

## The pipeline

```
ingest ─▶ committee_graph ─▶ relation_weave ─▶ social_post (dry-run)
(public   money_graph ──────┘  (aggregate edge-   (member-signed, mirror
 sources)                       primary concentration) disclaimer, ≥2 sources)
```

`weave.py` is the heart: it validates every record against the closed vocab, builds the graph,
and computes aggregate **edge-primary** metrics — committee cross-organ concentration,
cross-committee co-membership, per-payee money HHI, revolving-door chains. Nothing is a per-person
score.

## The 11 gates — do NOT weaken

Structural invariants live in **three places each** (ontology `:db/allowed`/closed-vocab vectors
+ lexicon `:const`/`:enum` + Python `ValueError`/refusal). Touch one, touch all three or you've
made a charter violation representable. `methods/test_charter_invariants.py` guards this.

- **G1 public-power-role-only** — `:node/scope ∈ {:public-office :public-org :public-committee
  :public-role}`. `:private-person`/`:individual`/`:citizen` are **unrepresentable**. A committee
  member is a public **seat**, never a private individual (tsumugi G1, ooyake G6). **This is the
  no-doxxing invariant.**
- **G2 non-adjudicating** — `:rel/kind` + `:money/kind` are **factual** closed vocabs; verdict
  tokens (`corruption`/`bribe`/`kickback`/`collusion`/`guilt`/`不正`/`汚職`) are **not enum
  members**. `nonAdjudicatingNotice` is `const true`. keizu records ties and disclosed shares; a
  legal characterization routes to chigiri + external counsel (danjo G4).
- **G3 source-provenance mandatory** — every `:rel` and `:money` carries **≥2** public-source
  citations (`minLength 2`); an under-sourced tie **raises** (not a silent drop).
- **G4 edge-primary, no score-of-soul** — concentration is computed **on read** from incident
  edges/flows. `:node/power-score`/`:node/influence`/`:node/rank` **do not exist** (the schema
  has no such attr; `validate_node` raises if one appears). Aggregate-first (tsumugi G2).
- **G5 mirror-not-target** — every post is `isMirror const true`, opens with the
  accountability-map disclaimer, and never speaks AS a government (ADR-2606042330). An
  accountability map, **never a target-list**.
- **G6 Murakumo-only** — any LLM narration runs on the Murakumo fleet (ADR-2605215000).
- **G7 no-server-key** — posts are member-signed; `serverHeldKey const false`; the server never
  signs (ADR-2605231525).
- **G8 outward-gated** — live public-source ingest + live posting = Council Lv6+ + operator +
  member signature; R0 = offline analyzer + **dry-run** posts; `:post/status` is `:dry-run` only
  (`:published` unrepresentable); every cell `.solve()` raises.
- **G9 PII-encrypted** — public-role only, but any incidentally-sensitive datum → XChaCha20-Poly1305
  envelope (ADR-2605181100).
- **G10 non-eschatological as-of** — committee compositions / appointments / ties are append-only
  term history; a re-composition is a NEW snapshot, never an overwrite (kotoba-canonical
  ADR-2605312345 + 非終末論).
- **G11 sourcing-honesty** — every datom declares `:representative` | `:authoritative`; the
  committed seed is `:representative`.

## When editing

- The closed vocab in `00-contracts/schemas/government-relations-ontology.kotoba.edn`
  (`:ontology/node-scopes`, `:ontology/rel-kinds`, `:ontology/money-kinds`, `:ontology/post-statuses`)
  is the single source the invariant test parses. Adding a verdict-bearing kind, a private node
  scope, or a `:published` post status fails `test_charter_invariants.py`.
- `weave.py` `VERDICT_TOKENS` is the Python mirror of the no-verdict rule; keep it in sync.
- `.solve()` raises `RuntimeError` on every cell at R0 — live execution is G8-gated. Do not wire a
  cell to a live portal fetch or a live firehose post.
- Tests are standalone-runnable (`python3 test_*.py`); run everything with `./run_tests.sh`
  (141 tests across 11 suites, hermetic — the registry check is soft). See MATURITY.md for the
  per-suite breakdown.

## Honest R0

Design + data-model + offline analyzer + dry-run posts only. The seed is bounded
`:representative` (public roles/organs, rounded figures) — **not** a live authoritative capture;
nodes are public seats/organs, never named private individuals. Live full-universe ingest (官報 /
政治資金収支報告書 / 調達ポータル / Federal Register / USAspending / TED / OECD rosters) and live
social posting are Council Lv6+ + operator gated (Lv7+ for live publication under 1 SBT = 1 vote).

## Autonomous on the Murakumo fleet (`methods/autorun.py` + `methods/kotoba.py`)

`methods/autorun.py` is the self-driving heartbeat — the constitution-permitted form of
"kotoba で自律的に稼働", the same shape the infra-intel/observatory family uses
(shionome/kabuto/danjo …). Each cycle it weaves the OFFLINE seed → concentration → **persists a
content-addressed transaction** (graph datoms + derived `:keizu.conc/*`) to the append-only
**local** kotoba Datom log (`methods/kotoba.py`), linking the previous tx's CID into a verifiable
commit-DAG. `autorun._canonical_order` sorts datoms by canonical JSON before hashing → CID
reproducible across processes (verified stable under `PYTHONHASHSEED=random`). **G1/G4 hold by
construction**: no PII node attr can reach the log (no-doxxing), revolving-door + award-and-fund
datoms carry `:keizu.conc/non-adjudicating true` (a co-occurrence of disclosed flows, never an
allegation), and no per-person score / verdict token is representable. Fleet cells
`keizu_relation_ingest` (cron 40) + `keizu_concentration_weave` (cron 45) + `keizu_relation_persist`
(cron 50) on `levi` (membership/council node) — see `50-infra/murakumo/fleet.toml`. Live ingest +
posting stay G8-gated (the loop persists to the LOCAL log only, posts nothing). Invariants guarded
by `methods/test_autorun.py` (commit-DAG verify, tamper-detect, canonical-order determinism,
append-only, **G4 non-adjudicating co-occurrence**, **G1 no-doxxing**, no-external-I/O).

```bash
cd methods && python3 autorun.py --cycles 3 --fresh   # AUTONOMOUS heartbeat → LOCAL kotoba Datom log
```

## Self-publication seed (ADR-2606272355) — register → autonomize → publish, no-server-key

keizu joins the **actor self-publication seed** pattern (danjo 弾正 is the reference
implementation): the uniform, charter-clean way for a government-mirror actor to be
registered at etzhayyim.com, run autonomously on the kotoba mesh, and **self-publish its
own history + procedures** to AT-proto **without any server-held key**. We plant the seed;
the actor grows on the mesh (murakumo, `orgs/com-junkawasaki/murakumo/`) and self-custodies
its signing identity in its WASM runtime.

**keizu's social cell was PRE-EXISTING (ADR-2606066000)** — the membrane + projection were
already built; this seed only adds the **did-web registration** + the **seed trigger wiring**,
and verifies the existing cell. The seed (all LANDED):

- **did-web registration (NEW)** — `50-infra/etzhayyim-did-web/public/actor/keizu/{did,profile}.json`
  (`id: did:web:etzhayyim.com:actor:keizu`, `alsoKnownAs did:web:keizu.etzhayyim.com`,
  `verificationMethod: []` — no server-minted key, did:web trust root = TLS; the `#xrpc-libp2p`
  peer multiaddr is assigned at `bb murakumo deploy` time when `wasmCid` is set; only the
  `#atproto_pds` service is present at R0). `_meta` carries the actor's adr ids +
  2606272355 + 2605231525 + 2606230001, glyph 系図, status r0, wasmCid null,
  execModel `mesh-component`, primaryLexicon `com.etzhayyim.keizu.relationEdge`.
- **social_post membrane (pre-existing)** — `cells/social_post/state_machine.cljc`: DRAFTS a
  record into a **dry-run** post ONLY if ≥2 public-source citations (G3) + mirror with the
  accountability disclaimer (G5) + `server_held_key` false (G7/no-server-key) + status
  `dry-run` (G8). A `published` request REFUSES. Verified under `bb`:
  `1 source → refused`, `server-key → refused`, `published → refused`, valid → `drafted`
  with `:post/status :dry-run`, `:post/server-held-key false`.
- **publication projection (pre-existing)** — `methods/social.cljc`: projects keizu's
  aggregate findings (committee cross-organ concentration, per-payee money HHI) into
  `app.bsky.feed.post`-shaped dry-run posts (`draft-committee-post` / `draft-money-post`);
  `enough-sources` raises on <2 (G3); `build-live` raises (G8 live gate). Verified under
  `bb` (requires the `keizu.methods.weave` sibling → run with `bb --classpath ..`).
- **seed trigger wiring (NEW)** — `kotoba.app.edn` `keizu-social` component
  (`on-tick "0 */6 * * *"` + `on-kse etzhayyim/actor/keizu/publish`,
  `:requires #{:cap/kqe :cap/atproto}`, `:src "methods/social.cljc"`). The pre-existing
  `keizu` observatory component is preserved.

**Division of labor (zero-knowledge)**: the **planter** authors the in-repo seed (holds no
key); the **operator** (founder) runs `bb murakumo deploy 20-actors/keizu/kotoba.app.edn <node>`
with `MURAKUMO_OPERATOR_SEED` + Tailscale and exercises the Council gate for the first live
post; the **actor's mesh runtime** self-generates/self-custodies its `did:key`, presents a
member CACAO leash (ADR-2606111400), and signs its own posts. The server never signs. R0 =
dry-run drafts only; live broadcast is Council Lv6+ + operator + member/actor-signature gated
(§1.12 / G11 / G8).

```bash
# membrane + projection load green (projection needs the weave sibling on classpath):
bb -e '(load-file "cells/social_post/state_machine.cljc")'
bb --classpath .. -e '(load-file "methods/social.cljc")'
# operator step (zero-knowledge — needs MURAKUMO_OPERATOR_SEED + Tailscale):
#   bb murakumo deploy 20-actors/keizu/kotoba.app.edn levi
```

## Build / test

```
./run_tests.sh                       # all 11 suites (141 tests)
cd methods && python3 weave.py       # concentration over the :representative seed
cd methods && python3 analyze.py     # end-to-end dry-run → methods/out/intel-report.md
cd methods && python3 social.py      # dry-run social posts
cd methods && python3 ingest.py      # offline normalize (──live refuses without the G8 gate)
```

## Do not

- Do not add `:private-person`/`:individual` to node scopes, or a `:node/power-score` — G1/G4.
- Do not add a personal-contact/sensitive field to a node (`:node/email`/`address`/`dob`/`mynumber`/`face`/…) — G9/G1 no-doxxing (`validate_node` raises; PII lives encrypted off-graph).
- Do not add a verdict kind (`:corruption`/`:bribe`/`:collusion`/…) to rel/money kinds — G2.
- Do not accept an under-sourced (<2) relation or money flow — G3.
- Do not let a post be `:published` or `serverHeldKey:true` — G7/G8.
- Do not call any cell's `.solve()` — R0 scaffolds raise by design (G8).
- Do not route narration through a commercial GPU — G6 (Murakumo-only).
- Do not use Kotoba/Datomic/SQL — kotoba Datom log only (N7).
