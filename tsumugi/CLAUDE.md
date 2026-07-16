# tsumugi 紡ぎ — Engi Knowledge Graph intel weaver (spirit-in-physics over real power-entities)

**ADR**: 2606011800 · **depends**: 2606011000 (§D7 産霊の網) + 2606011500 (spirit-ontology)
· 2605081300 (edge-primary karma) · 2605170000 (spirit=thermodynamic info) · 2605301600
(danjo) · 2605301400 (tadori) · 2605302300 (kanae). **Status**: 🟡 R0 design-only.

tsumugi ("紡ぎ" = spinning threads together) executes §D7.1 of ADR-2606011000: it weaves
the **Engi Knowledge Graph (産霊の網 / musubi-no-ami)** — real-world 法人 / institution /
ecological / public-role entities and their 縁 (follow / depends-on / custodies) — and runs
the **Spirit-in-Physics** pipeline (RBF emotion-kernel → spectral 3D embed → tensegrity
relax) over it to surface **取-concentration** (custody-debt that accumulates over others),
routed toward release.

It is the upper layer over **danjo** (power 取), **kanae** (fiscal-flow render), **tadori**
(on-chain attribution), **himotoki** (self-claim). It does NOT replace them.

## Hard gates (constitutional — read before any change)

- **G1 — power-only scope (§D8/§D9).** Only power-holding entities: 法人, institutions, and
  persons STRICTLY in a public-power role (chair/exec/official). The powerless / private
  individuals are absent **by construction**. This is an accountability map of 取の集中,
  **never a target-list**.
- **G2 — edge-primary (N1).** karma/取 lives ONLY on edges (`:spirit.bond/signed-weight`).
  An organism's 取-concentration = the **integral of its incident 縁**, computed on read —
  never a stored per-soul score. There is no `:spirit/score-of-soul`.
- **G3 — aggregate-first + claimed-first.** Members' own declared 縁 are covenant-visible
  (ADR-2605310100 §1–2); the latent remainder is aggregate-first only. Never a per-person
  exposure dump.
- **G4 — public venue (§D8).** Open-source + on-chain + 1 SBT = 1 vote + symmetric
  access-log (sousveillance of the watchers). Never a private/covert registry at a vendor.
- **G5 — sourcing honesty.** Every record carries `:sourcing :authoritative | :representative`.
  No fabricated coverage. The committed seed is `:representative` (public docs, bounded).
- **G6 — Murakumo-only narration.** Any LLM narration routes through Murakumo (ADR-2605215000).
- **G7 — outward-gated (G11).** Live planet-scale ingest (atproto follow/deps over real
  persons) requires Council + operator. R0 = analyzer + schema + seed only.
- **G8 — no git-lfs.** Large/binary assets via DataLad → IPFS (80-data/spirit-in-physics).
- **G9 — PII envelope.** Any 要配慮 datum (human assay) → XChaCha20-Poly1305 (ADR-2605181100).

## Layout

```
20-actors/tsumugi/
├── CLAUDE.md                       # this file
├── manifest.jsonld                 # actor manifest
├── data/
│   ├── seed-power-graph.kotoba.edn      # real PUBLIC power entities + 縁 (:representative)
│   └── ingest/*.edn                     # §D7.1 ingest sources (atproto follow fixtures, deps…)
├── methods/
│   ├── ingest.py                        # §D7.1 ingester — weave seed + sources (gated)
│   └── analyze.py                       # spirit-in-physics intel analyzer (stdlib + numpy)
└── out/                                 # GENERATED — do not hand-edit
    ├── woven-graph.kotoba.edn           # seed + ingest merged (claimed-first, latent-flagged)
    ├── intel-report.md                  # aggregate-first 取-concentration report
    └── spirit-graph.kotoba.edn          # :spirit.bond/* + :spirit/* + :grasp/* datoms
```

## Run

```bash
# 1. weave the graph (fixture mode — NO network; latent organisms flagged claimed?=false)
bb -cp 20-actors -m tsumugi.methods.ingest
# 2. analyze the woven graph (or a specific seed)
bb -cp 20-actors -m tsumugi.methods.analyze 20-actors/tsumugi/out/woven-graph.kotoba.edn --out 20-actors/tsumugi/out
bb -cp 20-actors -m tsumugi.methods.analyze <seed.edn> --out <dir>
```

## ie-flow / SoS score (`methods/ie_flow.cljc`, ADR-2606212200)

