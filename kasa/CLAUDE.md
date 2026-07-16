# kasa 嵩 — agent reference

> World **computing-capacity growth** observatory. Tier-B, R0 design-only. ADR-2606072000.
> Read the repo-root `CLAUDE.md` first; this file only adds actor-local rules.

## Identity

- **DID**: `did:web:etzhayyim.com:actor:kasa` (registration in INFRA_ACTORS pending).
- **Glyph**: 嵩 — *bulk / volume / amount*. The reckoner of the world's accumulating compute.
- **Role**: the **compute-capacity face** of the observation upper layer. kasa is the
  industry-aggregate sibling of `kanjō` 勘定 (which reads ONE company's 決算) and the demand-side
  counterpart of the silicon actors (`handotai` / `iwakura` / `fuigo` / `tsukuru`). Where kanjō
  asks *"what did one company disclose?"*, kasa asks *"how much compute did the WORLD add this
  year?"*. Lineage: `kanjō` (per-company financials), `kabuto` (supply graph), `mitooshi` 見通し
  (the forecaster — kasa hands it measured actuals, never competes with it).

## What kasa is, in one line

It reads PUBLIC headline figures + open datasets (WSTS/SIA semiconductor sales, TrendForce DRAM/
NAND, IDC HDD/SSD exabytes, JPR GPU units, TOP500 aggregate FLOPS, Epoch AI training compute) and
registers them as content-addressed Datoms, then computes the **annual increase (年間増加量)** — YoY
+ CAGR — of the world's computing capacity. It is **not** a market-research vendor, a forecaster,
or a country/company ranking.

## Hard rules (constitutional — do not weaken)

1. **Public sources ONLY (G1).** Ingest only public, redistributable HEADLINE figures + open
   datasets (WSTS/SIA + TrendForce + JPR press releases; TOP500 public list; Epoch AI / Our World
   in Data CC-BY; company filings). **Forbidden inputs**: the PAID, copyrighted FULL reports +
   subscription terminals (Gartner / IDC-report / Omdia / Bloomberg / S&P / Statista-Pro / Yole).
   Per **Charter Rider §2(e)** (anti-gatekeeping) + **§2(c)** (vendor query-tracking) — the same bar
   as kanjō. The headline figure a vendor puts in a FREE press release is admissible; the paywalled
   report / terminal compilation is not. **Read the press release, never the terminal.** Encoded in
   `methods/sources.py::admissible()`.
2. **Non-adjudicating (G2).** kasa records measured quantities + transparent growth rates. It does
   **not** rank countries/companies "ahead/behind", call a "winner", or rule on dominance.
3. **No forecasting (G4).** kasa records PAST/PRESENT actuals + measured growth ONLY. It does **not**
   project a FUTURE value — that is `mitooshi` 見通し (distribution-only forecaster, ADR-2606051800).
   The `:estimated` sourcing is for nowcasting a knowable-but-unreported PRESENT/PAST quantity
   (gap-fill), NOT a future forecast; every `:estimated` obs carries a `:compute.obs/method`.
4. **Sourcing honesty (G5).** Every obs/growth/agg carries `:*/sourcing` ∈
   `:authoritative` (parsed from a primary dataset row) | `:representative` (a public headline
   figure, rounded — the R0 seed) | `:estimated` (kasa nowcast a present/past value, with method) |
   `:synthesized` (a YoY / CAGR / aggregate kasa computed). Derived values are NEVER re-ingested as
   observations. Σ aggregates are coverage-bounded — read each against its `:compute.agg/n`; absence
   ≠ zero, and they are NOT market totals.
5. **kotoba-native (substrate boundary).** State = kotoba Datom log. No SQL / RisingWave / Lance as
   canonical store. Source CSV/PDF snapshots → DataLad → IPFS (`80-data/compute-capacity`), CID on
   `:compute.source/doc-cid` (G8 — no git-lfs).
6. **Revision = history, never deletion (G11, 非終末論).** When a source restates a prior estimate,
   kasa asserts a NEW obs and sets `:compute.obs/superseded-by` on the old one — the prior Datom is
   RETAINED. Read the truth "as-of" a date; there is no single final capacity number.
7. **Planning lens, NOT a targeting list (G9).** Capacity figures are routed to compute-commons
   sizing + labor-liberation planning (how much the religious-corp's non-rival compute donation is,
   relative to the world). They are **never** an export-control / sanctions / weaponization
   targeting list. No per-person data (society-scale aggregates only).
8. **Murakumo-only (G6).** Any LLM narration routes through the Murakumo fleet (ADR-2605215000).
9. **Coverage-honest aggregates (G12).** Aggregation is ONLY within one (domain × metric × unit ×
   scale) — memory (`:dram`/`:nand`) is a SUBSET of `:semiconductor` and lives in a distinct domain
   key, so it is structurally never double-counted; TOP500 `:petaflops` is never summed with raw
   `:flops`.
10. **Outward-gated INGEST (G7).** Live dataset fetch requires `KASA_OPERATOR_GATE=1` + an explicit
    `--fetch-epoch` + Council. R0 ships a bounded `:representative` seed; the full open-dataset parse
    ("ingest the world's compute stats") is **R1**.

## Vocabulary

`00-contracts/schemas/compute-capacity-ontology.kotoba.edn`:
- `:compute.series/*` — the DEFINITION of one measured series (domain × metric × unit × scale ×
  geography). The shape; observations hang off it.
- `:compute.obs/*` — ONE observation (a series × a year × a value), linked to its public source.
- `:compute.source/*` — a public source (publisher, access channel, license, url, doc-cid). The
  admissibility anchor.
- `:compute.growth/*` — derived YoY / CAGR (`:synthesized`, G5). The 年間増加量 — a measured rate of
  change, not a forecast.
- `:compute.agg/*` — domain / geography aggregate (`:synthesized`, coverage-honest, no double-count).

Domains: `:semiconductor :dram :nand :storage :gpu :cpu :flops :datacenter`.
Metrics: `:revenue :shipped-capacity :shipped-units :flops-installed :flops-training :power-capacity …`.

## Cells

- `cell:kasa.sources` → `methods/sources.py` — the G1 admissibility layer: which public publishers
  are ingestible, which paid terminals are barred. Emits `out/source-catalogue.kotoba.edn`.
- `cell:kasa.ingest` → `methods/ingest.py` — public rows-JSON → `:compute.series` + `:compute.obs`
  EAVT; merge with seed (`:authoritative` wins). Offline default; live `--fetch-epoch` G7-gated.
- `cell:kasa.analyze` → `methods/analyze.py` (stdlib) — obs → per-series YoY + full-span CAGR →
  domain aggregates (coverage-honest) → aggregate-first report. No forecast, no ranking.

## Lexicons (kotoba-native)

`com.etzhayyim.kasa.{registerSource,registerSeries,registerObservation,publishGrowthReport}`
— `00-contracts/lexicons/com/etzhayyim/kasa/` (path-reserved; lexicon JSON lands with R1).

## Run

```bash
cd 20-actors/kasa
python3 methods/sources.py              # → out/source-catalogue.kotoba.edn (G1 catalogue + self-check)
python3 methods/ingest.py               # offline: bridge data/ingest/*.json + seed → data/capacity.merged.kotoba.edn
python3 methods/analyze.py              # → out/intel-report.md + out/compute-growth.kotoba.edn
./run_tests.sh                          # 23 tests (15 unit + 8 invariant)
# live (G7-gated):
KASA_OPERATOR_GATE=1 python3 methods/ingest.py --fetch-epoch   # Epoch AI CC-BY dataset
```

`python3 methods/analyze.py` with no argument runs the **seed** graph alone (no ingest needed).

## Honesty (R0)

Bounded `:representative` seed of **8 public sources / 11 series / 52 observations** across 2020–2024,
spanning all four founder-named domains (storage HDD+SSD exabytes · DRAM+NAND revenue · GPU/CPU
units + datacenter-accelerator revenue · TOP500 + frontier-training FLOPS) plus datacenter power.
Figures are publicly-documented HEADLINE numbers, **rounded** — not exact dataset rows; the
frontier-training + datacenter-power rows are `:estimated` (Epoch AI / analyst estimate, each with a
method). "Ingest the world's compute-capacity stats" is the **R1** goal — full open-dataset XBRL/CSV
parse is **G7** Council + operator gated. kasa does not forecast (that is mitooshi), does not rank
countries, and gives no investment advice.
