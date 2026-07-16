# ake (朱) — maturity ledger

**Actor**: 朱 (ake) — community-edit membrane (Wikipedia-stance) · **ADR**: 2606052100 · **DID**:
`did:web:etzhayyim.com:actor:ake`

| Axis | R0 (this) | R1 (gated) | R2 (gated) |
|---|---|---|---|
| **propose intake** | offline screen+record over seed | live `:edit/*` intake over real KG seed | standing service across all KG actors |
| **triage** | deterministic risk+quality + pure-function route | Murakumo-only LLM refines scores (route stays pure) | calibrated scoring vs accepted-edit history |
| **review** | optimistic / vote-sim / council-pending + challenge→revert rollback primitive | real 1 SBT = 1 vote + 48h timelock | edit-war resolution wired to danjo-observed + Council |
| **promote** | member-signed dry-run, `published=false` | member-signed live promotion | `:representative→:authoritative` coverage pipeline |
| **history** | append-only revision engine (as-of/current) | "view history" tab on /actor + /search | contributor-trajectory dashboards |

## R0 evidence

- **Canonical Clojure path EXECUTED green** (`./run_tests_cljc.sh`, wired into `./run_tests.sh`):
  the `.cljc` methods are the canonical port and the `.py` files mirror them — previously only the
  Python mirror was run and `test_consistency` merely *asserted* the two matched. The 10 `.cljc`
  suites now run under **babashka** (`bb --classpath 20-actors`, repo-root cwd for the
  repo-relative seed paths): **110 tests / 447 assertions, 0 fail 0 error**. The runner skips
  gracefully (exit 0) where `bb` is absent, so the Python suite still gates a bb-less checkout.
  This closes the "canonical but never run" gap — a future edit that breaks `triage.cljc` while
  keeping `triage.py` green now fails loudly instead of shipping a red canonical path.
- **94% branch coverage of `methods/` (measured)** — every reachable, non-`__main__` branch of the
  membrane engines is exercised: `contributor.py`/`revision.py` 100%, `_edn.py` 99%, `analyze.py`
  93% (remainder = `__main__` + an unreachable no-route fallthrough), `triage.py` 91% (remainder =
  `__main__`). The triage ORES-score breakdown (every additive signal), the intake-refusal +
  revision-dedup paths in `analyze.py`, and the EDN reader's atom-level literals (true/false/nil,
  int, float, keyword-as-string, bareword, escaped string) each carry a dedicated, py+cljc-mirrored
  test so a silent re-weighting or a mis-read literal fails loudly. The five **cell** state machines
  are at full meaningful coverage too (`review_vote`/`propose` 100%, others ≥96% — the only misses
  are the import-time `sys.path` shim branches): the no-server-key tally refusal, the unknown-
  mechanism closed-vocab refusal, and the G5 grow-by-one append guard each carry a test.
- **137 tests green, HERMETIC** (`./run_tests.sh`): 6 EDN-reader + 29 triage (incl. a route_for
  totality + priority sweep over the whole risk×quality×rider domain — the G2 structural guarantee
  — and the full assess_quality score breakdown) + 7 revision + 6 edit-war + 13 contributor (incl.
  per-DID isolation + a no-ranking/no-score-of-soul API lock — the G9 structural guarantee) + 8
  ingest + 16 charter-invariants + 8 analyze (incl. the intake-refusal + revision-dedup paths) + 5
  lexicons + 10 consistency/SSoT-drift-lock (methods) + 22 cell state-machine (incl. the
  unknown-mechanism + G5 grow-by-one cell-level invariant guards) + 7 cell-chain membrane-flow
  integration. The suite is green in any checkout: the ingest genesis bridge is asserted against a
  committed fixture (`data/sample-profile-seed.kotoba.edn`); the real shared SSoT is validated by a
  soft check that no longer hard-couples ake's suite to the (coordination-committed) shared seed.
- **Cell composition proven** (`cells/test_membrane_flow.py`): one edit threaded through all five
  cells in sequence (propose→edit_triage→review_vote→promote→revision_log) — the runtime Pregel path
  — for every route (auto-accept / vote-accept / vote-reject / council-pending / rider-refused /
  server-sig-refused), confirming each cell's output is valid input to the next.
- **Edit-war resolution landed** (`revision.revert` + `test_editwar.py`): a `:challenge` routes
  high→vote; an upheld challenge rolls back to the predecessor by appending (Wikipedia revert) — the
  bad edit is undone for the current reader yet preserved in the auditable history (danjo-observable).
- **Membrane proven over REAL repo data** (`methods/ingest.py`): bootstraps the append-only revision
  history from the actual committed `actor-profile-seed.kotoba.edn`; surfaced + closed an
  `INFRA_ACTORS`↔profile-seed drift (added mitooshi + noroshi profile records, 19→21).
- **End-to-end membrane** (`methods/analyze.py`): 5 seed edits route to all 5 outcomes
  (auto-accept / vote-accepted×2 / council-pending / rider-refused); accepted edits land in the
  append-only revision history, refused/pending do not.
- **Structural invariants** enforced in 3 places each and tested structurally (not by prose-grep):
  G1 member-only + no-server-key, G2 no `:triage/decision` + pure-function route, G3 mirror
  target-kinds, G4 provenance-required, G5 append-only ops, G7 Council-Lv7 invariant-lock, G8
  `published=false`.
- **Registered** in `INFRA_ACTORS` → `did:web:etzhayyim.com:actor:ake` (resolvable + searchable);
  actor-profile seed added.

## Honest gaps (R0)

- No live ingest / binding vote / promotion / publish — all G8 (Council Lv6+ + operator).
- Triage scoring is deterministic; the Murakumo-only LLM refinement is R1.
- The `:representative` seed is 5 illustrative edits, not the live KG.
- The contributor-trajectory (G9) is now real, tested code (`methods/contributor.py` — rate limit +
  recoverable trajectory); it is not yet wired into a live request-path rate-limiter (R1).
- No UI — the "view history" tab on /actor + /search is R1.

## Zero invariant amendments

ake **strengthens** four existing invariants and amends none: no-server-key (ADR-2605231525),
kotoba-canonical-state (ADR-2605312345), 1 SBT = 1 vote, and the mirror invariant (ADR-2606042330).