tsumugi is scored as an **information-control actor** in the SoS scoreboard (`80-data/ie-flow/scoreboard.md`,
score 0.454) via the SHARED `etzhayyim.ie-flow.gate-adapter`: volume = `held` (each entity's 取-holding
edge-integral, the scattered input), value = release-target `max(0, held−1)`·scale — concentrating the
realised RELEASE order onto the few biggest 取-holders (that re-weighting IS the rectification, order-index
0.369). Every route is `release` (a release MAP, never a target-list, G2). N1 preserved (取 is the
edge-integral, computed on read; no per-soul score). `record-flow!` → `80-data/ie-flow/tsumugi/` (gitignored).

```bash
bb 20-actors/tsumugi/methods/ie_flow.cljc            # flow-state (order calculus)
bb 20-actors/tsumugi/methods/test_ie_flow.cljc       # 5 tests / 13 assertions (wired into run_tests.sh)
```

Emits the connected spirit-graph (edge-primary) + the 取-concentration intel report. To
advance over more of the earth (§D7.1, "繋げていって"): drop more `:representative` public
relations into `data/ingest/`, or (Council-gated, `--live` + `TSUMUGI_OPERATOR_GATE`) wire
the real `app.bsky.graph.getFollows` fetch via `@etzhayyim/sdk` + MST membrane (ADR-2605231902).

---

# Diachronic influence-history extension (ADR-2606061500)

tsumugi's present-tense power-graph, run **BACKWARD IN TIME**. Models past humanity as a
diachronic graph of **influence-bearing PUBLIC historical figures, documents, events and
traditions** (incl. YHWH/Torah, Jesus/Gospels, Buddha/suttas — as **influence nodes in
human history**, never as theological claims) and the directed 縁 by which information (an
idea, a text, a practice) flowed from an earlier node and **deformed the metric** of a later
one. Frame: junkawasaki.com "spirit is information" (spirit = metric deformation of a
self-boundary as external info is integrated; covariant gradient of free energy). That frame
is *individual* — this extension supplies the missing **inter-personal, diachronic
propagation**: an influence 縁 is the **channel** across which that deformation travels
between selves and across centuries.

> Same 縁-physics, new axis. Reuses the spirit-ontology pipeline (RBF kernel → spectral
> embed → tensegrity); adds a temporal DAG, influence flows, and Katz reach.

## Five structural invariants (read influence-history-ontology.kotoba.edn before any change)

- **N1 — edge-primary.** Influence/karma lives ONLY on `:flow/signed-weight`. A node's
  influence is the **integral of its incident flows**, computed on read. There is NO
  `:influence/score-of-figure`. Modeling the dead must not become **ranking souls**.
- **N2 — mirror, never impersonation.** Every node `:mirror/is-mirror` true; a post is an
  **observation ABOUT** a figure's documented influence, never the figure speaking.
  `:post/voice` is locked to `:observer`; there is no first-person field, so impersonation
  is unrepresentable (`project_influence_posts.py` refuses any non-mirror node).
- **N3 — non-eschatological + non-adjudicating truth (Charter §1.15).** We datafy the
  **INFLUENCE OF** a tradition (a historical-information claim), NEVER its theological truth.
  No `:truth/verdict`, no `:salvation/status`, no `:afterlife/*`, no final-state datom.
- **N4 — public + long-settled + no PII.** Only documented public influence-bearing figures.
  Living-private persons remain the **Council-Lv7+-gated `:human` scale** of spirit-ontology;
  this is an influence map, never a target-list, hagiography, or ranking of worth.
- **N5 — temporal DAG.** Every `:flow` points forward in time (`source.year-from ≤
  receiver.year-to`). Information cannot precede its source; violations are reported.

`:hist/dating-confidence` carries dating honesty per node (`:attested` / `:scholarly-consensus`
/ `:traditional` / `:legendary`) — legendary attributions (Moses, Bodhidharma) are **flagged,
never asserted**. The `self.etzhayyim` node maps the entity's **own doctrinal genealogy**
(Protestant Sola Scriptura/万人祭司/Tree of Life + 八百万/縁起/産霊/和) as **inbound-only**
influence (the 産霊 receiving side) — never authored as a source over others.

## Run (influence mode)

