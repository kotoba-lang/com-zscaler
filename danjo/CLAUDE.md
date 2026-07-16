# 20-actors/danjo — CLAUDE.md

## Identity

- **Name**: danjo (弾正 — Nara/Heian 律令制 Censorate; the 弾正台 Danjōdai monitored official misconduct. Here: the censor's EYE only, never the censor's SWORD)
- **DID**: `did:web:etzhayyim.com:actor:danjo` (canonical; `alsoKnownAs did:web:danjo.etzhayyim.com`) — **REGISTERED** in did-web (`50-infra/etzhayyim-did-web/public/actor/danjo/{did,profile}.json`) + the actor-profile-seed SSoT (`00-contracts/schemas/actor-profile-seed.kotoba.edn`), per ADR-2606013800 + ADR-2606272355
- **ADR**: ADR-2605301600 (R0 scaffold, 2026-05-30); **ADR-2606272355** (self-publication seed on the kotoba mesh, 2026-06-27)
- **Parent ADRs**: ADR-2605263900 (open-gov corpus — primary input), ADR-2605262130 (kotoba EAVT), ADR-2605192100 (Mission Charter §1.12 + §2(c)), ADR-2605192200 (Charter Rider), ADR-2605192300 (Council 5-of-7), ADR-2605215000 (Murakumo-only inference)
- **Cross-actor siblings**: toritate (ADR-2605262900; boundary), chigiri (ADR-2605262700; UPL routing), ossekai (ADR-2605264000; publication), tadori (ADR-2605301400; kotoba-native investigation sibling)
- **Status**: 🟢 R1 — live operation + social emission AUTHORIZED (founder, Council Lv7+ 1/1,
  2026-07-16): autonomous heartbeat → content-addressed append-only kotoba Datom log; Murakumo
  narration (graceful template fallback); founder-signed `:published` posts. External AT-Proto
  firehose relay still needs an operator transport credential (G7 no-server-key). 6 cells
  path-reserved + 4 Lexicon skeletons (ingest/analysis cells remain R0 pending Council Lv6+ ≥3
  per-cell ratify, ADR-2605301600 §roadmap; this authorization covers social_post only).
- **Form**: 任意団体 internal civic-transparency oversight substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock; NOT 会計検査院, NOT a state-recognized audit organ)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

danjo is a **cross-reference + transparency-publication substrate** over
the state's OWN pre-published open data, NOT a prosecutor, NOT a court,
NOT a surveillance system. Five discipline boundaries are structural and
constitutional:

1. **NON-adjudicating (G4)** — UPL-equivalent (mirrors chigiri G14 +
   toritate G5). danjo emits FACTUAL cross-reference observations only.
   It MUST NOT assert that a crime / law violation / 不正 occurred. Every
   `discrepancyObservation` carries `nonAdjudicatingNotice=true` at the
   schema layer. Legal characterization happens via human counsel
   contracted through Public Fund (Council Lv6+) and routed via chigiri —
   never inside danjo.
2. **Passive-only ingestion (G3)** — danjo reads ONLY the pre-published,
   IPFS-pinned `com.etzhayyim.gov.dataset.*` corpus (ADR-2605263900). NO
   live portal scraping, NO per-query API hits, NO non-public sources, NO
   whistleblower intake. This is the hard wall against §2(c) covert-ops /
   surveillance drift. danjo does NOT re-fetch from government portals;
   `kotodama.organism.sensors.gov.*` already fetched passively upstream.
3. **Source-provenance mandatory (G5)** — every observation cites ≥2
   upstream `gov.dataset.*` record CIDs. No inference-only allegation; no
   observation without a primary public-record citation.
4. **Open method (G6)** — every detector heuristic is published as a
   `methodNote` (open, versioned). No closed / secret scoring. The public
   audits the detector, not only its output.
5. **Transparent Religious Force discipline (G11; §1.12)** — observation
   + transparent publication ONLY. NO coercive action, NO referral to
   state coercion as an internal dependency, NO covert operation. 1 SBT =
   1 vote governs what is published as a named-party report. This is what
   makes a "government oversight" actor constitutional rather than a
   self-appointed prosecutor of the state.

## The boundary with toritate (READ THIS BEFORE EDITING EITHER)

