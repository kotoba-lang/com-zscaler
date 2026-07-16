# jinushi 地主 — world land-ownership ACQUISITION (取得) mirror

The data-acquisition (取得) feeder of the etzhayyim **land-sovereignty stack** (Tree-of-Life
land doctrine, ADR-2605192100 §1.11 + ADR-2605192245). The on-chain `LandRegistry.sol` records
only **DONATED, waqf-inalienable** land and starts at **0 parcels**; jinushi is the upstream
**observational mirror** that measures *how much of the world's land we have data on, who holds
it, and where the 取-concentration is* — the map that tells the registry what to seek and routes
land back toward the commons.

It is the land-scale sibling of the KG-mirror lineage (inochi 命 / tsumugi 紡ぎ / kabuto 兜 /
kanae 鼎): ingest PUBLIC records → normalize onto the kotoba Datom log → edge-primary
取-concentration routed to **RETURN-to-commons**.

This is the **clj-native** realization of the legacy `crawler → land-owners → maps` design
(`80-data/reports/260225-land-owners-crawler-maps-design.md`), re-homed off RDBMS/KV onto the
canonical kotoba Datom log (no RisingWave/Kysely; ADR-2605262130 + 2605312345).

## What it is (R0)

Reads a kotoba-EDN land record set (`:owners` + `:parcels`) and computes, aggregate-first:

- **acquisition coverage** — acquired land area ÷ world land area, per country, with a
  self-pruning **ingest worklist** of known countries still at zero parcels.
- **land 取-concentration** — HHI over owners by area + top-holder share.
- **RETURN-to-commons candidates** — private non-aggregate holders above a documented share
  threshold, routed to Council as an **advisory** (never a write-back, never a seizure list).

`「全世界の不動産の取得 coverage は?」` is now a runnable metric, not a guess:

```
$ bb --classpath 20-actors -e "(require 'jinushi.methods.coverage 'jinushi.methods.analyze)
    (println (jinushi.methods.coverage/render
      (jinushi.methods.analyze/analyze
        (jinushi.methods.analyze/load-file* \"20-actors/jinushi/data/seed-parcels.kotoba.edn\"))))"
# → 6 countries touched, 83,207 km² acquired = 0.056% of world land; HHI 2635; worklist RU/CN/CA/IN
```

(The 0.056% is the **honest** acquisition coverage on the synthetic seed — sparse data reads as
a tiny fraction, as it should. The real number rises only with operator/Council-gated live
registry ingest.)

## Gates (constitutional)

- **G1** a RETURN/commons **MAP**, NEVER a per-person holdings dossier or occupancy target list.
  Owners are PUBLIC entities or AGGREGATE buckets; natural-person land folds to one
  `:owner/aggregate` owner with no person name; centroids are coarse region centroids, never a
  dwelling fix; the ingest worklist names **jurisdictions**, never parcels/persons;
  return-candidates are advisory + aggregate, never a natural person. Test-enforced (no
  `:person`/`:worker` token may appear in the Datom log or report).
- **G2** non-adjudicating. Owner/area are DISCLOSED facts; concentration + coverage are read-time
  aggregates flagged `:bond/is-transient`, never verdicts/scores.
- **G3** acquisition only — jinushi **cannot move land**. It asserts no transfer/mint/donation;
  only the on-chain `LandRegistry` changes a parcel's hands, and only via member donation
  (no-server-key). Routed candidates go to a human/Council, never written back.
- **G4** sourcing honesty — R0 seed is `:representative` synthetic; live registry/OSM/Wikidata
  pull (`70-tools/e7m-dataset`) is operator/Council-gated. National fractions are reported only
  where land area is documented, never guessed.

## Methods (pure, portable .cljc — file I/O only at the `#?(:clj)` edge)

- `methods/analyze.cljc`     — ingest + normalize (owner-name suffix/case fold, sha256 record-id
  upsert/dedup) → coverage + concentration + return-candidates.
- `methods/datom_emit.cljc`  — canonical EAVT emit: ground `:owner/*` + `:parcel/*` `:add`
  datoms + derived `:jinushi/*` transient aggregates.