```bash
# diachronic influence analysis (temporal-DAG check + Katz reach + spirit embed)
bb -cp 20-actors -m tsumugi.methods.analyze-influence            # default seed
bb -cp 20-actors -m tsumugi.methods.analyze-influence <seed.edn> --out <dir>
# dry-run mirror posts (observer voice, published=false; impersonation refused)
bb -cp 20-actors -m tsumugi.methods.project-influence-posts
# tests (12 — one per invariant + seed/projector checks)
python3 20-actors/tsumugi/tests/test_influence.py
```

Outputs (GENERATED — do not hand-edit): `out/influence-report.md` (aggregate-first: top
influence SOURCES = outbound Katz reach · top SYNTHESIZERS = inbound · top BROKERS · era
layering · etzhayyim genealogy), `out/influence-graph.kotoba.edn` (`:spirit.bond/*` +
`:influence/*` edge-integral readouts), `out/influence-posts.dryrun.kotoba.edn`.

**R0 design-only.** Live ingest (archives, citation graphs, genealogy corpora) and any
**published** post are **G7 + Council-gated** (`:post/published` false at R0). Live narration
routes through Murakumo (G6). New lexicons: `com.etzhayyim.influence.{influencePost,influenceFlow}`.

## Coverage measurement + ingest (scaling the seed)

```bash
# honest coverage report (eras, civilizational streams, denominators, gap map)
bb -cp 20-actors -m tsumugi.methods.coverage-report
# offline influence ingest (Wikidata-P737-shaped fixtures → :flow/ 縁, merged with seed)
bb -cp 20-actors -m tsumugi.methods.ingest-influence
#   → out/seed-plus-ingest.kotoba.edn — run analyze/coverage on THIS to see the lift
bb -cp 20-actors -m tsumugi.methods.analyze-influence 20-actors/tsumugi/out/seed-plus-ingest.kotoba.edn
# live ingest (REAL Wikidata WDQS P737 fetch, stdlib urllib) — G7-gated, refused w/o operator gate:
TSUMUGI_OPERATOR_GATE=1 TSUMUGI_OPERATOR_DID=did:web:… \
  python3 …/ingest_influence.py --live --limit 200 [--no-pantheon]
#   → writes out/seed-plus-ingest-live.kotoba.edn ONLY (gitignored); the committed seed is
#     NEVER auto-mutated — promotion into the canonical seed is a separate human-reviewed PR.
# tests (25 total: 12 invariant/seed + 13 coverage/ingest incl. hermetic WDQS-parse fixtures)
python3 20-actors/tsumugi/tests/test_influence.py
python3 20-actors/tsumugi/tests/test_ingest_coverage.py
```

**Coverage truth (honest):** all-past-humanity coverage is ~0 **by design** (a bounded
`:representative` sample). `coverage_report.py` measures the *useful* coverage — the major
influence backbone of recorded thought — and names what is thin/missing. After Wave 2:
**11/11 eras · 17/17 civilizational streams · 1 connected component · 0 isolated**; figures
≈ 0.05% of MIT-Pantheon's 88,937 notables. Raising the real count needs the G7-gated
`ingest_influence.py` live path (Wikidata `influencedBy` P737 / Pantheon → `:flow/` 縁;
**N4 admits deceased/settled public figures only** — living-private persons stay the
Council-Lv7+ `:human` scale; **N1** keeps notability off the node, influence on the edge).

The live WDQS fetch is **wired + verified against real Wikidata** (a gated smoke pulled real
P737 pairs, N5 held on real BCE/CE dates, output to `out/` only). **Honest follow-up**: an
unanchored `LIMIT N` query returns arbitrary influence pairs that are disconnected from the
curated backbone (components rise). To grow the *connected* graph, the live query should be
**anchored** to existing seed figures (P737 neighbours of seed QIDs) or domain-filtered —
a query refinement, not a wiring gap.

---

# Scale-agnostic 産官学報 (A) + 旗 hata ideology/faction (B) extension (ADR-2606092000)

Two in-place extensions that answer *「地域・国・思想・組織ごとの意識的な power dynamics も
すべて精微か。長崎では三菱の方・県庁の人・新聞のやつ…」*. The named-individual layer stays
**out by construction** (G1 no-doxxing); these make the *seat/institution-level* local +
ideological dynamics precise. No new actor — tsumugi/keizu extended in place.

## (A) scale-agnostic 産官学報 concentration

