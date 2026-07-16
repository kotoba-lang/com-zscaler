# 澪 mio — CLAUDE (actor-local rules)

Proof-of-Useful-Flow verification ledger — backbone of the **Energy Order Protocol**
suite. OBSERVATION + VERIFICATION ONLY. ADR-2606211200.

## Invariants (do not weaken)

- **G1 order-not-consumption (BACKBONE).** Reward derives ONLY from VERIFIED ORDERED
  flow, never from CONSUMED energy. There is no `:consumed-reward` attribute and there
  must never be one; `useful-flow-score` is 0 unless `:verified`; only `:verified` routes
  to `:reward`. This is the whole point (PoW → PoUF). The test
  `g1-useful-flow-zero-unless-verified` + `g1-g3-no-trade-no-signal-no-consumed-reward`
  guard it — keep them green.
- **G2 verification-is-the-gate.** Never let a claim reach `:verified` without all five
  §9 facts (baseline / additionality≥min / trusted measurement / unique double-count-key
  / leakage≤max). `:self-report` weight (0.3) must stay below `verified-threshold × 1 / 1`
  so self-report ALONE cannot verify.
- **G3 map-not-market-signal.** No `:trade` / `:signal` / `price-forecast-point`. mio
  accounts realized verified flow; mitooshi owns forecasts; shionome owns capital flows.
- **G5 content-free-intention.** 委 yudane claims carry only an aggregate flex offer.
  Never add a `:mio.person/*` attribute or ingest per-person intent text.
- **G7 no-server-key.** mio holds no key and does no network I/O. Reward outputs are
  advisory/drafted-unsent; issuance = 1 SBT=1 vote + TitheRouter; actuation = hikari +
  Council gate. Live measurement ingest is an operator step (G7).

## Conventions

- clj/bb over the kotoba Datom log (repo-wide rule). No `.py`/`.sh` logic; the ledger is
  an append-only content-addressed commit-DAG (`methods/kotoba.cljc`), resume-safe,
  deterministic (caller supplies tx-id + as-of — no wall clock, no `Math/random`).
- Seed provenance is mixed: rows default to `:representative`; `:authoritative` rows fold
  a cited measurement via an operator-triggered G7 ingest.
- Tests: `./20-actors/mio/run_tests.sh` (babashka). Keep all green before commit.

## Suite

mio is the shared SSoT. Siblings submit claims into mio's seed/ledger:
撓 tawami (flexibility) · 燠 okibi (waste-heat) · 樋 toi (compute-routing) ·
委 yudane (intention) · hikari (renewable-absorb / peak-shave, the actuating body).