- `methods/coverage.cljc`    — world acquisition-coverage report + self-pruning ingest worklist.
- `methods/ingest.cljc`      — **REAL multi-source acquisition** from COMMITTED snapshots →
  `{:owners :parcels}`, offline; double-count-honest (counting sources only) + **`sanitize`**
  data-quality gate (drops parcels larger than their country, using the real area denominator).
- `methods/normalize_wdqs.cljc` — PROCESS raw WDQS (`*.raw.json`) → committed snapshots
  (canonical unit map km²/ha/decare/dunam/acre/m²/sq-mile/rai/feddan + salvage parse, in code).
- `methods/cid.cljc`         — CIDv1 (raw/sha2-256) content-addressing of snapshots (R1).
- `methods/emit_real.cljc`   — emit the REAL acquisition → canonical kotoba Datom log + CID.
- `methods/verify.cljc`      — integrity: committed snapshots ↔ `ingest-provenance.json` (CID+sha256).
- `methods/buildings.cljc`   — building-level ownership KG (owner + floors + height) + company
  linkage (LEI/QID → corp KGs); 取-concentration by #buildings AND by total floors controlled.
- `methods/company_link.cljc` — AUTHORITATIVE company linkage: building-owner LEI → GLEIF legal
  entity (legal name/jurisdiction/status) → kabuto/uchiwake/kanjō; QID → keizu/tsumugi.
- `methods/digest.cljc`      — CAPSTONE: fuses LAND + BUILDINGS + floors + COMPANY linkage + gate
  into one 全世界 不動産取得 digest (the headline answer; read-only, content-addressed inputs).
- `methods/nyc_pluto.cljc`   — government open-data cadastre beyond WDQS: NYC PLUTO (Socrata) →
  parcel owner + floors; US-NY gate-permitted natural persons (anonymized on publish, orgs named).
- `methods/confidence.cljc`  — per-source reliability (信頼度): trust tiers + trust-weighted
  conflict resolution (gov/registry > curated-crowd > open-crowd > web; disagreement recorded).
- `methods/diff.cljc`        — as-of DIFF (差分): added/removed/changed records between snapshots.
- `methods/osm_buildings.cljc` — OSM building stock (ODbL, open-crowd 0.60): building:levels
  (floors) + operator; 5th source.
- `methods/dvf_values.cljc`  — FR DVF (DGFiP/Etalab open data): property TRANSACTION VALUES
  (€, €/m²), multi-commune, no owner identity (gate-clean); 6th source, new VALUE dimension
  (Paris-5e €12,707/m² vs Saint-Étienne €1,438/m²). digest.cljc now fuses VALUE + RELIABILITY too.
- `methods/value_trend.cljc` — property-value as-of trajectory (YoY €/m²; 差分 on the VALUE
  dimension; Wellbecoming = trajectory). Paris-5e -3.4% 2022→2023.
- `methods/scale_ingest.cljc` — PRODUCTION-SCALE streaming ingest (R2): bounded-memory line-stream
  of full-bulk CSVs (PLUTO ~860k / nationwide DVF); person names anonymized on-the-fly; aggregates
  identical to the sample path. Operator-run per `PRODUCTION.md`.
- `methods/emit_all.cljc`    — UNIFIED canonical kotoba Datom log across ALL 6 sources / 4
  dimensions (land+building+company+pluto+osm+dvf), source-tagged for confidence; 35,669 datoms, CIDv1.
- `methods/reconcile.cljc`   — cross-source owner reconciliation (信頼度 payoff): join owners on
  LEI, resolve name by trust (GLEIF authoritative wins over Wikidata crowd label; disagreement recorded).
- `methods/jurisdiction.cljc` — per-jurisdiction PUBLIC-RECORD gate: which cadastres are
  public/bulk/owner-names-visible → whether natural-person ownership may be BULK-ingested
  (honest degrade to :unknown; SE/US/GB/IE/NL/NO=bulk-public, JP/KR=per-parcel, DE/AT/CH/FR=restricted).