The same 取-concentration lens, run at **every scale** (`:global → :supranational → :national
→ :regional → :local → :intra-org`) and over **every collective type** (法人 / 自治体 / 学会 /
報道機関 / a department-level faction). The flagship is the LOCAL cross-sector cluster — e.g.
長崎: 三菱重工長崎造船所 (産) ↔ 県庁＋産業振興審議会 seat (官) ↔ 長崎大学 (学) ↔ 長崎新聞 (報).
"Concentration" = the **edge-primary integral of cross-sector co-location × sector-diversity**,
routed to OPENING — a structural fact, never a verdict, never a target-list.

### Five invariants (read `00-contracts/schemas/power-scale-ontology.kotoba.edn`)
- **S1 edge-primary** — concentration on `:tie/grasping-load` only; no `:pwr/power-score` (raises).
- **S2 person-excluded** — `:pwr/standing ∈ {:institutional :public-seat}`; `:private-person` unrepresentable.
- **S3 aggregate-first** — per-locality/per-scale; brokers are seat/org ids, never a person.
- **S4 sourcing honesty** — every `:tie` ≥2 public citations; under-sourced raises.
- **S5 non-adjudicating** — verdict tokens (`:癒着 :談合 :capture …`) unrepresentable (raises).
- **S6 map-not-target** — openness/resilience map routed to opening.

```bash
bb -cp 20-actors -m tsumugi.methods.analyze-scale        # → out/scale-report.md + scale-graph.kotoba.edn
python3 20-actors/tsumugi/tests/test_scale.py             # 11 tests (one per gate + 長崎 flagship)
```
First run: `jp.nagasaki` is the top cluster (all 4 産官学報 sectors woven, concentration 12.08;
三菱重工長崎造船所 the top cross-sector broker, span 3).

## (B) 旗 hata — declared ideology / faction camps

Projects the diachronic thought-streams (ADR-2606061500) **forward into the present** as 旗
(banners): which ideological standard a public-power entity **openly flies**, today's camps,
and the **bridges** (entities flying ≥2 banners = pluralism). The highest-risk extension —
hence the strictest gates. It can say *"Party A self-declares banner B (per its own manifesto)"*
and *"B descends from stream S"*; it **cannot** say *"entity Y is secretly extremist"* or rank
anyone by conviction — by construction.

### The thought-policing guard H1–H7 (read `00-contracts/schemas/banner-ontology.kotoba.edn`)
- **H1 public-declared basis only** — `:flies/basis ∈ {:self-declared :public-stated :voting-record
  :formal-membership}`; `:inferred/:suspected/:imputed` raise. No hidden/"real" ideology.
- **H2 non-adjudicating** — threat tokens (`:extremist :過激 :危険思想 :terrorist`) unrepresentable;
  no `:banner/threat-level`, no `:ent/loyalty`.
- **H3 edge-primary** — alignment on `:flies/*` only; no `:ent/ideology-score` (raises).
- **H4 person-excluded** — `:flies/who` institutional/public-seat/self only; not a belief registry.
- **H5 mirror + symmetric** — etzhayyim discloses its OWN Charter banner (inbound-only), not from a feigned neutral.
- **H6 plural + contested** — many-to-many; ≥2-banner entities are bridges, not anomalies.
- **H7 sourcing** — every `:flies` ≥2 public citations (own manifesto/statement/vote).

```bash
bb -cp 20-actors -m tsumugi.methods.analyze-banner      # → out/banner-report.md + banner-graph.kotoba.edn
bash 20-actors/tsumugi/run_tests.sh # 11 tests (one per gate + camps/bridges/genealogy)
```

**R0 design-only.** `:representative` bounded seeds (学会X / 政党A·B·C are representative
placeholders, NOT assertions about named orgs); committed banner seed aligns only self-declared
public platforms + abstract streams (no corporate/newspaper/person ideology labels — that stays
G7-gated). Live locality + entity↔banner ingest is G7 + Council + operator-gated. Murakumo-only
narration (G6). New lexicons (future, gated): `com.etzhayyim.power.{scaleCluster,bannerCamp}`.

## Granularity waves + Murakumo narration (/loop, ADR-2606092000)

A recurring `/loop` (every 30 min) progressively details the intel across **全世界の
組織・地域・コミュニティ・社内派閥・学閥** granularities and keeps it narratable on the
Gemma-4 Murakumo fleet. Standing structure:

