# aburi 炙り — personal-tracking-exposure observatory (member-side, own-data)

**ADR**: 2606161630 · **depends**: 2605192100 (Mission Charter — §2(c) v3.1 reciprocity axis via
ADR-2606082400: monetized-OR-asymmetric surveillance prohibited / symmetric 相互監視 affirmed;
§1.13 Wellbecoming / anti-addictive) + 2606071601 (sukashi — the firm-side ad-tech supply chain) +
2606082100 (shiori — wellbecoming detraction, the surveillance/addictive-design burden) +
2605302130 (himotoki — DSAR / own-data pattern) + 2606112201 (kaiyaku — sever) + 2605312500
(kurashimori — consumer opt-out) + 2606101400 (tedai — on-device permission revoke) + 2605181100
(PII envelope) + 2605231902 (MST membrane) + 2605312345 (Datom = canonical state) + 2605215000
(Murakumo-only). **Status**: 🟡 R0 design-only.

aburi ("炙り" = 炙り出す — to bring hidden things to light by heat, as invisible ink reveals
itself; the craft-word sibling of sukashi 透かし "watermark / see-through") is the **member-side,
own-data** inversion of the ad-tech lineage. Where **sukashi** maps the **firm↔firm** programmatic
supply chain (ads.txt / sellers.json) and **shiori** maps cohort-scale wellbecoming detraction,
aburi answers the question the founder asked directly:

> **「Google・Facebook・X・Apple の規約に同意し権限を許可したとき、自分の情報がどの広告ネット
> ワーク／データブローカーに取得されているのか、どの企業がどれだけ追跡しているのかを可視化する」**

It weaves the member's OWN **surfaces** (search / social / app-store / mobile-app / OS), the
**permissions & ToS data-sharing clauses** they grant on each, the **collectors** (ad networks /
data brokers / tracker SDKs / analytics) that receive the data, the **data-types** that flow, and
the **relief routes** that close the gap — into the kotoba Datom log, and surfaces, on read:

1. **Who tracks you most** — `exposure[collector]` = Σ inbound `:flows-to` × disclosed
   permission-sensitivity (the 取-holders: which ad networks & brokers collect the most of you).
2. **Which platform exposes you most** — `surface_leak[surface]` (Google / Facebook / X / Apple…).
3. **What kinds of your data are most harvested** — `spread[datatype]`.
4. **The reciprocity gap** — permissions that leak but have **no opt-out / DSAR route**, routed to
   himotoki (DSAR) / kaiyaku (sever) / kurashimori (consumer opt-out) / tedai (on-device revoke).

This closes the gap the prior roster left open: sukashi is firm-side (never "who tracks ME"),
akashi is platform ad-library disclosure, himotoki is the request mechanism — but no actor fused
*the member's own exposure map* with *a route to relieve it*. aburi is that fusion, built **inside**
the Charter: it is the tool that restores **reciprocal sight** against the paradigm asymmetric
watcher (ad-tracking), not a new watcher.

> **Why this is charter-clean, not surveillance.** §2(c) v3.1 (ADR-2606082400) prohibits
> *monetized-OR-asymmetric* surveillance and *affirms* symmetric 相互監視. Ad-tracking is the
> archetypal asymmetric watcher: it sees you, you cannot see it. aburi makes that one-way mirror
> two-way **for the watched** — it holds only the member's OWN, local-only data, builds no dossier
> of anyone, never tracks, never sells. It is the reciprocity instrument, not its violation.

## Hard gates (constitutional — read before any change)

- **G1 — OWN-DATA-ONLY / member-principal.** aburi maps the MEMBER'S OWN exposure, derived from
  THEIR OWN consented exports (Google Takeout / Apple "App Privacy" labels / Google Play "Data
  safety" / on-device permission dump). The PUBLIC seed is **REPRESENTATIVE** (real public-catalogue
  facts, **no real person**). **NO record of any OTHER person, NO third-party PII, NO biometric, NO
  raw identifier value.** This is the himotoki/meisai own-data pattern — aburi is **never a profiler
  of others**. Test: `test_g1_own_data_no_other_person`.
- **G2 — edge-primary (N1).** Exposure lives ONLY on edges (`:en/load`). A collector's
  tracking-exposure = the **integral of its incident inbound `:flows-to` 縁 × disclosed
  permission-sensitivity weight**, computed **on read** — never a stored per-collector score. There
  is no `:aburi/score-of-collector`.