- `methods/fetch_wdqs.sh`    — polite, EXPLICIT, operator-only WDQS refresh of the snapshot.

## Real acquisition + WDQS load discipline (operator directive 2026-06-16)

REAL data ingests PUBLIC protected land from Wikidata — G1-safe (public owners, no persons, no
coordinates; only country + area + a per-source per-country public-owner bucket). **Data lands in
the repo DATA LAYER via the datalad substrate** (ADR-2605241500; the genome convention) and the
actor PROCESSES it later:

```
80-data/jinushi-land/
  *.raw.json                            # RAW WDQS fetches (gitignored; annex/IPFS cold tier)
  wikidata-national-parks.kotoba.edn    # Q46169  — PRIMARY world-coverage source (counts=true)
  wikidata-nature-reserves.kotoba.edn   # Q179049 — observed-only (counts=false; overlap)
  country-areas.kotoba.edn              # Q6256 P2046 — real per-country denominator (203 cc)
  ingest-provenance.json                # sources + derived / sha256 / cidv1 / unit-map / pin path
  .gitignore                            # *.raw.json + the derived Datom log (regenerable; cold tier)
  (jinushi-land-datoms.kotoba.edn)      # DERIVED canonical EAVT Datom log (gitignored; CID in provenance)
```

Pipeline: `fetch_wdqs.sh` (operator, polite) → `*.raw.json` in the data layer →
`normalize_wdqs.cljc` (PROCESS later: canonical unit map + salvage parse, in code) → committed
snapshots → `ingest`/`emit_real` (offline).

| source | class | records | countries | area | counts toward world coverage |
|---|---|--:|--:|--:|---|
| national parks | Q46169 | 2153 | 137 | 7.07M km² (sanitized) | **yes** (primary, non-overlapping) |
| nature reserves | Q179049 | 497 | 3 | 0.23M km² | **no** (overlaps NP countries NO/IE/CA) |

**National-park (protected public land) coverage = 137 countries · 7.07M km² = 4.75% of world land** (this is protected-public-land, NOT all-land-ownership; owner/value/floor layers are sample-scale — G4 honesty) (HONEST, sanitized).
A real WDQS country-area denominator (`country-areas.kotoba.edn`, 203 countries) now (a) resolves
national fractions for every covered country and (b) drives a **data-quality gate (G4)**: parcels
whose area exceeds their country's total area are dropped (Wikidata P2046 unit errors / ocean-
spanning marine megaparks). This **corrected the headline 6.67% → 4.17%** — just 5 outlier parks
had inflated it by ~3.7M km². The earlier loop figures (0.056% → 3.34% → … → 6.67%) were RAW
(pre-sanitization) upper bounds; 4.17% is the honest current value, itself still an upper bound
(sub-country marine parks + overlapping parks are not yet geometry-de-duped). The real acquisition
is emitted to the **canonical kotoba Datom log** (`methods/emit_real.cljc` → `jinushi-land-datoms.kotoba.edn`,
ground `:owner/*`+`:parcel/*` `:add` + derived `:jinushi/*` transient), making the world land
data first-class canonical state (ADR-2605312345); the log is regenerable + content-addressed
(CID in provenance), so it is not committed to git. Multi-source is
**double-count-honest** (G2/G4): only non-overlapping counting sources sum into world coverage;
overlapping protected-area classes are observed separately until a geometry de-dup leg exists.
Units resolved at snapshot time (km²/hectare/decare/dunam/acre/sq-mile/m²); non-positive
bad-data areas dropped (disclosed). Each snapshot is **content-addressed to a CIDv1**
(`methods/cid.cljc`, raw/sha2-256, `bafkrei…`, recorded in `ingest-provenance.json`). Cold tier
(git-annex local-store → IPFS CID map → PDS `datasetPin`) is the operator step via
`e7m-dataset add 80-data/jinushi-land` against superdataset `90-docs/baien/datasets` — not auto-run.

## Building-level ownership + company linkage (operator directive 2026-06-16)