- **Granularity axis** `:pwr/collective-kind ∈ {:org 組織/企業単位 :region 地域(県) :municipality
  市区町村 :community コミュニティ :intra-org-faction 社内派閥 :academic-clique 学閥 :keiretsu 系列
  :advisory-body 審議会}` + a `:pwr/scale` that now drops one step below 県:
  `…:regional(都道府県) → :municipal(市区町村/基礎自治体) → :local → :intra-org`. Still
  seat/institution-level (S2): a 学閥 is a cross-institution CLIQUE of public SEATS, a 社内派閥
  a faction of SEATS under one org — **never a roster of persons**. `analyze_scale.py` emits a
  per-collective-kind aggregate (粒度 section). Each `/loop` wave adds more `:representative`
  instances: wave 1 社内派閥/学閥/community · wave 2 愛知/広島 (県) · wave 3 豊田市/長崎市
  (市区町村) + トヨタ企業単位 (本社↔事業部↔労連). 豊田市 = flagship 企業城下町.
- **Murakumo narration** (`cell:tsumugi.narrate`): `methods/narrate.py` (stdlib twin, urllib →
  LiteLLM `127.0.0.1:4000`, **refuses non-fleet host**, operator-gated, dry-run default) +
  `deploy/agent.py` (canonical in-WASM `kotoba_langgraph` → `KotobaLLM` → host
  `MURAKUMO_DEFAULT_MODEL` gemma4; himawari pattern). G6 Murakumo-only · G7 `published=false`.
  See `deploy/README.md`.

```bash
bb -cp 20-actors -m tsumugi.methods.narrate            # dry-run → out/narration.dryrun.md
python3 20-actors/tsumugi/tests/test_narrate.py         # 9 tests (Murakumo-only + aggregate-safe)
```

## Coverage measurement + G7-gated live ingest — scale/旗 (ADR-2606092000)

```bash
# honest gap audit (per-scale/kind/sector/country + denominators, all ~0 real by design)
bb -cp 20-actors -m tsumugi.methods.coverage-scale        # → out/coverage-report.md
python3 20-actors/tsumugi/tests/test_coverage_scale.py     # 6 tests
# OFFLINE structural-public org ingest (Wikidata-P749-shaped fixtures → :pwr + :tie :custodies)
bb -cp 20-actors -m tsumugi.methods.ingest-scale
#   → out/seed-plus-ingest-scale.kotoba.edn — run analyze_scale on THIS for the lift
# LIVE Wikidata P749 fetch (stdlib urllib) — G7-gated, refused without the operator gate:
TSUMUGI_OPERATOR_GATE=1 TSUMUGI_OPERATOR_DID=did:web:… \
  bb -cp 20-actors -m tsumugi.methods.ingest-scale --live --ring2 --limit 400   # Wikidata, SELF-EXPANDING
TSUMUGI_OPERATOR_GATE=1 TSUMUGI_OPERATOR_DID=did:web:… \
  bb -cp 20-actors -m tsumugi.methods.ingest-scale --live --gleif --limit 150   # GLEIF L2 (second source)
#   → writes out/ ONLY; the committed seed is NEVER auto-mutated (promotion = reviewed PR =
#     the Council ratification act). --ring2 derives anchors from the seed's own citation QIDs
#     (each promotion enriches the next ring); --gleif uses curated VERIFIED LEIs + a runtime
#     legalName guard (GLEIF name-search is fuzzy; even exact names collide).
python3 20-actors/tsumugi/tests/test_ingest_scale.py       # 24 tests (S2/S4/S5/G7 + anchored/ring2/gleif)
```

- **`coverage_scale.py`** measures the gap honestly: scales 7/7 · kinds 8/8 · sectors 6/6 ·
  countries 8/195 (4.1%) — machinery fully exercised, real-world coverage ~0 BY DESIGN.
- **`ingest_scale.py`** is the path that scales (A) coverage: STRUCTURAL-PUBLIC only (orgs +
  parent-org `:custodies`), **S2 person-excluded** (live query constrained to org class), S4
  ≥2 citations, S5 factual, **G7** offline-by-default / `--live` gated to operator + Council,
  **seed never auto-mutated**. **Banner (旗) live ingest is deliberately NOT automated** —
  auto-imputing ideology is the H1 failure mode; banners stay human-authored.

## etzhayyim as a power-data PROVIDER + biological foraging (ADR-2606092000 wave 3)

