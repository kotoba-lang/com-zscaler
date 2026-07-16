# 20-actors/hirameki — CLAUDE.md

## What this is

**hirameki 閃き** — the world **public-patent** KG-mirror observatory. The all-technology
generalization of **tokigusuri 時薬** (which mirrors the pharma patent-cliff subset): hirameki
ingests PUBLIC patent bibliographic data across every CPC technology section into the kotoba
Datom log **and persists the corpus to the DataLad dataset substrate** (`80-data/hirameki-patents/`,
ADR-2605241500: DataLad + git-annex + IPFS), content-addressed to CIDv1. Per technology FIELD
it runs edge-primary exclusivity-**concentration** vs a **release-readiness** buffer routed to
RELEASE (解放); per PATENT it reads the **release clock**.

**Supersedes** the legacy RisingWave/B2/BPMN patent pipeline (ADR-2604251024) — now kotoba-
native + clj-native, no RisingWave, no B2.

`did:web:etzhayyim.com:hirameki` · `com.etzhayyim.hirameki.*` · ADR-2606212200 · clj-native R0.

## OBSERVATION ONLY (hard invariants — proven by tests)

- **a RELEASE map, NEVER a verdict** — G1: never a patent-busting / FTO-opinion /
  infringement-determination / per-company-verdict / patent-equity signal.
  `:hirameki/infringement-verdict`, `:hirameki/fto-opinion`, `:hirameki/equity-signal` unrepresentable.
- **a patent is the GATED OBJECT, never a 取-holder** — G2: only an assignee/holder imposes
  exclusivity; `:hirameki.patent/imposes-on` unrepresentable (enforced in code + test).
- **non-adjudicating** — G3: patent status (granted/expired) + assignee share are DISCLOSED
  facts from the issuing office, never re-judged, never a forecast.
- **lawful release only** — G4: statutory expiry / public-domain / voluntary open licensing
  (pledge / pool / FRAND-zero / MPP). Circumvention / `:hirameki.patent/design-around` unrepresentable.
- **aggregate, no person-level inventor** — G6: assignee = org; `:hirameki.inventor/person`
  unrepresentable; no-doxxing (tsumugi/keizu lineage).

## Analytical core

Per **field** (pure clj, on read): top-assignee-share + named-HHI (the `:other` long tail
excluded → a lower bound) → chokepoint-risk; **exclusivity-load** = concentration × (0.5 +
0.5·essentiality); **release-readiness** = expired + ½·expiring-soon + 0.4·open-licensed;
**route** (dominant-driver) ∈ `{:release, :open-license, :de-monopolization, :monitor}`:

- `:de-monopolization` → entrenched essential chokepoint → route-around (abaki / tsumugi / kabuto)
- `:release` → majority commons-bound → track the 解放 handoff to public domain
- `:open-license` → pledgeable / concentrated-ish → voluntary opening (pool / pledge / FRAND-zero)
- `:monitor` → early-life / diversified → watch the clock

Per **patent**: the release clock — `years-to-expiry` (filing + term − ref-year) →
`release-status` ∈ `{:pending, :in-force, :expiring-soon, :public-domain, :lapsed-released}`.

## Files

```
methods/hirameki_edn.cljc  loader + classify (:field / :patent)
methods/analyze.cljc       analyze → datoms → render-datoms → coverage → render-report (+ bb CLI)
methods/cid.cljc           CIDv1 raw/sha2-256 (ipfs-add parity; clj port of rasen/cid.py)
methods/dataset.cljc       G9: patent corpus → EDN → DataLad (80-data/hirameki-patents/) + manifest (+ bb CLI)
methods/ingest.cljc        G8/G9 LIVE ingest (USPTO ODP): pure odp->patent normalizer + env-key fetch → merge → re-materialize (+ bb CLI)
methods/kotoba.cljc        content-addressed append-only OBSERVATION LEDGER (tamper-evident commit-DAG)
methods/autorun.cljc       deterministic, idempotent-by-content heartbeat — append ONLY on change (+ bb CLI)
methods/test_*.cljc        loader + analytics + G1/G2/G3/G6 + cid + dataset + ledger/heartbeat invariants
kotoba/ontology.hirameki.edn  EAVT schema + negative space (unrepresentable attrs)
kotoba/seed.edn            R0 :representative slice — 11 fields (CPC A/B/C/G/H/Y) + 7 exemplar patents
data/ (gitignored)         generated observation ledger — never committed/hand-edited
manifest.edn               gates G1–G9 + non-goals N1–N5 + method/seed/dataset/ledger registry
```