jinushi extends from land-AREA coverage to per-BUILDING ownership: who owns which building, how
many floors, and — via the owner's **LEI (P1278)** / Wikidata QID — the **bridge to the corporate
KGs** (kabuto 兜 · uchiwake 内訳 · kanjō 勘定 · keizu 系図 · tsumugi 紡ぎ). Current slice (`wikidata-buildings.kotoba.edn`, six polite WDQS fetches): **2,405 buildings · 19
countries · 1,389 owners · 221 LEI links · 313 natural-person owners · 722 with floors · 168 with
height**. Two distinct 取-concentration lenses: by **#buildings** = rail operators (Bane NOR, SNCF,
Irish Rail — many station buildings); by **TOTAL FLOORS controlled (ビルのフロア)** = real-estate
developers — **Mitsui Fudosan 407F, Mitsubishi Estate 315F, Oxford Properties 283F, JR Central
222F, Ivanhoe Cambridge 207F** — the vertical real-estate concentration the registry exists to
surface. Emitted
as a KG Datom log (`:building/*` nodes + `:building/owner` edges + `:owner.org/{wikidata,lei,label}`).
**202 `:natural-person` owners** (US/FR-heavy) demonstrate the reframed gate at scale — public-
registry natural-person owners represented, not excluded.

**Authoritative company linkage** (`methods/company_link.cljc` + `gleif-companies.kotoba.edn`):
each building-owner LEI is resolved against the **GLEIF public register** to its authoritative
legal identity (legal name / jurisdiction / status). **221 owners → GLEIF, 690 buildings linked** across 56 jurisdictions (30+ US states + JP/FR/NO/GB/
IT/FI/PL/IL/…) — SNCF · JR East · RATP · Mitsui Fudosan · Mitsubishi Estate. The LEI is the cross-actor join key into the corporate KGs (kabuto/uchiwake/kanjō), the QID
into keizu/tsumugi — so "who owns this building" resolves to a real, registry-grounded company.
GLEIF registers legal persons only, so this layer is corporate by construction.

**Reframed gate (the charter does NOT ban personal data).** Land/building ownership is PUBLIC
RECORD. The constitution bans **asymmetric or monetized** surveillance (Rider v3.1 §2(c)
reciprocity axis, ADR-2606082400) while **affirming reciprocal/symmetric 相互監視** (Tier-0
神の監視, 村社会 transparency). So the gate is NOT "exclude natural persons" — it is:

- **P1 PUBLIC-RECORD provenance** only (already-disclosed registry / open KG; never covert/inferred).
- **P2 RECIPROCAL / SYMMETRIC** — the registry is open to all equally; an owner is as visible as
  anyone. Mirrored transparency, not a one-way watch-feed.
- **P3 MAP-NOT-TARGET, NON-MONETIZED** — routed to commons-return / transparency, never a
  seizure / eviction / targeting list, never sold.

Natural-person ownership is therefore **representable from a public registry** under P1–P3 (this
Wikidata slice happens to be all legal entities; `:owner/type :natural-person` is a public-record
attribute, not a person-exclusion). What stays unrepresentable: covert/inferred ownership,
asymmetric watch-lists, monetized resale. **Per-jurisdiction grounding** (`methods/jurisdiction.cljc`):
natural-person ownership is BULK-ingested only where the registry is public-by-law + bulk +
owner-names-visible (SE/US/GB/IE/NL/NO); per-parcel-only regimes (JP/KR) and restricted ones
(DE/AT/CH Grundbuch, FR owner names) are NOT bulk-ingested; unknown jurisdictions degrade honestly.

## Sources beyond WDQS (multi-source ingest)

The ingest is source-agnostic (`{:owners :parcels}` + per-source normalize + provenance/CID +
jurisdiction gate), so WDQS is just one source. Landed/feasible additions:

- **Government open-data portals** — e.g. NYC PLUTO (Socrata, public domain) → real parcel
  ownership + floors **including natural persons** (US-NY bulk-public; `methods/nyc_pluto.cljc`,
  landed as a 1,000-parcel sample). Same pattern fits NL Kadaster BAG, FR Etalab cadastre,
  data.gov portals — gated per jurisdiction.