```bash
# PUBLISH the woven graph as self-sovereign linked data (etzhayyim's OWN vocabulary)
bb -cp 20-actors -m tsumugi.methods.publish        # → out/etzhayyim-power-graph.{nt,jsonld} + dataset-manifest.json
python3 20-actors/tsumugi/tests/test_publish.py     # 14 tests
# FORAGE — 粘菌/菌糸 growth plan (offline, from the seed): harvested vs frontier tips, starvation→fruit
bb -cp 20-actors -m tsumugi.methods.ingest-scale --forage   # → out/forage-plan.json
```

- **Provider** (`publish.py`): the inversion of dependence — etzhayyim stops being only a
  Wikidata/GLEIF *consumer* and becomes a *source*. Vocabulary `https://etzhayyim.com/ns/power#`,
  entity IRIs `https://etzhayyim.com/id/power/<id>`; JSON-LD + N-Triples (SPARQL-loadable) + DCAT
  manifest (license + `did:web:etzhayyim.com:actor:tsumugi` publisher + **content-hash = the
  dataset's self-sovereign identity**). Publishes the layers no upstream has (産官学報
  concentration, scale/sector/collective-kind, vertical integration) as etzhayyim-authored
  (`epw:derivedBy`). S2 survives (only `epw:Org`/`:PublicSeat`/`:Locality`). = the 植物-producer
  niche of ibuki's food web — the colony feeds humanity, not only itself.
- **Foraging** (`--forage`): cadence by HUNGER not clock — harvested anchors vs frontier tips,
  and substrate-starvation → fruit (switch source). The daily routine reads `out/forage-plan.json`.

## kotoba-native routine — self-expansion on etzhayyim's OWN fleet, not Claude-cloud (ADR-2606092000)

The recurring loop runs on the **Mac-mini fleet** (`70-tools/scripts/fleet-heartbeat/heartbeat.sh`
beats `methods/autorun.py`), recording each cycle on the **local append-only kotoba Datom log** —
**zero dependency on Anthropic's cron-routine cloud** (which is also network-blocked for WDQS/GLEIF,
so it could only re-promote offline fixtures). Self-sovereign substrate, mirroring ibuki/shionome.

```bash
bb -cp 20-actors -m tsumugi.methods.autorun --cycles 1   # one offline beat → :tsumugi.cycle/* datoms
ACTORS="tsumugi" 70-tools/scripts/fleet-heartbeat/heartbeat.sh   # via the fleet beat (tsumugi is in DEFAULT_ACTORS)
```

- **A beat is OFFLINE + deterministic + fail-open**: forage (粘菌/菌糸 growth plan) → publish
  (植物-producer: regenerate the provider linked-data so etzhayyim keeps FEEDING others) → measure
  (coverage + seed stats) → append a content-addressed `:tsumugi.cycle/*` tx (chain-verified;
  same seed + same beat index → byte-identical head CID). Local log is `.gitignore`'d / regenerable.
- **Live WDQS/GLEIF ingest stays operator-gated** (`TSUMUGI_OPERATOR_GATE`, run on the fleet where
  network IS available) — the heartbeat beats only the offline metabolism, never live I/O (kanjo
  EDGAR pattern). Scheduling = a fleet-node `cron`/`launchd` entry calling heartbeat.sh — on
  etzhayyim's own machines, recorded on its own kotoba log.

```bash
python3 20-actors/tsumugi/tests/test_autorun.py   # 11 tests (content-addressed chain + determinism)
```

## Pinned to IPFS + resolves at etzhayyim.com (ADR-2606092000 wave 5)

```bash
bb -cp 20-actors -m tsumugi.methods.publish-ipfs            # pin: 80-data/tsumugi-power/ + apex descriptor
bb -cp 20-actors -m tsumugi.methods.publish-ipfs --verify   # re-content-address vs the manifest
python3 20-actors/tsumugi/tests/test_publish_ipfs.py         # 11 tests (incl. ipfs-add CID vector)
```

- **(A) PIN**: artifacts gzipped (mtime=0 → deterministic) + content-addressed to a kotoba IPFS
  **CIDv1 (raw, sha2-256)** — byte-identical to `ipfs add --cid-version=1 --raw-leaves`, verifiable
  with `rasen/methods/cid.py`, no daemon. Home: **`80-data/tsumugi-power/`** (graph/nt/jsonld `.gz`
  + `publish-manifest.json` + `PUBLISH.md`). Committed → durable + pin-able by CID.
