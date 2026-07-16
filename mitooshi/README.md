# mitooshi 見通し — Probabilistic Forecasting Observatory

> The charter-clean answer to *「kotoba で quant market や Google Trends の model での未来予測 actor は設計しているか? 実際の予測と、事実からモデル誤差・weight を修正・学習する architecture は?」*
>
> **Status:** 🟡 R0 · **ADR:** [2606051800](../../90-docs/adr/2606051800-mitooshi-probabilistic-forecasting-observatory-r0.md) · **DID:** `did:web:etzhayyim.com:actor:mitooshi`

## What it is (and is NOT)

A naive *"quant-market prediction"* actor is a **trading bot** — and a trading bot is
profit speculation, which the Charter prohibits (§1.3 non-profit-only; yobel explicitly
bars *"predictive market / derivative speculation"*). So mitooshi is the **inverse**, the
same way okaimono inverts Amazon and yadori inverts GoDaddy:

- it emits **probability distributions** over **public** time-series (chokepoint
  transit-load, congestion, availability, flow-rate, price-index, search-interest),
- routed to **resilience / planning / early-warning** (danjo / kanae / watari / watatsuna
  siblings),
- and it **never places a trade, holds a position, or derives P&L**. It does not
  adjudicate or advise (kanjo boundary — not 投資助言業).

見通し = *visibility into a distribution of possible futures*, never a prophecy of one.

## The architecture you asked about: 事実 → 誤差 → weight 修正 → 学習

The "correct the model from fact, online" loop is **structural**, not bolted on:

```
   kotoba Datom log  (as-of = ground truth SSoT; append-only)
        │
   (1) forecast_issue ──▶ a probability distribution datom, stamped :info-as-of
        │                  (G1 distribution-only · G2 non-speculative use)
        │   …time passes…
   (2) series_ingest ──▶ the realizing observation arrives, append-only :observed-at
        │                  (G4 primary-public source only)
        │
   (3) backtest_score ─▶ join forecast × obs ACROSS as-of  →  proper-scoring residual
        │                  CRPS / pinball / Brier / log-score + PIT + skill-vs-baseline
        │                  G5: obs MUST be strictly AFTER info-as-of, else score.py RAISES
        │                  → look-ahead leak is STRUCTURALLY impossible on an append-only log
        │
   (4) online_update ──▶ residuals drive EWMA bias-correction + variance-inflation
        │                  → a proposed new model version  (G8 runtime = baien federated edge)
        │
   (5) calibration_gate ▶ promote the new version ONLY if: beats baseline (skill>0, G12)
                          AND calibrated (PIT deviation in bound, G7)
                          AND member/operator-signed (G9 no-server-key)
```

**Why the append-only Datom log matters:** because the forecast records *what the model
was allowed to see* (`:info-as-of`) and the observation records *when the fact arrived*
(`:observed-at`), a backtest can only ever score against facts that came **after** the
forecast. Look-ahead leakage — the thing that makes most backtests lie — is impossible by
construction. This is the one property a forecasting system most needs and the one
append-only EAVT gives for free.

**The loop is proven to close** (`test_learning_loop.py`): a deliberately biased +
mis-dispersed model is handed nothing but its own residuals, and the cycle drives

```
   CRPS 1.503 → 0.403   (model error −73%)
   PIT mean 0.95 → 0.50 (overconfident/biased → calibrated)
   learned: bias +2.02, variance ×0.78
   → calibration_gate: promote CLEARED (member-signed) / REFUSED (unsigned, no-server-key)
```

i.e. the model corrects itself from fact, the error measurably drops, calibration is
restored, and promotion is gated on skill + calibration + a member signature.

And it stays stable **continually** (`test_continual_learning.py`): run round after round,
the EWMA correction converges to the true bias, rejects per-round noise, and **re-converges
when the world drifts** — a regime shift of the true bias `2.0 → 4.0` is tracked
(`c → 1.996` then `c → 3.996`), so the model keeps following reality instead of getting
stuck. That is the drift-monitoring half of continual learning.

## Run it

```bash
./run_tests.sh                     # the whole suite (89 tests) with one command
cd methods
python3 analyze.py                 # leak-free backtest → out/scorecard.md + out/reliability.md (PIT diagram)
python3 horizon.py                 # multi-horizon skill-decay table (AR(1)) → out/horizon-skill.md
python3 ingest.py --batch ../data/ingest/sample-batch.json   # offline normalizer (refuses --live)
python3 test_score.py              # 26 proper-scoring-rule tests (gaussian/quantile/categorical/ensemble)
python3 test_analyze.py            # 9 backtest + reliability tests
python3 test_ingest.py             # 7 ingest tests (G4 source membrane + G10 --live gate)
python3 test_bridge.py             # 6 cross-actor bridge tests (watari/watatsuna chokepoint join)
cd ../cells && python3 test_state_machines.py   # 24 cell tests (all 5 cells coded)
cd .. && python3 test_learning_loop.py          # 6 end-to-end learning-loop tests
cd .. && python3 test_continual_learning.py     # 5 multi-round convergence + drift-tracking tests
```

`analyze.py` also emits `out/reliability.md` — a per-model **PIT reliability diagram** (the
G7 calibration-honesty artifact: a calibrated forecaster's PIT histogram matches the uniform
ideal; over/under-confidence shows as bars away from it) + `out/reliability.kotoba.edn`
(`:fc.calib/*` datoms, kami-engine-viz-ready).

