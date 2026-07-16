# kanae (鼎) — Global Government Fiscal-Flow Visualization Substrate

**DID**: `did:web:kanae.etzhayyim.com`
**Namespace**: `com.etzhayyim.kanae.*`
**ADR**: ADR-2605302300 (R0 scaffold)
**Status**: R0 scaffold (2026-05-30) — 5 cells path-reserved + 4 Lexicon skeletons
**Primary upstream**: danjo (ADR-2605301600 + ADR-2605302245) + `com.etzhayyim.gov.dataset.*` corpus (ADR-2605263900)
**Parent ADRs**: ADR-2605301600 (danjo engine), ADR-2605302245 (danjo global fiscal-flow extension), ADR-2605263900 (open-gov corpus), ADR-2605262130 (kotoba), ADR-2605261800 (kami-engine WASM), ADR-2605192100 (Mission Charter §1.12), ADR-2605192200 (Charter Rider), ADR-2605192300 (Council 5-of-7), ADR-2605215000 (Murakumo-only inference)

## Overview

kanae is the **kotoba-native global government fiscal-flow visualization
substrate**. It assembles **worldwide fund flows** — the full domestic
chain (appropriation → outlay → subaward → procurement-award →
recipient) **and** inter-governmental flows (IMF / World Bank / OECD / UN
transfers, aid, loans) — into **kotoba EAVT** as `fundFlowEdge` datoms,
**narrates** flow subgraphs with **Murakumo-only LLM** (factual,
source-cited, non-adjudicating), and **renders** aggregate-first
**kami-engine WASM** visualizations (Sankey fund-flow diagrams, recipient
concentration treemaps, a globe of inter-governmental transfers).

> **鼎の軽重を問う — kanae weighs the public fiscal record openly.** The
> name 鼎 (kanae, the ritual bronze tripod cauldron) evokes the idiom
> 鼎の軽重を問う ("to question the weight of the cauldron" = to weigh the
> worthiness of those who govern). kanae makes the state's own published
> fiscal record legible and weighs it openly — but it holds no coercive
> power, names no individual beyond what the source records already name,
> and renders no verdict. The three legs = the three upstream substrates
> it stands on: domestic budget, domestic procurement, inter-governmental
> transfer.

## danjo finds, kanae renders (the defining boundary)

kanae is the **visualization + narrative lens that danjo lacks**:

- **danjo** (ADR-2605301600) is the non-adjudicating cross-reference
  **ENGINE**. It ingests the open-government corpus into kotoba EAVT and
  emits `discrepancyObservation` + `crossReferenceLink` + budget /
  procurement / `intergov-fund-flow` datoms. **Visualization is an
  explicit danjo non-goal.**
- **kanae** is the downstream **fiscal-flow ASSEMBLY + Murakumo
  NARRATIVE + kami-engine WASM VISUALIZATION** organ. It reads danjo's
  fiscal datoms (read-only) and the global corpus, and renders them.

The four capabilities the 2026-05-30 request named —
ingest → LLM analysis → visualization → kotoba persistence — are covered
end-to-end by the **corpus → danjo → kanae** pipeline. kanae supplies the
missing fourth capability (visualization), paired with Murakumo
narrative, persisting to kotoba EAVT (NOT RisingWave like `maps`).

## Identity (CRITICAL — IMMUTABLE)

- **NON-adjudicating** (G4; UPL-equivalent, like danjo G4 / chigiri G14 /
  toritate G5) — every `flowNarrative` carries `nonAdjudicatingNotice=true`
  and states FACTUAL flow descriptions only. kanae MUST NOT assert that a
  crime / 不正 / violation occurred. A visualization may imply nothing the
  narrative does not factually state. Legal characterization routes to
  external counsel via chigiri + Public Fund (Council Lv6+).
- **kotoba-native persistence** (G2; N13) — the fund-flow graph lives in
  kotoba EAVT. RisingWave / Postgres / Lance / DuckDB / SQLite are
  PROHIBITED as primary store or read backend (ADR-2605262130). The
  `maps` actor's RisingWave backend is **NOT** a template — the user
  explicitly asked for "datomic kotoba api で永続化".
