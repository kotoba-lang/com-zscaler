# 潮目 (shionome) — Maturity

**ADR**: 2606072200 · **Status**: 🟡 R0 (design + offline analyzer + dry-run + autonomous-over-local-log) · **Updated**: 2026-06-07

## Stage ladder

| Stage | Scope | Gate | State |
|---|---|---|---|
| **R0** | ontology + 4 lexicons + `:representative` seed + analyzer (weave/concentration/social/ingest/export) + kotoba Datom-log writer + **autonomous heartbeat loop** (offline, dry-run, content-addressed DAG) + 5 cell scaffolds (`.solve()` raise) + tests | ADR-2606072200 (PROPOSED) | ✅ landed |
| R1 | ingest + flow_graph + rotation_weave build kotoba EAVT datoms over **offline** public-source batches; no live posting | Council Lv6+ ≥3 per cell | ⏳ (source registry ready — `registry/sources.seed.json`, 12 sources `unverified-seed`) |
| R2 | +regime_observer on live read-path; first dry-run networkPosts reviewed | Council Lv6+ ≥4 + 30-day public comment | ⏳ |
| R3 | +social_post live publication under 1 SBT = 1 vote + member signature; live public-market-data ingest | Council Lv7+ + operator | ⏳ |

## R0 evidence

- **Tests**: `./run_tests.sh` green — **182 tests** across weave (47) / grounding (24) / ingest (14) /
  social (11) / export (6) / sources (8) / registry (7) / charter-invariants (18) / analyze (4) /
  lexicons (5) / consistency (7) / kotoba (9) / autorun (6) / cells-state-machines (13) /
  cells-membrane-flow (3).
- **Stock layer (the money-and-markets pyramid)**: alongside the FLOW graph, a bucket may carry an
  `:outstanding-usd` snapshot — the observed total SIZE of an asset class in USD trillions.
  `weave.stock_pyramid` aggregates the latest such snapshot per asset class into the "how big is
  everything" sizing view (the Visual Capitalist money & markets pyramid). On the `:representative`
  seed the 8 global layers total **1,383 tn** (derivatives gross notional 600 / real-estate 380 /
  debt 140 / broad-money 121 / equities 115 / gold 16 / cash 8 / crypto 3). A SIZE is a factual
  observed quantity carrying `no_trade_notice=true` — never a per-asset rating/signal/target
  (G2/G4 untouched); stock (usd-tn) is never summed with flow magnitudes (usd-bn).
- **Entity grounding (`methods/grounding.py`)** — answers *who is inside each layer?* by
  decomposing a pyramid layer into the NAMED real entities sibling actors already mirror, and
  reporting the coverage gap HONESTLY. On the checked-out seeds: the **equities** layer is grounded
  by kabuto's **1,719** listed companies — value coverage **$46.8tn / $115tn ≈ 40.7%** (a stated
  LOWER BOUND: only the 205 companies that report a market-cap contribute), count coverage
  **1,719 / ~55,000 ≈ 3.1%** of the listed universe (a `:representative` denominator). A
  cross-cutting **systemic-institutions overlay** (hokorobi, 17 institutions, 14 authoritative) and
  the **7 ungrounded layers** (cash/broad-money/debt/real-estate/gold/crypto/derivatives) are named
  explicitly. **Disclosure DEPTH** (kanjō): of the named equity constituents, **5** carry
  primary-disclosure financials (Apple/Sony/Nintendo/Toyota/Microsoft) — an authoritative-depth
  signal, not a fundamental rating. A **per-layer grounding roadmap** (`grounding_roadmap`) records,
  for every layer, the candidate sibling actor and — where it is `ungroundable-at-r0` — the explicit
  reason (e.g. derivatives = BIS gross-notional aggregate, not entity-decomposable; debt = bond-market
  size ≠ corporate balance-sheet liabilities, so kanjō must NOT be conflated with the debt layer).
  Fail-open (a missing sibling ledger → that layer reported ungrounded, never a crash); shionome's
  core (weave/concentration) does NOT import the bridge. Sizes/counts only, no advice.
- **The no-trade invariant (トレードはしない, G2) is enforced in four homes**: the ontology
  closed-vocab (trade tokens are not enum members + no `:bucket/rating` attr), the lexicons
  (`noTradeNotice` const true on flows/findings/posts), `weave.TRADE_TOKENS` (refused on every
  flow/bucket kind), and `social._guard_no_trade` (scanned out of every post body). The structural
  test parses all three data homes and asserts they agree.
- **Autonomous operation, empirically run**: `python3 autorun.py --cycles 3` runs the full
  observe→validate→weave→analyze→dry-run-post→persist cycle by itself and appends 3
  content-addressed transactions (273 EAVT datoms each) to the append-only kotoba Datom log; the
  commit-DAG verifies (`verify_chain` OK), and a tampered byte breaks it (`test_kotoba`). Same
  input → same head CID (deterministic / resume-safe).
