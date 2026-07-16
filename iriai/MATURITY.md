# iriai 入会 — MATURITY

**ADR-2606272200 + 2606280900** · clj-native R0 · `did:web:etzhayyim.com:iriai`

## R0 checklist (17/17)

- [x] manifest.edn — actor id/glyph/tier/purpose/gates G1–G9/non-goals N1–N5/composes
- [x] ontology — `kotoba/ontology.iriai.edn` (entities/lifelines/verdicts/instruments/degradation-models/attributes + NEGATIVE SPACE)
- [x] synthetic seed — `kotoba/seed.edn` (6 regions × 4 lifelines = 24 cells + 11 deployed assets, all verdicts)
- [x] infra layer — `methods/infra.cljc` (commons-gap + resilience → verdict → assess → datoms → report)
- [x] 資金 funding layer — `methods/fund.cljc` (§1.16 in-kind proposal, cash≡0, give-only, imputed value)
- [x] 管理 management layer — `methods/manage.cljc` (1 SBT=1 vote + :intent + no-server-key)
- [x] 物理 twin layer — `methods/twin.cljc` (real degradation physics → condition/RUL/safety; project run-ahead)
- [x] 運用 maintain layer — `methods/maintain.cljc` (lifecycle gate, safety-floor first, OpEx, executor routing)
- [x] gates — `methods/gates.cljc` (ex-info assertions incl. G9 safety-floor + structural `forbidden-absent?`)
- [x] persistence — `methods/kotoba.cljc` (content-addressed append-only commit-DAG, verify-chain)
- [x] heartbeat — `methods/autorun.cljc` (5 layers; deterministic, idempotent-by-content, resume-safe)
- [x] seed loader — `methods/iriai_edn.cljc`
- [x] tests — 13 suites, **79 tests / 512 assertions green** (bb)
- [x] runner — `run_tests.clj` (bb-native, no shell, ADR-2606072802)
- [x] docs — README.md + CLAUDE.md + this MATURITY.md
- [x] ADRs — `2606272200` (commons) + `2606280900` (twin + maintenance + road)
- [x] gitignore — `data/persisted/` (generated ledger never committed)

## Coverage-verdict distribution (synthetic seed)

| verdict | count | example |
|---|---|---|
| :provision | 4 | kibou (off-grid rural, all four lifelines) |
| :redundancy | 2 | shima (single-source island power + telecom) |
| :reinforce | 4 | saigai (disaster-degraded) |
| :maintain | 9 | midori / machi / shima water+gas |
| :await-consent | 4 | yama (high need, no consent) |
| :monitor | 1 | machi gas (below adequate, low burden) |

Funding plan: **10 proposals** · imputed §1.16 income value aggregate · **cash to consumer $0**.
Governance: **10 decisions**, all `:intent`-only, all keyless, 2 escalated to Council Lv7+.

## Maintenance-verdict distribution (11 deployed assets, ADR-2606280900)

| verdict | count | example (asset · physics) |
|---|---|---|
| :ok | 2 | Midori T1 (transformer, θh=56°C) · Midori Rd R1 (PCI=98) |
| :inspect | 1 | Machi Fibre F1 (margin=21.1 dB, interval due) |
| :preventive-service | 1 | Kibou Main W1 (C=118/130, service due) |
| :refurbish | 1 | Machi T2 (mid-life) |
| :corrective-repair | 3 | Saigai Gas G1 (condition) · **Shima Gas G2 (SAFETY-FLOOR, leak-p=0.91)** · **Kibou Bridge B1 (SAFETY-FLOOR, load-rating=0.9)** |
| :renew | 2 | Shima Main W3 (C=68) · Saigai Rd R2 (RUL<3) |
| :decommission | 1 | Old Substation T9 (loss-of-life≥1, θh=120°C, FAA=2.71) |

Maintenance plan: **2 safety-floor actions** (never deferred, G9) · all `:intent` · annual upkeep
OpEx imputed onto §1.16 rails (**cash to consumer $0**). Twin: mean condition 0.62, 3 unsafe.

## R0 → R1 → R2

- **R1 (G7-gated)**: real region/utility-coverage ingest from public open data (World Bank / IEA /
  WHO-JMP / ITU — read-only, no key); inochi/jinushi land-sovereignty grounding for consent; amime
  N-1 energy-mesh join for the electric layer.
- **R2**: fleet registration (cell-runner cells.edn + healthz, the kaname/kafun track);
  Murakumo-narrated commons digest; live kotoba-engine bridge (ibuki-R3); lexicon JSON.

Live production + actuation stays the producer actors' (hikari/mizuho/kamado/noroshi) under
Council Lv7+, never iriai.
