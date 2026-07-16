# mitooshi 見通し — CLAUDE instructions

Probabilistic forecasting observatory. ADR-2606051800. **Read the root `/CLAUDE.md`
Charter + substrate rules first.** mitooshi-specific invariants below OVERRIDE nothing in
the Charter; they make it concrete for this actor.

## The one-sentence identity

mitooshi forecasts **distributions over public series, routed to resilience** — it is the
**charter-clean inverse of a quant trading bot** (a trading bot = profit speculation =
Charter §1.3 + yobel violation). It **never trades, never advises, never prophesies a
single future.**

## The 12 gates — do NOT weaken

- **G1 distribution-only** — `:forecast/point-asserted` is `:db/allowed [false]` (schema),
  `const false` (lexicon), `ValueError` (`forecast_issue` + `score.py`). A deterministic
  single-future is **unrepresentable** (非終末論 — no final-state datom). Every forecast is
  a probability distribution.
- **G2 non-speculative** — `:forecast/use` enum is `{resilience planning nowcast
  early-warning research}`. `trade`/`speculation`/`wager`/`position` are **not enum
  members**. Never add them. mitooshi settles no money and holds no position.
- **G3 non-adjudicating** — route to planners (danjo/kanae/watari); no advice/rating/
  valuation/業績予想 (kanjo boundary).
- **G4 primary-public-source-only** — `:series/source-class` enum excludes proprietary
  terminals (Bloomberg/CapIQ/Refinitiv/四季報) and scraped Google-Trends. Trends only via
  **karakuri member-principal ToS-honest** path, recorded `:member-principal`, G10-gated.
- **G5 leak-free proper-scoring** — `score_pair` RAISES if the obs is not strictly after
  the forecast's `:info-as-of`. Proper scoring rules ONLY (CRPS/pinball/Brier/log-score);
  skill vs a documented baseline, never cherry-picked accuracy. **This is the heart — do
  not relax the leak check.**
- **G6 Murakumo-only** · **G7 calibration-honest** (miscalibrated → promotion refused) ·
  **G8 baien-edge online-update** (no commercial GPU) · **G9 no-server-key** (promotion
  member-signed; `serverHeldKey` const false) · **G10 outward-gated** (live anything =
  Council Lv6+ + operator) · **G11 sourcing-honesty** (`:representative`) · **G12
  anti-pseudoscience** (`:skilled` true ONLY when beating a baseline on a proper score).

## When editing

- The structural invariants live in **three places each** (schema `:db/allowed`/enum +
  lexicon `const`/`enum` + Python `ValueError`/refusal). If you touch one, touch all three
  or you've created a representable charter violation.
- Tests are standalone-runnable (`python3 test_*.py`) AND pytest-compatible. Keep them so —
  the repo pytest plugin env is broken (`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1`).
- `.solve()` raises `RuntimeError` at R0 on every cell — live execution is G10-gated. Do
  not "helpfully" wire a cell to a live endpoint; that needs Council Lv6+ + operator.
- New forecasters (quantile/categorical/ensemble) extend `score.py` + `forecast_issue`;
  they must still pass the leak check and beat a baseline to be `:skilled`.
- Inference (any LLM narration of a forecast) is Murakumo-only: LiteLLM `127.0.0.1:4000`.

## Siblings / boundaries

- **watari / watatsuna** — supply the public series (chokepoint transit-load, cable load).
  mitooshi forecasts; they observe. Shared chokepoint keywords.
- **danjo / kanae** — consume mitooshi forecasts for resilience/fiscal views.
- **kanjo** — the boundary: kanjo records actuals + explicitly *does not forecast*; when a
  forecast over disclosed financials is wanted, that is mitooshi, distribution-only and
  non-advisory (still not 投資助言業).
- **yobel** — the prohibition mitooshi respects: no predictive-market / derivative
  speculation.
- **baien federated (ADR-2605242600/2630)** — the real training substrate for G8 updates.
- **kizashi** — shares the G12 anti-pseudoscience stance (evidence-graded only).
