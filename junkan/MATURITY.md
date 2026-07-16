# junkan 循環 — maturity scorecard (governance-asymmetry substrate)

ADR-2605290927 · clj-native, kotoba-Datom-native · updated 2026-06-21

Execution rule: **bb-only**. `.sh` / bash / shell runners are prohibited for
this actor; tests run via `bb 20-actors/junkan/run_tests.bb`.

## What this substrate answers

全世界の政府で **国民と政府を構造的に不均衡にしている具体的な法律・制度・思想・
価値観** を、5 つの asymmetry STOCK と feedback LOOP の system-dynamics で読み取る。
各 instrument に **誰が定めたか (enactor) / 経緯 (origin) / 関係者 (stakeholders)**
を記録。**分析専用 (G4) · 仮説のみ (G5) · 集計のみ (G6) · MAP であって target-list
ではない (G7)。**

## R0 → R1 checklist

| # | item | status |
|---|---|---|
| 1 | ontology (EAVT schema, 5 stocks, loops, Meadows, negative space) | ✅ `kotoba/ontology.junkan-gov.edn` |
| 2 | global instrument seed (laws/institutions/doctrines/values) | ✅ 465 instruments · 197 jurisdictions · 4 kinds incl :value (iter 40) |
| 3 | 誰が (enactor) on every instrument | ✅ test-enforced |
| 4 | 経緯 (origin) on every instrument | ✅ test-enforced |
| 5 | 関係者 (stakeholders) on every instrument | ✅ test-enforced |
| 6 | all 5 asymmetry stocks covered | ✅ test-enforced |
| 7 | both polarities present (widen + narrowing/balancers) | ✅ test-enforced |
| 8 | analysis read-off (stock regimes + member-stock-grounded loops + leverage + coverage) | ✅ `methods/analyze.cljc` |
| 9 | EAVT datom emission (flagged :derived + :hypothesis) | ✅ 6755 datoms |
| 9b | temporal era-trajectory analytic (widen/narrow force per era) | ✅ `analyze/era-trajectory` (iter 3) |
| 9c | EAVT/AVET/VAET arrangement queries over the datoms | ✅ `methods/query.cljc` (iter 4) |
| 10 | content-addressed findings ledger (commit-DAG, verify-chain) | ✅ `methods/kotoba.cljc` |
| 11 | deterministic idempotent-by-content heartbeat | ✅ `methods/autorun.cljc` |
| 12 | G4 analysis-only (no outward channel; by absence) | ✅ test-enforced |
| 13 | G5 hypothesis-only (no proven causation) | ✅ test-enforced |
| 14 | G6 aggregate-only (no person/PII attr) | ✅ test-enforced |
| 15 | G11 candidates-not-directives | ✅ test-enforced |
| 16 | datalad dataset (snapshot + provenance + report) | ✅ `80-data/junkan-governance/` |
| 16b | continental region coverage (balance + gap detection) | ✅ `analyze/region-of` · 5 continents balanced (iter 7) |
| 16c | transparent leverage scoring (disclosed weights + components) | ✅ `analyze/amplify-score` + `flip-score` (iter 7) |
| 16d | stock × continent cross-tab (where each asymmetry is active) | ✅ `analyze/region-stock-matrix` (iter 8) |
| 16e | substrate integrity checker (ontology↔seed↔region-map) | ✅ `methods/validate.cljc` · 0 errors (iter 9) |
| 16f | generated live SCORECARD (coverage+integrity+read-off) | ✅ `methods/scorecard.cljc` → SCORECARD.md (iter 10) |
| 16g | leverage-by-continent (most tractable flip candidate per region) | ✅ `analyze/leverage-by-region` (iter 11) |
| 16h | kind × polarity matrix (laws widen / doctrines narrow) | ✅ `analyze/kind-polarity-matrix` (iter 12) |
| 17 | tests green | ✅ 65 tests / 6404 assertions |
| 18 | live passive-data ingest (Tier-A public archives) | ⏳ R1, Council-gated |
| 16i | as-of / regime-trajectory reader (history) | ✅ `methods/history.cljc` (iter 13) |
| 19 | kotoba-kqe live-engine binding | ⏳ R1 |
| 20 | Murakumo-only LLM-assisted loop-naming | ⏳ R1 |
| 21 | India packaged-goods / loose-refill retail culture system-dynamics addendum | ✅ `methods/consumer_culture.cljc` + `kotoba/seed.india-packaged-goods.edn` |
| 22 | Country/region loop-actor design registry + validation | ✅ `methods/country_region_actors.cljc` + `kotoba/seed.country-region-loop-actors.edn` |
| 23 | India municipal solid-waste collection/segregation/processing/recycling-linkage cycle system-dynamics addendum | ✅ `methods/waste_sanitation.cljc` + `kotoba/seed.india-waste-sanitation.edn`, registered as `waste-sanitation-cycle` domain (world/IN + 6 regions) |

## Current read-off (HYPOTHESIS — see report.md)

- **coercion-asymmetry** stock reads **vicious** (net ≈ +0.24) as the global
  security-law corpus enters; other stocks read **transitioning** (contested —
  strong widening and narrowing forces both present).
- **Era trajectory** (iter 3): instruments cluster strongly in **2010–** (n=26),
  net-widening, and **1945–1989** (n=23) — the post-WWII rights wave reads
  net-narrowing only in **1990–2009**. A long-run shape, not a country ranking (G7).
- Deepest amplify-candidates: Magna Carta (L1), German Basic Law eternity clause
  (L1), UDHR (L2). Most-tractable flip-candidates: statutory surveillance/
  foreign-agent/online-speech laws (L5).

## Coverage worklist (grows each /loop iteration)

- broaden jurisdiction coverage — Global South / small states under-represented
- (auto-generated; see `analyze/coverage`): add balancers for any stock lacking one
