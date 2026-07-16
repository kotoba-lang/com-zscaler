# 20-actors/kanae — CLAUDE.md

## Identity

- **Name**: kanae (鼎 — the ritual bronze tripod cauldron; 鼎の軽重を問う = to weigh the worthiness of those who govern. Here: weigh the public fiscal record OPENLY; render no verdict)
- **DID**: `did:web:kanae.etzhayyim.com`
- **ADR**: ADR-2605302300 (R0 scaffold, 2026-05-30)
- **Parent ADRs**: ADR-2605301600 (danjo engine — primary upstream), ADR-2605302245 (danjo global fiscal-flow extension), ADR-2605263900 (open-gov corpus), ADR-2605262130 (kotoba EAVT), ADR-2605261800 (kami-engine WASM render), ADR-2605192100 (Mission Charter §1.12 + §2(c) + §2(e)), ADR-2605192200 (Charter Rider), ADR-2605192300 (Council 5-of-7), ADR-2605215000 (Murakumo-only inference)
- **Cross-actor**: danjo (engine/visualizer boundary), ossekai (publication), kataribe (press), toritate (∥ disjoint — corp's own books), maps (∥ disjoint — geo + RisingWave), kami-engine (WASM render substrate)
- **Status**: R0 scaffold — 5 cells path-reserved + 4 Lexicon skeletons
- **Form**: 任意団体 internal civic-transparency fiscal-flow visualization substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock; NOT 会計検査院, NOT a commercial fiscal-intelligence product)

## danjo finds, kanae renders (READ THIS BEFORE EDITING EITHER)

This is the defining boundary. Do NOT blur it.

- **danjo** (ADR-2605301600) = the non-adjudicating cross-reference
  **ENGINE**. It ingests the open-government corpus into kotoba EAVT and
  emits `discrepancyObservation` + `crossReferenceLink` + budget /
  procurement / `intergov-fund-flow` datoms. **Visualization is a danjo
  non-goal.**
- **kanae** = the downstream **fiscal-flow ASSEMBLY + Murakumo
  NARRATIVE + kami-engine WASM VISUALIZATION** lens. It reads danjo's
  fiscal datoms (read-only, never writes back) + the global corpus, and
  renders them.
- The global fiscal-flow generalization of danjo's ingest cells and the
  `intergov-fund-flow` datom class live in **ADR-2605302245** (engine
  side; R3-gated, non-adjudicating). Do NOT add a renderer or an
  LLM-narrative path to danjo, and do NOT add a cross-reference /
  observation engine to kanae. Keep the engine/visualizer split clean.

## Constitutional Discipline (CRITICAL — IMMUTABLE)

kanae is an **assembly + narrative + visualization + publication
substrate** over the state's OWN pre-published fiscal records (via danjo
+ the corpus), NOT a prosecutor, NOT a court, NOT a surveillance system,
NOT a commercial BI / fiscal-intelligence product. Seven discipline
boundaries are structural and constitutional:

1. **NON-adjudicating (G4)** — UPL-equivalent (mirrors danjo G4 / chigiri
   G14 / toritate G5). Every `flowNarrative` carries
   `nonAdjudicatingNotice=true` at the schema layer and states FACTUAL
   flow descriptions only. A visualization may imply nothing the
   narrative does not factually state. It MUST NOT assert a crime / 不正 /
   violation occurred. The `fundFlowEdge` `flowClass` enum carries no
   verdict token. Legal characterization happens via human counsel
   through Public Fund (Council Lv6+) and routed via chigiri — never
   inside kanae.
2. **kotoba-native persistence (G2; N13)** — the fund-flow graph lives in
   kotoba EAVT. RisingWave / Postgres / Lance / DuckDB / SQLite are
   PROHIBITED as primary store or read backend (ADR-2605262130). **The
   `maps` actor's RisingWave backend is NOT a template** — the user
   explicitly asked for "datomic kotoba api で永続化". This is the hard
   architectural difference from `maps`.