- **G3 — non-adjudicating (N3).** Collector catalogue membership + collector→data-type mappings +
  sensitivity bands are **DISCLOSED facts** (Exodus Privacy / εxodus, IAB sellers.json / ads.txt,
  Apple App-Privacy nutrition labels, Google Play Data-safety), never aburi verdicts. **Naming an
  SDK as an ad collector is a public catalogue fact, never an accusation of wrongdoing.** aburi
  judges no company. Test: `test_g3_collectors_are_catalogued_facts_not_verdicts`.
- **G4 — RECIPROCITY-RESTORING, not surveillance** (§2(c) v3.1, ADR-2606082400). aburi makes the
  asymmetric ad-watcher **visible to the watched**. It itself never tracks, never sells, builds no
  asymmetric per-person dossier of anyone (it holds only the member's own, local data).
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; collector→data
  mappings cite a public catalogue (`:collector/catalog`); seed bounded/representative; no
  fabricated coverage.
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated + LOCAL-ONLY personal data** (meisai pattern, the easiest gate to violate).
  The member's OWN exports are processed **locally under `data/local/` (gitignored; never
  committed/pinned/published)** — this repo is public; an exposure export in a commit is
  unrecoverable. If you add a new local-data path, add it to `.gitignore` in the same change. Live
  ingest of exports **and** any relief routing (to himotoki/kaiyaku/kurashimori/tedai) require
  member-sig + operator DID + Council. R0 = analyzer + ontology + representative public seed only.
  **No-server-key**; the loop does no network I/O.
- **G8 — no credentials / no raw identifiers.** The datom-emit attr allowlist
  (`NODE_ATTRS`/`EDGE_ATTRS`) contains **no credential or raw-identifier attribute**, so none can
  ever be projected to the substrate; the substrate stores exposure **structure** (which data-kind
  flows where), never the member's raw data values. Test:
  `test_g8_no_credential_or_raw_id_attr_in_emit_schema`.

## How it routes (the relief chain)

```
aburi (observe own surfaces + grants)
  ├─ reciprocity-gap (exposure w/ no opt-out) → himotoki (DSAR 開示・削除) / kaiyaku (sever) /
  │                                              kurashimori (consumer opt-out) / tedai (revoke on device)
  ├─ collector concentration (top data brokers) → sukashi / kabuto / tsumugi  (supply-chain
  │                                              transparency, never a target-list)
  └─ surface leak (surveillance/addictive burden) → shiori  (wellbecoming detractor)
```

aburi never carries the relief itself — it is the **map + the routing signal**. The DSAR belongs
to himotoki, the sever to kaiyaku, the opt-out to kurashimori, the on-device revoke to tedai; all
member-sig + outward-gated (G7).

## Layout

```
20-actors/aburi/
├── CLAUDE.md                              # this file
├── manifest.jsonld                        # actor manifest (cells + 8 gates)
├── data/
│   ├── seed-tracker-exposure.kotoba.edn   # REPRESENTATIVE public-catalogue graph (no real person)
│   └── local/                             # GITIGNORED — the member's OWN data only (G7)
│       ├── intake/                        #   consented exports the member drops in (Takeout/Apple/Play/perm)
│       └── persisted/                     #   the member's append-only exposure commit-DAG
├── methods/                               # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py / .cljc                 # edge-primary tracking-exposure analyzer
│   ├── datom_emit.py / .cljc              # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── coverage_report.py / .cljc         # honest coverage + gap map (G5)
│   ├── kotoba.py                          # (A) local content-addressed commit-DAG helpers
│   ├── ingest.py                          # (B) member exports → exposure graph (G2/G8 guard + adapters)
│   ├── autorun.py                         # (B) local heartbeat: sweep intake → append tx (dedup by CID)
│   └── kotoba_bridge.py                   # (A) push local log → live kotoba :8077 (dry-run; injected)
├── tests/                                 # pure stdlib (incl. G1 / G3 / G8 inversions)
│   ├── test_analyze.cljc
│   ├── test_coverage.cljc
│   ├── test_ingest.cljc                   # (B) adapters + G8 guard + analyzable end-to-end
│   └── test_bridge.py                     # (A) heartbeat + exactly-once cursor + no-server-key
├── tools/                                 # bb task impl (no .sh in this repo)
│   ├── build.clj                          #   `bb aburi:build-wasm` — cljc-native (cherry+ComponentizeJS) + CID
│   └── publish.clj                        #   `bb aburi:publish` — pin/deploy/kv orchestrator
├── wasm/                                  # (C) cljc-native WASM component (cherry+ComponentizeJS, ADR-2606261200)
│   ├── wit/world.wit                      #   exports analyze / datoms / coverage
│   ├── app.cljs · README.md               #   cljc entry; built via `bb aburi:build-wasm` (operator step)
└── out/                                   # GENERATED — do not hand-edit
```