### Cross-actor: forecast what watari/watatsuna observe

`bridge.py` is the composition the maritime-resilience picture is built on. watari (live
moving-craft) and watatsuna (submarine cables) both emit chokepoint-keyed aggregates over
the **same keyword space** (`:malacca`, `:luzon-strait`, `:suez-red-sea`, `:hormuz`…). The
bridge turns those into mitooshi `:series` + `:obs`, so mitooshi **forecasts the very
chokepoints they observe** — observe (watari/watatsuna) → forecast (mitooshi):

```bash
python3 bridge.py --watari ../../watari/out/movement-situation.kotoba.edn \
                  --watatsuna ../../watatsuna/out/cable-criticality.kotoba.edn --at 1 --out out
# → 9 chokepoint series; :malacca becomes BOTH s-malacca-transit (3 vessels, watari)
#   AND s-malacca-cable (940 Tbps, watatsuna); 54 non-chokepoint records ignored
```

Verified on the **real** watari/watatsuna analyzer outputs (not just synthetic samples).
Each run is one snapshot at `--at <ts>`; successive runs build the append-only as-of trail
(非終末論). The sibling outputs are `:derived`, so the bridge ingests them as `:representative`
public observations tagged with `:obs/source-actor`, never as authoritative fact (G11).

### How far ahead can it see? (multi-horizon skill decay)

`horizon.py` is the honest answer to "how far is the forecast useful?". On a mean-reverting
AR(1) process it scores a leak-free h-step forecaster against climatology at each lead time:

| horizon h | mean CRPS | skill vs climatology |
|---|---|---|
| 1 | 0.566 | **+0.30** (clearly beats climatology) |
| 3 | 0.795 | +0.006 (≈ climatology) |
| 6 | 0.873 | −0.10 (no better than the mean) |
| 12 | 0.839 | −0.07 |

Skill decays as the lead time grows — a long-range forecast eventually does no better than
the climatological mean. mitooshi never claims flat-skill foresight; the **useful-foresight
range is where skill > 0** (非終末論).

Seed result (`:representative`, all four distribution kinds wired end-to-end):

| model | dist | metric | score | skill vs clim | skilled |
|---|---|---|---|---|---|
| EWMA + drift | gaussian | CRPS | 0.089 | +0.81 (and +0.31 vs persistence) | ✅ |
| Quantile regressor | quantile | pinball | 0.024 | +0.71 | ✅ |
| Direction classifier | categorical | Brier | 0.338 | +0.44 | ✅ |
| Ensemble | ensemble | CRPS (energy) | 0.027 | +0.78 | ✅ |

Each beats its baseline → earns promotion; an unskilled model would be honestly `❌` and
refused by `calibration_gate` (G12).

## Files

| Path | What |
|---|---|
| `00-contracts/schemas/forecasting-ontology.kotoba.edn` | vocab (`:series :obs :forecast :fc.score :fc.model :fc.calib :fc.update`) + the 4 structural invariants |
| `methods/score.py` | the empirical heart — CRPS (Gaussian closed form) / pinball / Brier / log-score / PIT / skill, leak-checked |
| `methods/analyze.py` | leak-free backtest → scorecard + derived score datoms (all 4 dist kinds) |
| `methods/ingest.py` | offline public-series normalizer; G4 source membrane + G10 `--live` gate |
| `methods/bridge.py` | cross-actor: watari/watatsuna chokepoint aggregates → forecastable `:series`/`:obs` |
| `methods/horizon.py` | multi-horizon skill-decay analysis (AR(1)) — the useful-foresight range |
| `run_tests.sh` | one-command runner for the whole 89-test suite |
| `test_learning_loop.py` · `test_continual_learning.py` | one-shot + multi-round learning-loop proofs |
| `cells/series_ingest` | G4 primary-public source membrane (refuses proprietary terminals) |
| `cells/forecast_issue` | G1/G2 invariant gate (refuses point-assertion + speculative use) |
| `cells/backtest_score` | wraps score.py over the Datom log |
| `cells/online_update` | residual → bias + variance recalibration (baien-edge) |
| `cells/calibration_gate` | skill + calibration + no-server-key promotion refusal gate |
| `lex/*.edn` | 6 lexicons, invariants as `const` / `enum` |
| `data/seed-forecast-graph.kotoba.edn` | bounded `:representative` seed |

## Honest R0 boundaries

Design + datafication + **offline** scoring/recalibration only. No live ingest, no live
publish, no live model promotion, no live federated backward pass (all G10 — Council Lv6+
+ operator). Seed is `:representative` (illustrative values, not live capture). The
recalibrator is the design-only reference; the real training substrate is baien federated
edge (ADR-2605242600/2605242630), Murakumo-only. Google Trends, if ever, only via karakuri
member-principal ToS-honest path — never scraped. All four distribution kinds
(gaussian/quantile/categorical/ensemble) are scored in `score.py` AND driven end-to-end by
`analyze.py` over the seed. `ingest.py` is an offline normalizer only — `--live` fetch is
refused without `MITOOSHI_OPERATOR_GATE=1` (G10), and even when attested it exits design-only
(no network code).