- **toritate** (ADR-2605262900) audits the religious-corp's OWN on-chain
  books (TitheRouter + Public Fund Safe + Council Safe + Land Registry).
  Its anti-related-party check runs only against ITS OWN tithe-recipient
  vendors.
- **danjo** audits the STATE's published open-data books (国会会議録 /
  予算書 / 政府調達). It cross-references the state's spending at large.
- The two meet only where a vendor appears in both sets. Do NOT conflate
  them: toritate's identity is on-chain transparency of the corp's books;
  danjo's identity is non-adjudicating cross-reference of the state's
  published records. Keep the actor boundary clean.

## Architecture

6 Pregel cells, each path-reserved at R0 under `40-engine/kotoba/crates/kotoba-kotodama/cells/danjo_*/`:

```
diet_statement_index ──┐
procurement_graph ─────┤── reuben (continuous ingest → kotoba EAVT datoms)
budget_ledger ─────────┘

crossref_engine ───────┐
statement_consistency ─┘── gad (continuous cross-reference → observations)

oversight_report ──────── naphtali (periodic event; aggregate + Council ≥3 attestation)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on MST
(`com.etzhayyim.danjo.*`); the cross-reference graph lives in kotoba
QuadStore (EAVT) per ADR-2605262130. All cell modules are R0 path-
reserved and will be import-time `RuntimeError("danjo R0 scaffold:
activate via Council ADR + R1 ratification")` at W1 creation.

## kotoba EAVT ingest (G3 + ADR-2605262130) — Structural

danjo's ingest cells project the `gov.dataset.*` corpus into kotoba
datoms — NOT into RisingWave / Postgres / Lance / SQLite (prohibited as
primary store or read backend by ADR-2605262130). The datom entities are:
`gov-official`, `diet-statement`, `contracting-authority`,
`procurement-award`, `budget-appropriation`, `budget-outlay`,
`corp-entity` (LEI), `cross-reference-link`, `discrepancy-observation`.
Hot-path queries use kotoba-kqe arrangements (EAVT / AEVT / AVET / VAET),
identical discipline to the tadori sibling (ADR-2605301400).

## Non-adjudication is structural, not advisory (G4) — how the schema enforces it

`discrepancyObservation` schema enforces:

- `nonAdjudicatingNotice` is a required boolean and MUST be `true`;
- `sourceRecordCids[]` MUST contain ≥2 entries (G5);
- `methodNoteCid` is required (G6);
- the `category` enum contains only FACTUAL cross-reference categories
  (e.g. `single-bidder-streak`, `awardee-officer-ubo-link`,
  `statement-vs-outlay-divergence`, `outlay-without-appropriation-trace`)
  — there is NO `crime` / `violation` / `guilt` value. A legal verdict is
  unrepresentable at the schema layer, exactly as chigiri's
  `forceAuthorizationRecord` makes `posture=offensive` unrepresentable.

## R1 Activation Triggers

1. ADR-2605301600 Council Lv6+ ≥3 ratify;
2. Bootstrap Council Seat 2-5 RFP closure (2026-06-19) + ≥1 filled
   Council seat beyond Founder Seat 1;
3. ADR-2605263900 JP corpus W1 fetchers confirmed healthy
   (`jp_kokkai_kaigiroku` + `jp_chotatsu` + JP 予算書) with pinned
   `gov.dataset.*` records present;
4. Charter Rider scanner false-positive rate ≤5% over 7-day trial on
   danjo-bound publication samples;
5. `70-tools/scripts/lint/no-danjo-adjudication.mjs` (LANDED at R0)
   deployed to the lefthook config (gated on the repo-wide "lefthook
   hooks full set" wave; the script is already green standalone);
6. `com.etzhayyim.danjo.crossReferenceLink` + `.methodNote` schemas
   Council-attestation-reviewed (R1 minimum cell trio = ingest cells).

## R1 Cell Activation Order

1. `danjo_diet_statement_index` (lowest-risk; read-only index of
   already-public 国会会議録 into kotoba EAVT; produces datoms only, no
   observations);
2. `danjo_procurement_graph` (read-only index of 政府調達; awardeeLei
   resolution against corp.leiReference);
3. `danjo_budget_ledger` (read-only index of 予算書; recipientLei
   resolution).

R2 adds `danjo_crossref_engine` + `danjo_statement_consistency` (first
discrepancyObservation records, aggregate). R3 adds
`danjo_oversight_report` (first aggregate oversightReport; named-party
publication path under G10 + 1 SBT = 1 vote).

## Autonomous heartbeat (`methods/autorun.py` + `methods/kotoba.py`) — kotoba-native, fleet-runnable

Distinct from the 6 path-reserved Pregel cells above (which stay import-time `RuntimeError` until
R1), `methods/autorun.py` is the offline **autonomous heartbeat** — the same shape the infra-intel /
observatory actor family uses (shionome / ipaddress / yabai / kabuto / kanjō …). Each cycle it
observes the OFFLINE pre-published corpus + the OPEN method-pack → runs the implemented detectors
(`run_all`) → **persists a content-addressed transaction** (procurement-record graph datoms +
`danjo.discrepancyObservation` datoms) to the append-only **local** kotoba Datom log
(`methods/kotoba.py`), linking the previous tx's CID into a verifiable commit-DAG. Deterministic /
resume-safe; NO external I/O. **The five discipline boundaries hold by construction**: passive-only
(offline corpus, G3); non-adjudicating (every obs datom is `:danjo.obs/non-adjudicating true` and
`derived_datoms` RAISES if any verdict token appears, G4); ≥2 source CIDs (G5) + method-note CID
(G6); named-party publication stays G10 + 1 SBT = 1 vote gated — the loop persists to the LOCAL log
only, it publishes nothing. Fleet cells `danjo_corpus_ingest` (cron 25) + `danjo_crossref_weave`
(cron 30) + `danjo_oversight_persist` (cron 35) on `benjamin` (force+ethics node) — see
`50-infra/murakumo/fleet.toml`. Invariants guarded by `methods/test_autorun.py` (commit-DAG verify,
tamper-detect, determinism, append-only, **G4 non-adjudicating + no-verdict-token**, G5/G6
provenance, no-external-I/O).

```bash
python3 methods/autorun.py --cycles 3 --fresh   # AUTONOMOUS heartbeat → LOCAL kotoba Datom log
```

## Self-publication seed (ADR-2606272355) — register → autonomize → publish, no-server-key

danjo is the **reference implementation** of the actor self-publication seed: the
uniform, charter-clean way for a government-mirror actor to be registered at
etzhayyim.com, run autonomously on the kotoba mesh, and **self-publish its own history +
procedures** to AT-proto **without any server-held key**. We plant the seed; the actor
grows on the mesh (murakumo, `orgs/com-junkawasaki/murakumo/`) and self-custodies its
signing identity in its WASM runtime.

The seed (all LANDED):

- **did-web registration** — `50-infra/etzhayyim-did-web/public/actor/danjo/{did,profile}.json`
  (`verificationMethod: []` — no server-minted key, did:web trust root = TLS; the
  `#xrpc-libp2p` peer multiaddr is assigned at `bb murakumo deploy` time when `wasmCid` is set).
- **social_post membrane** — `cells/social_post/state_machine.cljc`: DRAFTS a record into a
  **dry-run** post ONLY if ≥2 public-source citations (G5) + non-adjudicating mirror with the
  disclaimer (G4) + `server_held_key` false (no-server-key) + status `dry-run`. A `published`
  request REFUSES. Verified under `bb`: `<2 sources / server-key / published → refused`,
  valid → `drafted` with `:post/status :dry-run`, `:post/server-held-key false`.
- **publication projection** — `methods/social.cljc`: projects danjo's HISTORY (oversight
  observations + revenue-ledger lines) + PROCEDURES (per-yen tax traceability from
  `data/jp-national-taxes.edn`) into `app.bsky.feed.post`-shaped dry-run posts
  (`draft-procedure-post` / `draft-revenue-post` / `draft-observation-post`); `enough-sources`
  raises on <2 (G5); `build-live` raises (live gate). Verified under `bb`.
- **seed trigger wiring** — `kotoba.app.edn` `danjo-social` component (`on-tick "0 */6 * * *"`
  + `on-kse etzhayyim/actor/danjo/publish`, `:requires #{:cap/kqe :cap/atproto}`).

**Division of labor (zero-knowledge)**: the **planter** authors the in-repo seed (holds no
key); the **operator** (founder) runs `bb murakumo deploy 20-actors/danjo/kotoba.app.edn <node>`
with `MURAKUMO_OPERATOR_SEED` + Tailscale and exercises the Council gate for the first live post;
the **actor's mesh runtime** self-generates/self-custodies its `did:key`, presents a member CACAO
leash (ADR-2606111400), and signs its own posts. The server never signs. R0 = dry-run drafts
only; live broadcast is Council Lv6+ + operator + member/actor-signature gated (§1.12 / G11).

```bash
bb -e '(load-file "methods/social.cljc")'                 # projection loads green
bb -e '(load-file "cells/social_post/state_machine.cljc")' # membrane loads green
# operator step (zero-knowledge — needs MURAKUMO_OPERATOR_SEED + Tailscale):
#   bb murakumo deploy 20-actors/danjo/kotoba.app.edn asher
```

## Build & Deploy

**R0 status**: Scaffold only. No cells, no smoke test (cells don't yet
exist). Lexicon schema validation (R1) will run via lefthook
`validate-lexicons` on the 4 danjo Lexicons.

**Constitutional lint (LANDED at R0)** —
`70-tools/scripts/lint/no-danjo-adjudication.mjs` enforces the two
defining gates structurally and is already green:

- **Check A (G8)** — scans danjo CODE files (.py/.ts/.mjs/.js) for
  commercial gov-intelligence terminal hostnames + SDK imports (GovWin /
  Bloomberg Government / Politico Pro / E&E News / FiscalNote / CQ Roll
  Call). Constitutional docs that ENUMERATE the deny-list are out of
  scope by extension (same discipline as sensor-no-active-probe exempting
  charter_rider.py).
- **Check B (G4)** — parses the danjo Lexicon JSON and asserts
  `nonAdjudicatingNotice` is `const:true` on discrepancyObservation +
  oversightReport, and that the discrepancyObservation `category` enum
  carries NO verdict token (crime / violation / guilt / illegal /
  unlawful / fraud / 犯罪 / 違法 / 有罪 / 不正). A legal verdict is thus
  unrepresentable at the schema layer.

```bash
# schema audit (no args needed — validates canonical lexicon paths):
node 70-tools/scripts/lint/no-danjo-adjudication.mjs
# pre-commit usage (staged danjo files as args):
node 70-tools/scripts/lint/no-danjo-adjudication.mjs <files...>
# regression suite (8 tests; pins the G4 anchor + G8 deny-list):
node --test 70-tools/scripts/lint/no-danjo-adjudication.test.mjs
```

The regression suite (`no-danjo-adjudication.test.mjs`) proves the G4
anchor cannot silently regress: it spawns the lint against poisoned
fixtures (a verdict token added to the `category` enum / a non-`const`
`nonAdjudicatingNotice`) and asserts a non-zero exit, plus a G8 fixture
(govwin host + fiscalnote import) and the doc-exemption case.

R1 smoke test (when cells are created):

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.danjo_diet_statement_index import _r0_marker" 2>&1 | grep "R0 scaffold"
# ... similar for all 6 danjo_* cells
```

## Related Files

- `/20-actors/danjo/manifest.jsonld`
- `/20-actors/danjo/README.md`
- `/00-contracts/lexicons/com/etzhayyim/danjo/` (4 Lexicon JSONs + README)
- `/90-docs/adr/2605301600-danjo-public-accountability-oversight-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605263900-public-data-open-government-ipfs-ingestion.md` — open-gov corpus (primary input)
- `/90-docs/adr/2605262130-kotoba-storage-substrate-unification.md` — kotoba substrate (EAVT, no RisingWave)
- `/90-docs/adr/2605192100-etzhayyim-mission-charter.md` — §1.12 + §2(c)
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — toritate (boundary sibling)
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — chigiri (UPL boundary; DSAR routing)
- `/90-docs/adr/2605301400-tadori-onchain-tracing-actor-and-kotoba-eavt-migration.md` — tadori (kotoba-native sibling)
- `/CHARTER-RIDER.md` — License + Rider canonical text
- `/COUNCIL.md` — Bootstrap Council roster + RFP
- `/CLAUDE.md` — Religious-corp status table