- **Murakumo-only inference** (G7) — the `flowNarrative` LLM analysis
  runs on the Murakumo fleet ONLY (ADR-2605215000). Every narrative
  carries a `murakumoInferenceAttestation`; a vendor-LLM origin is
  unrepresentable at the schema layer.
- **Passive-only / upstream-only** (G3) — kanae reads ONLY the
  pre-published, IPFS-pinned `gov.dataset.*` corpus + danjo datoms. NO
  live portal scraping, NO per-query API, NO non-public sources, NO
  whistleblower intake (§2(c)).
- **Aggregate-first** (G10) — every visualization is aggregate by
  default; a named-party element is permitted ONLY where the source
  records already name the party AND it is severity-gated +
  Council-reviewed (anti-individualism ontology, N9).
- **kami-engine WASM render-only** (G14) + **no ad/analytics SDK** (G15)
  — render is open-source + reproducible from public datoms; no
  third-party BI, no GA4 / Meta Pixel.
- **Transparent Religious Force discipline** (G11; §1.12) — assembly +
  narrative + visualization + publication ONLY; no coercion; 1 SBT = 1
  vote governs named-party publication.

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
+ R1 ratification")` at W1 creation. Murakumo node assignment mirrors
danjo (reuben ingest / gad analysis / naphtali publish).

## kotoba EAVT persistence (G2 + ADR-2605262130) — Structural

kanae's assembly cells project fiscal flows into kotoba datoms — NOT into
RisingWave / Postgres / Lance / SQLite (prohibited as primary store or
read backend by ADR-2605262130; this is the hard difference from the
`maps` actor). The datom entities are: `fund-flow-edge`,
`fiscal-authority`, `appropriation`, `outlay`, `procurement-award`,
`recipient-entity` (LEI), `donor-jurisdiction`, `recipient-jurisdiction`,
`flow-narrative`, `visualization-manifest`. Hot-path render queries use
kotoba-kqe arrangements (EAVT / AEVT / AVET / VAET), identical discipline
to danjo (ADR-2605301600) and tadori (ADR-2605301400).

## Non-adjudication is structural, not advisory (G4) — how the schema enforces it

`flowNarrative` schema enforces:

- `nonAdjudicatingNotice` is a required boolean and MUST be `true`;
- `sourceRecordCids[]` MUST contain ≥2 entries (G5);
- `methodNoteCid` is required (G6);
- `murakumoInferenceAttestation` is required (G7 — a vendor-LLM origin is
  unrepresentable).

`fundFlowEdge` schema enforces:

- the `flowClass` enum contains only DESCRIPTIVE fiscal categories
  (`appropriation`, `outlay`, `subaward`, `procurement-award`,
  `intergovernmental-transfer`, `aid-disbursement`, `loan`, `repayment`)
  — there is NO `crime` / `violation` / `guilt` / `不正` value. A legal
  verdict is unrepresentable at the schema layer, exactly as danjo's
  `discrepancyObservation` category enum and chigiri's
  `forceAuthorizationRecord` posture.

## R1 Activation Triggers

1. ADR-2605302300 Council Lv6+ ≥3 ratify;
2. Bootstrap Council Seat 2-5 RFP closure (2026-06-19) + ≥1 filled
   Council seat beyond Founder Seat 1;
3. **danjo R1** (ADR-2605301600) ingest cells confirmed healthy
   (`danjo_budget_ledger` + `danjo_procurement_graph` producing fiscal
   datoms) — kanae's `flow_assembler` reads them;
4. ADR-2605263900 fiscal fetchers confirmed healthy (USAspending / EU FTS
   / IMF SDMX / World Bank + JP 予算書 / 政府調達) with pinned
   `gov.dataset.*` records present;
5. Charter Rider scanner false-positive rate ≤5% over 7-day trial on
   kanae-bound publication samples;