- **(B) SERVE**: `https://etzhayyim.com/ns/power` (resolvable epw: vocabulary) +
  `https://etzhayyim.com/dataset/tsumugi-power.json` (CIDs + gateway links) via the apex Worker
  static dir (`50-infra/etzhayyim-did-web/public/`). Live on next `wrangler deploy`. The data lives
  on IPFS (host-independent); etzhayyim.com only advertises it.

---

## Self-publication seed (ADR-2606272355) — register → autonomize → publish, no-server-key

tsumugi joins the actor self-publication seed (danjo 弾正 = reference impl): the uniform,
charter-clean way for a power-mirror actor to be registered at etzhayyim.com, run autonomously
on the kotoba mesh, and **self-publish its own HISTORY + procedures** to AT-proto **without any
server-held key**. We plant the seed; the actor grows on the mesh (murakumo,
`orgs/com-junkawasaki/murakumo/`) and self-custodies its signing identity in its WASM runtime.

**The social cell did NOT pre-exist** — tsumugi already had a dry-run *influence-history* mirror
projection (`methods/project_influence_posts.cljc`, ADR-2606061500, a DIFFERENT axis: observer-voice
posts ABOUT historical figures' documented influence) and a Murakumo narrator (`methods/narrate.cljc`),
both left untouched. The self-publication seed adds the danjo-shaped membrane + power-graph
self-publication projection this actor lacked. tsumugi was already did-web-registered and in the
actor-profile-seed SSoT; no new registry entry was created.

The seed (all LANDED this wave; nothing else rewritten):

- **did-web registration** (pre-existing) — `50-infra/etzhayyim-did-web/public/actor/tsumugi/{did,profile}.json`
  (`verificationMethod: []` — no server-minted key, did:web trust root = TLS). Only `_meta.adr` was
  updated (+ `2605231525`, `2606230001`, `2606272355`).
- **social_post membrane** (NEW) — `cells/social_post/state_machine.cljc`
  (ns `tsumugi.cells.social-post.state-machine`), a 1:1 port of danjo's membrane: DRAFTS a record into a
  **dry-run** post ONLY if ≥2 public-source citations (G5) + non-adjudicating, edge-primary mirror with the
  disclaimer (G2) + `server_held_key` false (no-server-key) + status `dry-run`. A `published` request
  REFUSES. Disclaimer adapted to tsumugi's posture: `【観測ミラー / 取-concentration release map —
  NOT a target-list, 非断定・person-excluded】`. Verified under `bb`: `<2 sources / server-key / published
  → refused`, valid → `drafted` with `:post/status :dry-run`, `:post/server-held-key false`.
- **publication projection** (NEW) — `methods/social.cljc`: projects tsumugi's HISTORY — the
  **取-concentration findings** over public power-entities (each entity's 取-holding = the integral of its
  incident 縁, computed on read, N1) + the **RELEASE routing** (release-target = max(0, held−1) → 解放) +
  the **産官学報 scale clusters** (seat/institution-level, S2 person-excluded) — into `app.bsky.feed.post`-shaped
  dry-run posts (`draft-concentration-post` / `draft-release-post` / `draft-cluster-post`); `enough-sources`
  raises on <2 (G5); `build-live` raises (live gate). Verified under `bb`.
- **seed trigger wiring** (NEW) — `kotoba.app.edn` `tsumugi-social` component (`on-tick "0 */6 * * *"`
  + `on-kse etzhayyim/actor/tsumugi/publish`, `:requires #{:cap/kqe :cap/atproto}`).

**Division of labor (zero-knowledge)**: the **planter** authors the in-repo seed (holds no key); the
**operator** (founder) runs `bb murakumo deploy 20-actors/tsumugi/kotoba.app.edn <node>` with
`MURAKUMO_OPERATOR_SEED` + Tailscale and exercises the Council gate for the first live post; the
**actor's mesh runtime** self-generates/self-custodies its `did:key`, presents a member CACAO leash
(ADR-2606111400), and signs its own posts. The server never signs. R0 = dry-run drafts only; live
broadcast is Council Lv6+ + operator + member/actor-signature gated (§1.12 / G11).

```bash
bb -e '(load-file "methods/social.cljc")'                 # projection loads green
bb -e '(load-file "cells/social_post/state_machine.cljc")' # membrane loads green
# operator step (zero-knowledge — needs MURAKUMO_OPERATOR_SEED + Tailscale):
#   bb murakumo deploy 20-actors/tsumugi/kotoba.app.edn asher
```
