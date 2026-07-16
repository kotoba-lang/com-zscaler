# wakai 和会 — Maturity

**Stage: R0** (scaffold) — ADR-2605263500. Member-to-member SOLIDARITY POOL, **NOT
insurance** (no premium-as-contract / actuarial pricing / claim adjudication / policy
denial / underwriting / investment-return / commercial (re)insurance / DeFi speculation).

| Dimension | State |
|---|---|
| Lexicons | ✅ 5 under `com.etzhayyim.wakai.*` (contribution / distribution / poolStateReport / publicFundBackstopRequest / silenWakaiReview) — const fields fully populated (README's "R0 skeleton" note is now outdated) |
| Manifest | ✅ `manifest.jsonld` |
| Tests | ✅ `methods/test_charter_gates.cljc` + `methods/test_pool.cljc` — **15 tests / 42 assertions, green** (`bb test:wakai` / `./run_tests.sh`) — pins the anti-insurance / anti-speculation const ledger + the pool engine's own invariants |
| Cells | ⛔ none yet (R1 — wiring `methods/pool.cljc` into a live kotoba-kotodama Pregel cell is separate from the reference-impl existing) |
| Methods | ✅ `methods/pool.cljc` — R0 reference implementation (pure functions matching the Lexicon record shapes 1:1): `validate-contribution` (G6+G8), `validate-distribution` (G3+G7+G9, rejects <3 community or <3 Council attestations), `aggregate-pool-state` (G6 pinned; no individual amounts). `solve()` raises — validation + aggregation only, NOT a live pool |

## Charter gates pinned by the test

- **G3 NOT insurance** — `mutualAidDistributionAttestation.claimAdjudicated` const false;
  `silenWakaiReview.claimDenialEventsCount` const 0.
- **G4/G5 no commercial (re)insurance** — `silenWakaiReview.commercialInsuranceSoftwarePenetrationPct`
  + `commercialReInsurancePenetrationPct` const 0.
- **G6 no investment-return / no speculation** — `contribution.investmentReturnPromised`
  const false; `poolStateReport.poolAssetClass` const "usdc-stable-only";
  `defiYieldFarmingActiveCount` + `tokenSpeculationActiveCount` const 0.
- **G7 no pre-existing-condition exclusion / no underwriting** —
  `distribution.noPreExistingConditionExclusion` const true;
  `silenWakaiReview.preExistingConditionExclusionEventsCount` const 0.
- **G9 community discernment** — distribution requires `communityDiscernmentAttestations`
  + `councilAttestations` (Council Lv6+ ≥3), not claim adjudication.
- **G11 administrator vocation-flow** —
  `silenWakaiReview.administratorVocationFlowCompliantRatioPctIntegerHundredths` const 10000 (=100.00%).

## R0 → R1 gate

Council Lv6+ ≥3 baseline (silenWakaiReview, witness ≥3) + the 4 pool cells + Public-Fund
backstop wired (Council Lv6+ ≥4/7 + toritate ledger cross-link).
