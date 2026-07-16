# kasa 嵩 — worldwide computing-capacity growth observatory

**Tier-B · R0 design-only · ADR-2606072000**

> 嵩 (かさ) — *bulk / volume / amount.* The reckoner of how much computing capacity the world adds
> each year, from public information.

## Why

The founder asked: *「年間のコンピューターのストレージサイズ、メモリサイズ、GPU・CPU などの
コンピューティング能力の、全世界での増加量を、統計・公開情報から推測するアクターは設計されているか?」*
The answer was **no** — the closest actors stopped short:

- `kanjō` 勘定 reads ONE listed company's 決算 (per-company, not industry capacity).
- `kabuto` 兜 holds the supply graph (relationships, not volumes).
- `handotai` 半導体 tracks semiconductor *news/market*, not capacity statistics.
- `mitooshi` 見通し *forecasts* distributions — but it had no measured-actuals feeder for compute.

kasa fills the gap: a kotoba-native observatory that registers the world's annual computing-capacity
**magnitude and growth** across four domains and routes it to compute-commons sizing.

## What it measures

| Domain | Series (seed) | Source |
|---|---|---|
| **Storage** | HDD + SSD capacity shipped (exabytes/yr) | IDC / vendor headline |
| **Memory** | DRAM + NAND market revenue ($B/yr) | TrendForce press |
| **GPU / CPU** | discrete-GPU + client-CPU units (M/yr); datacenter-accelerator revenue ($B) | JPR / IDC headline |
| **Compute / FLOPS** | TOP500 aggregate Rmax (PFLOP/s); frontier-model training compute (FLOP) | TOP500 list / Epoch AI (CC-BY) |
| **Datacenter** | total power capacity (GW) | analyst estimate |

plus world **semiconductor sales** ($B/yr, WSTS/SIA) as the umbrella.

## Output (R0 seed, 2020–2024)

`methods/analyze.py` emits `out/intel-report.md` — the **annual increase (年間増加量)** table (per-
series YoY + full-span CAGR) and coverage-honest domain aggregates (e.g. storage = HDD ⊕ SSD = 1,660
EB in 2024; memory never double-counted into semiconductor). See the report for the full matrix.

## Hard boundaries

- **Public sources only** — paid terminals (Bloomberg / Gartner / IDC-report / Omdia) barred
  (Charter Rider §2(e)+§2(c)); read the press release, never the terminal.
- **No forecast** — measured/estimated actuals only; future projection is `mitooshi` 見通し.
- **Non-adjudicating / planning lens** — sizes the compute commons; never a country ranking or an
  export-control / targeting list.
- **Sourcing-honest** — `:representative` (headline, rounded) / `:estimated` (nowcast + method) /
  `:synthesized` (derived growth). No `:authoritative` rows in the R0 seed.

## Run

```bash
python3 methods/sources.py     # G1 source catalogue
python3 methods/analyze.py     # growth report + EDN
./run_tests.sh                 # 23 tests green
```

See `CLAUDE.md` for the full gate list and `MATURITY.md` for the honest R0 scorecard.
