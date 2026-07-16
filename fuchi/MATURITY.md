# fuchi (扶持) — maturity ledger

**Actor**: 扶持 (fuchi) — maintainer sustenance allocator (investment-fund inverse) · **ADR**:
2606052300 · **DID**: `did:web:etzhayyim.com:actor:fuchi`

| Axis | R0 + R1 a/b/c/d (offline) | R1 live-but-gated (this) | live execution (Council-gated) |
|---|---|---|---|
| **covenant** | offline screen+record over seed (G4/G5/G9) | — | live intake over real 信者 roster (MEMBERS.md) |
| **assessment** | in-kind envelope, cash≡0 (G2/G3) | — | live need assessment per maintainer |
| **allocation** | tenure-weighted in-kind, G1 allowlist, cash≡0 | — | member-signed live allocation |
| **routing (a)** | **rails wired to real producing-actor DIDs** (mitsuho/hikari/okaimono/iyashi/commons-land/Murakumo/warifu); dry-run intents | `dispatch_live()` exists, **refuses by default** (gate) | live provisioning dispatch (flag + Council Lv6+ + member sig) |
| **governance (b)** | **real 1 SBT = 1 vote + 48h timelock** (dedupe, weight≡1, no-server-key, finalize-raises-early) | `finalize_binding()` exists, **refuses by default**; timelock still strict | binding vote on-chain |
| **booking (c)** | **toritate ledgerEntry projection** (cash≡0) + **kanae :flow/* graph** | `write_live()` exists, **refuses by default** | toritate writes the live ledger + kanae renders live |
| **coupling (d)** | **Displacement-Dividend earmark** (TitheRouter 10% split, exact) + **G2 gate** (no displacement w/o funded cohort) | `commit_live()` exists, **refuses by default**; needs **Lv7** + G2 funded-cohort | live surplus→donation→earmark; binding G2 gate on the robotics wave |
| **live gate** | — | **`methods/live_gate.py`** — single authorization membrane; per-leg `FUCHI_ALLOW_LIVE_<LEG>` flag + operator attestation + Council Lv6+/Lv7+ + member sig; default refused; never overrides cash≡0/no-server-key/G3 | env flag flipped + Council ratifies |

## R0 + R1 a/b/c/d + R1 live-but-gated evidence

- **174 tests green** (`./run_tests.sh`): 15 allocate + 13 route + 8 provision + 11 vote + 10 book
  + 10 couple + **22 live_gate** + 15 analyze + **39 charter-invariants** + 3 lexicons + 9
  consistency/SSoT-drift-lock (methods) + 19 cell state-machine.
- **R1 (live-but-gated)** — `methods/live_gate.py` is the single membrane every outward leg crosses.
  `provision.dispatch_live` / `vote.finalize_binding` / `book.write_live` / `couple.commit_live`
  each call `live_gate.require()`, which **raises `LiveGateRefused` unless** the operator process
  flag (`FUCHI_ALLOW_LIVE_<LEG>=1`) + an operator attestation DID + Council **Lv6+** (Lv7+ for
  `couple`, invariant-adjacent) + a **member signature** (no-server-key) are ALL present. Default =
  refused (the deliverable). The gate is an authorization membrane, **never** an invariant override:
  cash≡0, no-server-key, in-kind-only rails, the 48h vote timelock, and the G2 funded-cohort gate
  all still hold in live mode (the `couple` leg stacks both refusals). `analyze.py` prints every leg
  refused and emits `out/live-gate-status.kotoba.edn`. Actual live execution still needs the env
  flag flipped + Council ratification.
- **R1(a)** — `analyze.py` emits 14 provisioning intents over the seed, each addressed to a real
  producing actor DID; the seth liquidity intent is `member_principal` (warifu 0%); all dry-run
  (`published=false`, cash 0, keyless).
- **R1(b)** — seth's allocation now routes through a real ballot tally (`5-1/48h✓`); the tally is
  `pending` before the window closes, `finalize()` raises early, duplicate voters and `:server`
  voters are rejected, out-of-window ballots are dropped.
- **R1(c)** — accepted in-kind rails are booked into toritate `ledgerEntry` categories
  (subsistence/vocation/care), `cashStipendUsd≡0`, liquidity excluded; a kanae-renderable flow
  graph (Public Fund → 扶持 → provider → maintainer) is emitted (`out/kanae-flow.kotoba.edn`).
- **R1(d)** — the seed's `cohort-sanae-2026` ($60k surplus, funded → tithe $6k + earmark $54k)
  covers its committed $8.5k in-kind → **admissible**; `cohort-hataori-2026` ($0 / unfunded) →
  **REFUSED** by the G2 coupling gate. The 10% TitheRouter split is exact for every input
  (`gross = tithe + earmark`); `out/cohort-earmarks.kotoba.edn` is emitted.
- **End-to-end allocator** (`methods/analyze.py`): 5 seed maintainers route to all four outcomes —
  abel→auto, seth→sbt-vote (11-2, accepted, in-kind 50% because of an external liquidity residual),
  eve→council-lv7 (new commons-land grant), cain→refused (affiliate ad-share = Charter-Rider §2 hit),
  noah→outreach (zero tenure share, minimal floor). `cash≡0` on every allocation and seed line.
- **Charter-clean inverse proven structurally** (`test_charter_invariants.py`, parses the ontology +
  lexicons + code, not prose-grep): G1 the instrument set is the sustenance set and equity/debt/
  ROI/exit are absent from `:alloc/instrument :db/allowed`; G2 cash fields are `:db/allowed [0]`;
  G3 `:rail/kind` has no `:cash-disbursement`; G5 `:maintainer/owns-payoff :db/allowed [false]`;
  G7 no `:gov/decision` attribute exists; G9 `:alloc/server-held-key :db/allowed [false]`.
- **Tenure curve reused**, not reinvented — the Displacement-Dividend `ln(1+min(tenure,40))×hazard`
  (ADR-2606032130).
- **Registered** in `INFRA_ACTORS` → `did:web:etzhayyim.com:actor:fuchi` (resolvable + searchable);
  actor-profile seed added.

## Honest gaps (R0)

- No live disbursement / provisioning / land grant / binding vote — all G10 (Council Lv6+ +
  operator; invariant-adjacent Lv7+). The R1 a/b/c engines are built + tested **offline**; flipping
  them to live is the gated R1-live phase.
- The `:representative` seed is 5 illustrative maintainers, not the live roster.
- 扶持 **cannot eliminate a maintainer's external fiat obligations** — it maximizes in-kind coverage
  and routes the irreducible residual to member-principal 0% warifu liquidity (N4). Full
  fiat-denominated income is a Charter Lv7+ matter and is out of scope.
- No UI — a maintainer-sustenance dashboard is R1+.

## Zero invariant amendments

fuchi **strengthens** five existing invariants and amends none: cash≡0 (ADR-2605301020),
no-server-key (ADR-2605231525), payoff帰属=etzhayyim, Charter-Rider §2(b) speculative-finance
prohibition (ADR-2605192200), and the non-profit / donation-only invariants (ADR-2605192100/192115).