- **OSM** (ODbL) — `building:levels` (floors), `operator`, footprints via Overpass/Geofabrik
  (the `70-tools/e7m-dataset` OSM fetcher already exists); huge building coverage.
- **Overture Maps / open building footprints** (CC-BY/ODbL) — geometry + heights, bulk parquet.
- **GLEIF** (landed) + GLEIF L2 relationships for corporate-group rollup.
- **CommonCrawl / Internet Archive** — feasible but heavy + low-precision + license/G1 care; best
  as a *targeted* leg (mine specific public-registry domains, not blind web) — the
  kotoba-over-CommonCrawl precedent is ADR-2606012300.

Each source lands raw in the data layer (datalad cold tier), is normalized in code, content-
addressed (CID in provenance, `verify.cljc`), and obeys the per-jurisdiction public-record gate.

## Data storage + no-re-query guarantee (operator directive)

Every source is fetched ONCE; the data lives in the repo and is never re-queried in normal use:

- **Downloaded into the repo** (`80-data/jinushi-land/`, git-committed): the no-person raw
  responses (`*.raw.json`/`*.raw.csv` — WDQS parks/buildings/floors, country areas, OSM, FR DVF)
  + the
  resolution caches (`wikidata-owner-labels.raw.edn` = wbgetentities QID→label/is-human;
  `gleif-resolution.raw.edn` = LEI→legal entity) + the processed snapshots (`*.kotoba.edn`).
- **PRIVACY carve-out (G1/publish-prudence)**: `nyc-pluto.raw.json` (~1,500 ORDINARY natural
  persons by name) is **gitignored — NEVER committed** to the public repo; it lives only locally /
  IPFS cold-tier. The committed `nyc-pluto-parcels.kotoba.edn` is anonymized (persons → sha256-key,
  no name; verified 0 named persons). Notable public-figure owners from open KGs (e.g. Wikidata,
  identified by already-public QID) ARE represented per the public-record directive.
- **No method touches the network** — every `.cljc` reads local files; the ONLY network code is
  `methods/fetch_wdqs.sh` (explicit, polite, operator-run). The 30-min loop = zero network I/O.
- **No re-query, even on a from-scratch rebuild**: the raw + the label/GLEIF caches are committed,
  so regenerating snapshots reads local files only. New fetches are incremental (only genuinely
  new countries / LEIs / QIDs; already-resolved entities are skipped).
- **Content-addressed**: every artifact carries a CIDv1 in `ingest-provenance.json` (`verify.cljc`
  re-derives + checks) — the same content hash datalad/IPFS would use.

**IPFS pinned (real)**: every committed artifact is `ipfs add`-ed (CIDv1) + locally pinned;
`ipfs-pins.kotoba.edn` records the directory CID (`bafybeieg7zfh…`) + per-file CIDs. Single-block
files' CIDs are byte-identical to `methods/cid.cljc` (verified). Public retrieval = connect the
daemon to peers / a remote pinning service (operator).