- **Cross-asset regime** is FACTUAL and descriptive: on the seed it reads **risk-on**
  (risk-asset net +27.3bn / safe-asset net −26.6bn). It carries `no_trade_notice=true` and the
  social narration explicitly states 「記述であり助言ではありません」.
- **Net-flow math is unit-clean**: only capital-movement kinds (rotation / fund-inflow /
  fund-outflow / fx-flow) contribute to net flow / HHI / rotation; observation-only kinds
  (cross-correlation / price-move / volume-shift / yield-shift, in zscore/pct/bps) are excluded so
  the money totals are not corrupted by mixed units.
- **Empty-graph path covered**: `analyze.run` over an empty seed exercises the `"(none in seed)"`
  fallbacks and the empty posts/kanae-flows — the degenerate branches the populated seed never
  reaches.
- **kanae export honesty** (G11/G2): only the 8 capital-movement flows export as kanae fund flows;
  the 3 observation-only flows are skipped and counted (no silent drop).
- **Deny-list across all paths**: the commercial-market-data `SOURCE_DENY` is enforced in
  `validate_flow`/`validate_snapshot` AND on the outbound post path (`social._guard_sources`) — a
  flow or a post citing Bloomberg/Refinitiv/FactSet/CapIQ/四季報 is refused (Rider §2(e)/N5).
- **Registry-driven sourcing** (G11): an ingest record naming a registry `sourceId` gets the
  registry's verification status (a caller cannot forge `:authoritative` for an unverified source).
- **R1-readiness — public-source registry** (`registry/sources.seed.json` + `VERIFICATION.md`): 12
  global primary sources (FRED / US Treasury / ICI / SEC EDGAR / BOJ flow-of-funds / JPX / ECB /
  IMF-IIF / EIA / LBMA / on-chain explorers / US Census), each with a `mapsTo` shionome datom type,
  all `unverified-seed` (G8 — no live ingest until Council Lv6+ + operator verifies).

## kotoba-WASM + Murakumo-fleet cron (deploy-readiness)

Two concrete runtime artifacts make "kotoba wasm として自律稼働 + fleet cron" real, both
**empirically built/verified off-fleet**; the only remaining step to *live* operation is a human
operator gate-flip (G7/G8 — Council Lv6+ + operator + member signature):

1. **Standalone kotoba-WASM component** (`wasm/`) — `app.py` + `wit/world.wit` built with
   **componentize-py** into `shionome-actor.wasm` (18.5 MB, WASI Preview 2), `wasm-tools
   validate` clean, **jco-transpiled and executed under node** (`node verify.mjs` → regime=risk-on,
   `no_trade=true`). CID `bafybeigk6whellozcybop4btzcrdtybd5yejjrax7tczxhapfsyya64hka`
   (dag-pb, T2 donated-mesh). The `.wasm` is gitignored (reproducible from source via `build.sh`).

2. **5 Murakumo-fleet cron cells** (`20-actors/magatama/cells/shionome_*`) — `kotoba_langgraph`
   Pregel cells ("Resident in Kotoba WASM", the ossekai pattern), each shipping a
   `cells.toml.fragment` with a `trigger = { kind = "cron", … }` on a real fleet node:

   | cell | node | cron | port |
   |---|---|---|---|
   | shionome_ingest | issachar | `5 * * * *` | 14010 |
   | shionome_flow_graph | issachar | `10 * * * *` | 14011 |
   | shionome_rotation_weave | dan | `15 * * * *` | 14012 |
   | shionome_regime_observer | dan | `20 * * * *` | 14013 |
   | shionome_social_post | naphtali | `0 9 * * *` | 14014 |

   Registered in `50-infra/murakumo/fleet.toml` (node↔cell placement) + discovered by
   `cell_runner_main.py` (`shionome_*` fragment glob). Pure cell logic lives in
   `shionome_core.py` (no `kotoba_langgraph` dep) and is tested off-fleet: **14/14** in
   `test_shionome_cells.py`. The cells run as **k3s DaemonSet Pods** via the Ansible playbook
   `60-apps/etzhayyim-project-murakumo/ansible/k8s-gpu-cluster.yml` — the actual `ansible … deploy`
   onto the physical Mac-mini fleet is the **operator** step (an agent cannot execute it; cron
   cells fire the analyze→dry-run cycle, while live market-data ingest + live external posting stay
   G8-gated).

The append-only Python autonomous loop (`methods/autorun.py`) remains the off-fleet self-driving
demonstrator over a local kotoba Datom-log file; the fleet cron cells are its production form.