6. `70-tools/scripts/lint/no-kanae-adjudication.mjs` (LANDED at R0)
   deployed to the lefthook config (gated on the repo-wide "lefthook
   hooks full set" wave; the script is already green standalone);
7. `com.etzhayyim.kanae.fundFlowEdge` + `.methodNote` schemas
   Council-attestation-reviewed (R1 minimum cell pair = assembly cells).

## R1 Cell Activation Order

1. `kanae_flow_assembler` (lowest-risk; read-only assembly of danjo +
   gov.dataset budget/procurement datoms into kotoba EAVT `fundFlowEdge`;
   produces datoms only, no narratives, no viz);
2. `kanae_intergov_flow` (read-only assembly of IMF / WB / OECD / UN
   inter-governmental flows into `fundFlowEdge`).

R2 adds `kanae_flow_narrative` (first Murakumo narratives) +
`kanae_viz_compiler` (first aggregate `visualizationManifest`). R3 adds
`kanae_publish` (first published global fiscal-flow visualization;
named-party element path under G10 + 1 SBT = 1 vote).

## Build & Deploy

**R0 status**: Scaffold only. No cells, no smoke test (cells don't yet
exist). Lexicon schema validation (R1) will run via lefthook
`validate-lexicons` on the 4 kanae Lexicons.

**Constitutional lint (LANDED at R0)** —
`70-tools/scripts/lint/no-kanae-adjudication.mjs` enforces the defining
gates structurally and is already green:

- **Check A (G4)** — parses the kanae Lexicon JSON and asserts
  `nonAdjudicatingNotice` is `const:true` on `flowNarrative`, and that
  the `fundFlowEdge` `flowClass` enum carries NO verdict token (crime /
  violation / guilt / illegal / unlawful / fraud / 犯罪 / 違法 / 有罪 /
  不正). A legal verdict is thus unrepresentable at the schema layer.
- **Check B (G7)** — asserts `flowNarrative` requires
  `murakumoInferenceAttestation` (vendor-LLM origin unrepresentable).
- **Check C (G8 + G15)** — scans kanae CODE files (.py/.ts/.mjs/.js) for
  commercial gov-intelligence terminal hostnames + SDK imports (GovWin /
  Bloomberg Government / Politico Pro / E&E News / FiscalNote / CQ Roll
  Call) and ad/analytics SDK tokens (GA4 / gtag / Meta Pixel / fbq).
  Constitutional docs that ENUMERATE the deny-list are out of scope by
  extension (same discipline as the danjo lint).

```bash
# schema + deny-list audit (no args needed — validates canonical paths):
node 70-tools/scripts/lint/no-kanae-adjudication.mjs
# pre-commit usage (staged kanae files as args):
node 70-tools/scripts/lint/no-kanae-adjudication.mjs <files...>
# regression suite (pins the G4 + G7 anchors + G8/G15 deny-list):
node --test 70-tools/scripts/lint/no-kanae-adjudication.test.mjs
```

R1 smoke test (when cells are created):

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.kanae_flow_assembler import _r0_marker" 2>&1 | grep "R0 scaffold"
# ... similar for all 5 kanae_* cells
```

## Related Files

- `/20-actors/kanae/manifest.jsonld`
- `/20-actors/kanae/CLAUDE.md`
- `/20-actors/kanae/methods/` (open, versioned aggregation + narrative heuristics — `v1-global-seed`; G6 open method)
- `/70-tools/scripts/lint/no-kanae-adjudication.mjs` (G4 + G7 + G8 + G15 constitutional lint, green at R0)
- `/00-contracts/lexicons/com/etzhayyim/kanae/` (4 Lexicons + README)
- `/90-docs/adr/2605302300-kanae-global-fiscal-flow-visualization-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605302245-danjo-global-fiscal-flow-extension.md` — danjo global fiscal-flow extension (engine-side)
- `/90-docs/adr/2605301600-danjo-public-accountability-oversight-tier-b-actor-r0.md` — danjo (cross-reference engine; primary upstream)
- `/90-docs/adr/2605263900-public-data-open-government-ipfs-ingestion.md` — global fiscal corpus
- `/90-docs/adr/2605262130-kotoba-storage-substrate-unification.md` — kotoba substrate (EAVT, no RisingWave)
- `/90-docs/adr/2605261800-nvidia-omniverse-compat-kami-engine.md` — kami-engine (WASM render substrate, G14)
- `/CHARTER-RIDER.md` §2 — 8 prohibited categories (esp. §2(c) covert-ops + §2(e) anti-gatekeeping)
- `/CLAUDE.md` — Religious-corp status table