The published registration (C) lives outside this dir: `00-contracts/schemas/actor-profile-seed.kotoba.edn`
(SSoT), `50-infra/etzhayyim-did-web/src/registry/infra-actors.ts` (tier-3 fallback), and static
`50-infra/etzhayyim-did-web/public/actor/aburi/{did,profile}.json`.

## Run

**bb is the standard runner — there are no `.sh` scripts in this repo.** All tasks are `bb.edn`
tasks run from the repo root; the pure analyzer methods can also be invoked directly with python3.

```bash
# from the repo root:
bb test:aburi                       # cljc analyzer/coverage (14 tests / 1369 assert) + python ingest/bridge (17)
bb aburi:ingest --cycles 1          # (B) ingest member exports in data/local/intake/ → local commit-DAG
bb aburi:bridge                     # (A) push local log → live kotoba :8077 (dry-run default)
bb aburi:build-wasm                 # (C) build the WASM component (cljc-native: cherry+ComponentizeJS) + report CID  [operator]
bb aburi:publish --deploy --pin --kv --verify   # (C) materialize + deploy to etzhayyim.com  [operator, CF auth]

# pure reports (methods are python/.cljc, not scripts):
cd 20-actors/aburi && python3 methods/analyze.py   # → out/tracking-exposure-report.md
```

## A/B/C — acquisition, live log, publish

- **(B) acquisition** is the answer to 「実際の取得になっている?」: `ingest.py` parses the member's
  OWN consented exports — iOS **App Privacy Report** (the ad/tracker DOMAINS each app contacted →
  catalogued collectors), Google Play **Data-safety** (data shared "for Advertising" → ad
  collectors), Google **Takeout** ad-settings, on-device **permission dump** — into the same
  exposure graph the analyzer reads. `autorun.py` dedups by intake CID and appends one content-
  addressed tx per new export. The **G8 guard raises** on any credential / raw-identifier value
  (IDFA/GAID/IMEI/email/PAN/UUID); only exposure STRUCTURE is projected. Exports + the log live
  under `data/local/` (gitignored).
- **(A) live log** is the answer to 「datomic になっている?」: `kotoba_bridge.py` transacts the local
  commit-DAG to the live kotoba engine at `:8077` (one `datomic.transact` per tx, exactly-once
  `:aburi-bridge/*` cursor, `expected_parent` chaining, `:aburi.tx/*` provenance). **Dry-run by
  default**; live requires `ABURI_KOTOBA_LIVE=1` + member-sig + operator + Council. No-server-key:
  the operator bearer is unsigned + keyed by a PUBLIC DID; the network leg is injected (tests are
  offline).
- **(C) publish** is the answer to 「etzhayyim.com で公開?」: registered in the three homes above +
  a build-ready WASM component, all driven by **bb** (`bb aburi:build-wasm`, `bb aburi:publish`;
  impl `tools/{build,publish}.clj`). The cljc-native (cherry+ComponentizeJS) build, IPFS pin, KV/kotoba ingest, and
  Worker deploy are the **operator steps**. `wasmCid` stays **null** by design until the operator pins —
  **not byte-reproducible** (each build yields a different CID, and the apex `/ipfs` gateway
  re-verifies bytes against the CID), so the operator records the **pinned** CID at
  `bb aburi:publish --pin` time, never committed in advance.

## Cross-links

aburi sits beside **sukashi** (the firm-side ad-tech supply chain — aburi's collectors map into
sukashi's `org.corp.*` id space), **himotoki / kaiyaku / kurashimori / tedai** (the relief carriers
for the reciprocity gap), **shiori** (where surface-leak surveillance/addictive burden routes), and
**kabuto / tsumugi** (where collector concentration routes for transparency). The seed surfaces
**The Trade Desk / Meta Audience Network / Google AdMob / LiveRamp** as the top trackers and
**Google/Android** as the leakiest surface, and names **the ToS "share with partners" clause** as
the top **reciprocity gap** — a critical-sensitivity leak with no opt-out route yet (the next
relief to wire).