Dataset substrate: `80-data/hirameki-patents/` (corpus + datoms EDN, both CID-verified vs
`ipfs add`; `publish-manifest.json` / `ingest-provenance.json` / `PUBLISH.md`).

## Datom convention

`[":db/add" entity ":hirameki.<kind>/<aspect>" value]` (attrs are `:`-prefixed strings,
kotoba EAVT). Entities: `hirameki-field:<id>`, `hirameki-patent:<id>`, `hirameki-section:<sec>`.
Every emitted entity carries `:hirameki/sourcing` + `:hirameki/derived true` (the emission is
an observation snapshot); `:authoritative` rows additionally carry `:hirameki/source`.
Disclosed facts: `:hirameki.field/*`, `:hirameki.patent/*`. Derived: `:hirameki.obs/*`,
`:hirameki.section/*`.

## Run

```bash
bb --classpath 20-actors 20-actors/hirameki/methods/analyze.cljc        # print the RELEASE map
bb --classpath 20-actors 20-actors/hirameki/methods/dataset.cljc        # corpus → 80-data/hirameki-patents/ (+ CIDs)
bb --classpath 20-actors 20-actors/hirameki/methods/autorun.cljc        # heartbeat → append observations to ledger
./20-actors/hirameki/run_tests.sh                                       # 7 suites (39 tests / 145 assertions)
```

### G8/G9 LIVE ingest (operator step — needs a free API key)

The key-free PatentsView bulk was retired into the **USPTO Open Data Portal**
(`data.uspto.gov` / `api.uspto.gov`), which needs a **free** API key. The ingest is
**no-server-key**: the key is the operator's, read from env, never committed.

```bash
# 1) get a free key at https://data.uspto.gov  (instant)
# 2) run the live ingest — fetches authoritative patents, folds them into the corpus,
#    re-materializes the DataLad snapshot (corpus + datoms + manifest, CID-verified)
USPTO_ODP_API_KEY=… bb --classpath 20-actors 20-actors/hirameki/methods/ingest.cljc \
  "applicationMetaData.inventionTitle:semiconductor" 50 "2026-06-22T00:00:00Z"
```

The PURE normalizer (`odp->patent`) is unit-tested against a fixture (G6 drops inventor
names; G9 every authoritative row carries a cited `:source` URL). The fetch leg prints the
live record's keys on first run so a schema mismatch is caught immediately. Without a key
the CLI fails clear (exit 2) with the exact command — it never fabricates authoritative data.
The full ~200M-patent corpus then goes via DataLad→IPFS (git-annex); IPFS pin + IPNS = the
remaining operator publish step.

## R0 → later waves

- **R0 (ADR-2606212200)**: clj-native scaffold + `:representative` seed + analyze/datoms/coverage +
  CIDv1 content-address (ipfs-parity verified) + DataLad dataset materialization + append-only
  observation ledger + idempotent-by-content heartbeat + 6 test suites.
- **G8/G9 ingest tool (landed, ADR-2606212200)**: `ingest.cljc` makes the live authoritative
  ingest EXECUTABLE — pure `odp->patent` normalizer (USPTO ODP JSON → `:authoritative` rows
  with cited source, fixture-tested, G6 drops inventors) + an env-key fetch leg. Note the
  key-free PatentsView path is RETIRED → USPTO ODP needs a free key (no-server-key, env). The
  remaining operator step is: set `USPTO_ODP_API_KEY`, run, then push the full corpus via
  DataLad→IPFS (git-annex) + IPFS pin + IPNS publish.
- Later: per-field depth (CPC subclass granularity surfaces real de-monopolization cases),
  citation graph (`edge_patent_cites` → tsumugi cross-link), SEP/standards linkage, Murakumo-
  narrated digest, fleet registration, lexicons, DID registration.

## Related

- `/90-docs/adr/2606212200-hirameki-worldwide-patent-kg-mirror.md`
- `/90-docs/adr/2604251024-patent-bulk-ingest-and-blob-cid.md` (superseded — legacy RisingWave/B2)
- `/20-actors/tokigusuri/` (pharma patent-cliff sibling — N4 boundary)
- `/20-actors/busshi/`, `/20-actors/tsumugi/` (KG-mirror lineage)
- `/80-data/hirameki-patents/` (DataLad dataset substrate)
- `/CHARTER-RIDER.md` §2(e) no paid terminals
