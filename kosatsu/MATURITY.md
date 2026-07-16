# 高札 (kosatsu) — maturity & test breakdown

**Status**: R0 (design + offline analyzer + dry-run posts). **ADR**: 2606072000.

## Test suites (79 tests, `./run_tests.sh`)

| suite | tests | what it locks |
|---|---|---|
| `methods/test_weave.py` | 23 | validation gates (G1 no-self, G2 asserter+no-verdict, G3 ≥2 primary sources / no commercial terminal, G4 delist needs lifted-at / no final state, G5/G7 no score / no PII) + the divergence engine (status_as_of event log, contested-on-active-delist, coverage-split-not-contested, unanimous, single-asserter, agreement_index, delisting_timeline, by_authority, co_designation, report shape) |
| `methods/test_charter_invariants.py` | 32 | the THREE homes agree: ontology `:db/allowed`/closed-vocab + lexicon `:const`/`:enum` + seed values; measure/authority/subject-kind drift-lock both directions; no per-subject score attr; no self-designation attr; post dry-run/mirror/no-server-key |
| `methods/test_lexicons.py` | 3 | all 6 lexicons present, well-formed, required ⊆ properties |
| `methods/test_analyze.py` | 9 | analyze renders + writes the report; social posts are dry-run mirror (G2/G7/G8/G9); ingest `--live` refused + offline normalize validates + rejects a verdict measure; bridge join keys advisory + tsumugi 縁 edges |
| `methods/test_consistency.py` | 5 | seed has no dangling refs; seed kinds ⊆ ontology; every designation resolves; every subject has a designation (G5: subjects exist only as targets) |
| `cells/test_state_machines.py` | 7 | every cell `.solve()` raises (G8); publication membrane drafts when clean and refuses under-sourced / published-status / server-key |

## Roadmap gates

- **R0** (this) — ADR-2606072000 PROPOSED. Design + offline + dry-run.
- **R1** — Council Lv6+ ≥3 per cell: `designation_ingest` builds kotoba EAVT datoms over offline
  PRIMARY-source list batches; migrates the legacy RisingWave sanctions/intel scaffolds.
- **R2** — Council Lv6+ ≥4 + 30-day public objection: `competing_claim_weave` over the live Datom
  log; first reviewed dry-run posts; tadori/keizu/tsumugi SoS bridge live.
- **R3** — Council Lv7+ + operator: `social_post` live publication under 1 SBT = 1 vote + member
  signature; live primary-source ingest.

## Honest gaps (R0)

- Seed is `:representative` with **synthetic** subject ids/labels — mirrors no real person/org.
- No live list ingest, no live posting, no kotoba-EAVT write (all G8-gated).
- `bridge.py` emits advisory join keys only — it does not read another actor's graph.