**datalad / IPFS cold tier** (ADR-2605241500): the working copies are plain-git-committed (so
tests/verify run on the repo state, the genome convention). Registering them into the DataLad
superdataset + git-annex → IPFS pin is the OPERATOR step (not run here; would need the
superdataset + an annex/IPFS remote):
`e7m-dataset add 80-data/jinushi-land` against superdataset `90-docs/baien/datasets`. (A bare
datalad *subdataset* is deliberately NOT used: it would move the data out of the parent repo's
git into a gitlink, breaking the parent's tests/verify/clone — hence git-committed + CID here.)

**「wdqs に負担をかけない」 is enforced by design:**

- The committed **snapshot is the loop's source of truth**. Each loop iteration re-ingests the
  snapshot with **ZERO network I/O** (`ingest.cljc`). A 30-min loop hitting WDQS would be abuse;
  it never does.
- A live refresh is an **explicit, rare, operator-only** step (`fetch_wdqs.sh`): ONE small
  LIMITed query, descriptive User-Agent **with a contact address**, `--max-time`, a courtesy
  sleep, no retry loop, and it **refuses `LIMIT > 800`**. If WDQS hits its 60 s server cap,
  LOWER the limit — never hammer. The 15-min result cache is honoured by reusing the snapshot.
- Area is **honest**: rows whose unit could not be resolved are dropped at snapshot time and the
  dropped count is disclosed in the snapshot (`:dropped-unknown-unit`), never guessed (G4).

## Run

```bash
CP=20-actors
for ns in test-analyze test-datom-emit test-coverage test-ingest test-cid test-emit-real test-normalize-wdqs test-verify; do
  bb --classpath $CP -e "(require 'clojure.set 'jinushi.methods.$ns) (clojure.test/run-tests 'jinushi.methods.$ns)"
done
# 100 tests / 388 assertions green

bb --classpath 20-actors -m jinushi.methods.coverage     # synthetic seed → out/coverage.md
bb --classpath 20-actors -m jinushi.methods.datom-emit   # → out/jinushi-datoms.kotoba.edn
bb --classpath 20-actors -m jinushi.methods.ingest       # REAL snapshots → live world coverage (offline)
bb --classpath 20-actors -m jinushi.methods.cid          # CIDv1 of each committed snapshot
bb --classpath 20-actors -m jinushi.methods.normalize-wdqs # raw *.raw.json → committed snapshots (process)
bb --classpath 20-actors -m jinushi.methods.emit-real    # REAL acquisition → kotoba Datom log + CID
bb --classpath 20-actors -m jinushi.methods.digest       # CAPSTONE: whole 不動産取得 picture, one report
bb --classpath 20-actors -m jinushi.methods.verify       # snapshots ↔ provenance CID/sha256 integrity

# operator-only, rare, polite — refresh the snapshot from WDQS (NOT run by the loop):
methods/fetch_wdqs.sh 400
```

## Status / roadmap

- **R0 (landed)** — analyze + datom-emit + coverage + ontology + synthetic seed + 16 tests. ✅
- **R2 (landed)** — REAL multi-source public-land ingest (`normalize_wdqs.cljc` + `ingest.cljc`
  + `fetch_wdqs.sh`): committed Wikidata snapshots — national parks (1859 / **85 cc**, sanitized 6.20M km²,
  **counts**; four polite country-bound fetches) + nature reserves (497 / 3 cc, observed-only,
  overlap-excluded). World coverage **4.17%** HONEST (sanitized; raw 6.67% before dropping 5 over-country outliers). Processing is code (canonical unit map + salvage parse in `normalize_wdqs.cljc`).
  Double-count-honest (G2/G4), full unit map (km²/ha/decare/dunam/acre/sq-mile/m²), non-positive
  bad-data dropped, data in 80-data via datalad substrate, WDQS-load-safe (snapshot SoT; loop
  never queries WDQS). +7 tests. ✅
- **R1 (landed)** — CIDv1 (raw/sha2-256) content-addressing of every snapshot (`methods/cid.cljc`,
  verified against the canonical empty-block vector; CIDs recorded in `ingest-provenance.json`).
  Append-only commit-DAG (`kotodama/src/kotoba/datom.cljc` reuse) + dag-pb/UnixFS `ipfs add`
  parity remain follow-on legs. +4 tests. ✅
- **Datom log (landed)** — `methods/emit_real.cljc`: the REAL acquisition emitted to the canonical
  kotoba Datom log (counting dataset → analyze → EAVT `:add`/derived), CID in provenance. Makes
  the world land data first-class canonical state (ADR-2605312345). +4 tests. ✅
- **R2+** — broaden real sources (more national-park countries: JP/IN/AR/MX missed the LIMIT;
  protected landscapes / public-land registries / OSM landuse) one small polite batch at a time;
  geometry de-dup so overlapping protected-area classes can count.
- **R3** — bridge confirmed-donation parcels to the on-chain `LandRegistry` lane (still a member
  donation, no-server-key) + maps.etzhayyim.com `:feature/*` layer.
- **ADR** — to author: `26xxxxxxxx-jinushi-land-ownership-acquisition-mirror.md` (mirror-lineage
  pattern; land-sovereignty §1.11 grounding).