3. **Murakumo-only inference (G7)** — the `flowNarrative` LLM analysis
   runs on the Murakumo fleet ONLY (ADR-2605215000: LiteLLM
   127.0.0.1:4000 / EVO-X2 LAN 192.168.1.70 / per-node Ollama gemma3:4b).
   Every narrative carries a `murakumoInferenceAttestation`; a vendor-LLM
   origin (OpenAI / Anthropic-direct / Vertex / RunPod) is unrepresentable
   at the schema layer and forbidden by the Substrate boundary GPU row.
4. **Passive-only / upstream-only (G3)** — kanae reads ONLY the
   pre-published, IPFS-pinned `gov.dataset.*` corpus + danjo datoms. NO
   live portal scraping, NO per-query API, NO non-public sources, NO
   whistleblower intake. This is the hard wall against §2(c) covert-ops /
   surveillance drift. kanae does NOT re-fetch from government portals;
   `kotodama.organism.sensors.gov.*` (upstream) + danjo already did.
5. **Aggregate-first visualization (G10)** — every visualization is
   aggregate by default (recipient classes, program totals, juris-to-juris
   columns). A named-party element is permitted ONLY where the source
   records already name the party AND it is severity-gated +
   Council-reviewed + governed by 1 SBT = 1 vote. NO per-individual
   targeting (anti-individualism ontology, N9).
6. **kami-engine WASM render-only (G14) + no ad/analytics SDK (G15)** —
   the render substrate is kami-engine WASM (ADR-2605261800), reproducible
   from public kotoba datoms. NO third-party BI (Tableau / Power BI /
   Looker / Palantir Foundry) as render or fusion layer; NO GA4 / Meta
   Pixel / affiliate SDK in the render path. The visualization is a public
   good reproducible by anyone from the same public datoms (§2(e)
   anti-gatekeeping), never a paywalled product.
7. **Transparent Religious Force discipline (G11; §1.12)** — assembly +
   narrative + visualization + publication ONLY. NO coercive action, NO
   referral to state coercion as an internal dependency, NO covert
   operation. This is what makes a "global fiscal-flow visualization"
   actor constitutional rather than a self-appointed prosecutor's
   dashboard.

## Architecture

5 Pregel cells, each path-reserved at R0 under `40-engine/kotoba/crates/kotoba-kotodama/cells/kanae_*/`:

```
flow_assembler ──┐
intergov_flow ───┘── reuben (continuous assembly → kotoba EAVT fundFlowEdge datoms)

flow_narrative ──┐
viz_compiler ────┘── gad (continuous Murakumo narrative + periodic WASM scene compile)

publish ──────────── naphtali (periodic event; aggregate + Council ≥3 attestation; IPFS-pin)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on MST
(`com.etzhayyim.kanae.*`); the fund-flow graph lives in kotoba QuadStore
(EAVT) per ADR-2605262130. All cell modules are R0 path-reserved and will
be import-time `RuntimeError("kanae R0 scaffold: activate via Council ADR
+ R1 ratification")` at W1 creation.

## kotoba EAVT persistence (G2 + ADR-2605262130) — Structural

kanae's assembly cells project fiscal flows into kotoba datoms — NOT into
RisingWave / Postgres / Lance / SQLite (prohibited as primary store or
read backend). The datom entities are: `fund-flow-edge`,
`fiscal-authority`, `appropriation`, `outlay`, `procurement-award`,
`recipient-entity` (LEI), `donor-jurisdiction`, `recipient-jurisdiction`,
`flow-narrative`, `visualization-manifest`. Hot-path render queries use
kotoba-kqe arrangements (EAVT / AEVT / AVET / VAET), identical discipline
to the danjo + tadori siblings.

## Non-adjudication is structural, not advisory (G4) — how the schema enforces it

`flowNarrative` schema enforces `nonAdjudicatingNotice` const true +
`sourceRecordCids[]` ≥2 + required `methodNoteCid` + required
`murakumoInferenceAttestation`. `fundFlowEdge.flowClass` enum contains
only descriptive fiscal categories (appropriation / outlay / subaward /
procurement-award / intergovernmental-transfer / aid-disbursement / loan /
repayment) — there is NO crime / violation / guilt / 不正 value. A legal
verdict is unrepresentable at the schema layer, exactly as danjo's
`discrepancyObservation` category enum.

## R1 Activation Triggers

1. ADR-2605302300 Council Lv6+ ≥3 ratify;
2. Bootstrap Council Seat 2-5 RFP closure (2026-06-19) + ≥1 filled seat;
3. **danjo R1** healthy (`danjo_budget_ledger` + `danjo_procurement_graph`
   producing fiscal datoms — kanae's `flow_assembler` reads them);
4. ADR-2605263900 fiscal fetchers healthy (USAspending / EU FTS / IMF
   SDMX / World Bank + JP 予算書 / 政府調達) with pinned records present;
5. Charter Rider scanner FP ≤5% over 7-day trial on kanae samples;
6. `70-tools/scripts/lint/no-kanae-adjudication.mjs` (LANDED at R0)
   deployed to lefthook (gated on the repo-wide lefthook wave);
7. `com.etzhayyim.kanae.fundFlowEdge` + `.methodNote` schemas
   Council-attestation-reviewed (R1 minimum cell pair = assembly cells).

## R1 Cell Activation Order

1. `kanae_flow_assembler` (lowest-risk; read-only assembly of danjo +
   gov.dataset budget/procurement into kotoba EAVT `fundFlowEdge`; datoms
   only, no narratives, no viz);
2. `kanae_intergov_flow` (read-only assembly of IMF / WB / OECD / UN
   inter-governmental flows).

R2 adds `kanae_flow_narrative` + `kanae_viz_compiler`. R3 adds
`kanae_publish`.

## Self-publication seed (ADR-2606272355) — register → autonomize → publish, no-server-key

kanae is registered, runs autonomously on the kotoba mesh, and **self-publishes its own
history + procedures** to AT-proto **without any server-held key** — the uniform,
charter-clean actor self-publication seed (danjo 弾正 = reference impl). We plant the seed;
the actor grows on the mesh (murakumo, `orgs/com-junkawasaki/murakumo/`) and self-custodies
its signing identity in its WASM runtime. **danjo finds, kanae renders** holds at the
publication layer too: kanae narrates DISCLOSED fund flows, never a verdict.

The seed (all LANDED):

- **did-web registration** — `50-infra/etzhayyim-did-web/public/actor/kanae/{did,profile}.json`
  (`verificationMethod: []` — no server-minted key, did:web trust root = TLS; the
  `#xrpc-libp2p` peer multiaddr is assigned at `bb murakumo deploy` time when `wasmCid` is set).
- **social_post membrane** — `cells/social_post/state_machine.cljc` (ns
  `kanae.cells.social-post.state-machine`): DRAFTS a record into a **dry-run** post ONLY if
  ≥2 public-source citations (G5) + non-adjudicating mirror with the fiscal-flow-viz disclaimer
  (G4) + `server_held_key` false (no-server-key) + status `dry-run`. A `published` request
  REFUSES. Verified under `bb`: `<2 sources / server-key / published → refused`, valid →
  `drafted` with `:post/status :dry-run`, `:post/server-held-key false`.
- **publication projection** — `methods/social.cljc` (ns `kanae.methods.social`): projects
  kanae's HISTORY (source-cited fund-flow edges + aggregate net-flow summaries assembled from
  danjo's fiscal datoms) + PROCEDURES (how a fund flow is traced: appropriation→outlay→recipient)
  into `app.bsky.feed.post`-shaped dry-run posts (`draft-procedure-post` / `draft-flow-edge-post`
  / `draft-net-flow-post`); `enough-sources` raises on <2 (G5); `build-live` raises (live gate).
  Verified under `bb`.
- **seed trigger wiring** — `kotoba.app.edn` `kanae-social` component (`on-tick "0 */6 * * *"`
  + `on-kse etzhayyim/actor/kanae/publish`, `:requires #{:cap/kqe :cap/atproto}`).

**Division of labor (zero-knowledge)**: the **planter** authors the in-repo seed (holds no
key); the **operator** (founder) runs `bb murakumo deploy 20-actors/kanae/kotoba.app.edn <node>`
with `MURAKUMO_OPERATOR_SEED` + Tailscale and exercises the Council gate for the first live post;
the **actor's mesh runtime** self-generates/self-custodies its `did:key`, presents a member CACAO
leash (ADR-2606111400), and signs its own posts. The server never signs. R0 = dry-run drafts
only; live broadcast is Council Lv6+ + operator + member/actor-signature gated (§1.12 / G11).

```bash
bb -e '(load-file "methods/social.cljc")'                 # projection loads green
bb -e '(load-file "cells/social_post/state_machine.cljc")' # membrane loads green
# operator step (zero-knowledge — needs MURAKUMO_OPERATOR_SEED + Tailscale):
#   bb murakumo deploy 20-actors/kanae/kotoba.app.edn asher
```

## Build & Deploy

**R0 status**: Scaffold only. No cells, no smoke test. Lexicon schema
validation (R1) will run via lefthook `validate-lexicons` on the 4 kanae
Lexicons.

**Constitutional lint (LANDED at R0)** —
`70-tools/scripts/lint/no-kanae-adjudication.mjs`:

- **Check A (G4)** — `flowNarrative.nonAdjudicatingNotice` is `const:true`
  + the `fundFlowEdge` `flowClass` enum carries NO verdict token (crime /
  violation / guilt / illegal / unlawful / fraud / 犯罪 / 違法 / 有罪 /
  不正).
- **Check B (G7)** — `flowNarrative` requires `murakumoInferenceAttestation`
  (vendor-LLM origin unrepresentable).
- **Check C (G8 + G15)** — scans kanae CODE for commercial gov-intel
  terminal hostnames + SDK imports (GovWin / Bloomberg Government /
  Politico Pro / E&E News / FiscalNote / CQ Roll Call) and ad/analytics
  SDK tokens (GA4 / gtag / fbq / Meta Pixel). Docs that enumerate the
  deny-list are exempt by extension.

```bash
node 70-tools/scripts/lint/no-kanae-adjudication.mjs
node --test 70-tools/scripts/lint/no-kanae-adjudication.test.mjs
```

The regression suite proves the G4 + G7 anchors cannot silently regress:
it spawns the lint against poisoned fixtures (a verdict token in the
`flowClass` enum / a non-`const` `nonAdjudicatingNotice` / a missing
`murakumoInferenceAttestation` / a GovWin host / a GA4 token) and asserts
a non-zero exit, plus the doc-exemption case.

## Related Files

- `/20-actors/kanae/manifest.jsonld`
- `/20-actors/kanae/README.md`
- `/20-actors/kanae/methods/v1-global-seed.json`
- `/00-contracts/lexicons/com/etzhayyim/kanae/` (4 Lexicon JSONs + README)
- `/70-tools/scripts/lint/no-kanae-adjudication.{mjs,test.mjs}`
- `/90-docs/adr/2605302300-kanae-global-fiscal-flow-visualization-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605302245-danjo-global-fiscal-flow-extension.md` — danjo engine-side extension
- `/90-docs/adr/2605301600-danjo-public-accountability-oversight-tier-b-actor-r0.md` — danjo engine (primary upstream)
- `/90-docs/adr/2605263900-public-data-open-government-ipfs-ingestion.md` — global fiscal corpus
- `/90-docs/adr/2605262130-kotoba-storage-substrate-unification.md` — kotoba (EAVT, no RisingWave)
- `/90-docs/adr/2605261800-nvidia-omniverse-compat-kami-engine.md` — kami-engine (WASM render, G14)
- `/CHARTER-RIDER.md` — License + Rider canonical text
- `/COUNCIL.md` — Bootstrap Council roster + RFP
- `/CLAUDE.md` — Religious-corp status table
