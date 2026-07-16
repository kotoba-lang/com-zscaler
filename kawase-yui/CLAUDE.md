# 20-actors/kawase-yui — CLAUDE.md

## Identity

- **Name**: kawase-yui (為替結 — remittance bond / tie of value-exchange; classical 為替 = bill of exchange / wire transfer + 結 = tie / bond / generative tie of life per Shinto 産霊)
- **DID**: `did:web:kawase-yui.etzhayyim.com`
- **ADR**: ADR-2605282200 (R0 scaffold, 2026-05-28)
- **Sibling actor**: wakai (ADR-2605263500 — mutual aid framing precedent)
- **Compute-cost layer**: mKOTO economy (ADR-2605282100)
- **Cross-actor**: chigiri (ADR-2605262700 — G14 + G11), toritate (ADR-2605262900 — accounting)
- **Parent ADRs**: ADR-2605192100 (Mission Charter §1.5 + §1.7 + §1.12), ADR-2605192200 (Charter Rider §2(b) + §2(c) + §2(e)), ADR-2605282100 (mKOTO N2 non-transferability), ADR-2605172100 (Alt C no custom token + Base L2 + ERC-4337)
- **Status**: R0 scaffold landed (9 iterations of loop 2026-05-28; iter-summary below)
- **Form**: Adherent-SBT-gated on-chain pool family + Pregel cell substrate + Python facade — NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock

## Constitutional Discipline (CRITICAL — IMMUTABLE)

kawase-yui is **adherent-to-adherent multi-stable remittance built on a
pre-funded local-pool topology** (the structural innovation Wise plc
popularized), re-framed under religious-corp constraints. Seven
discipline boundaries are structural — each enforced at a different
layer so violations are caught early:

1. **Adherent SBT↔SBT only (G3)** — Solidity-level `onlyAdherent`
   modifier on `KawaseYuiPool.deposit()` + `claim()`; reverts when
   `AdherentRegistry.tokenOf(msg.sender) == 0`. Non-SBT participation
   structurally impossible at the L6 chain layer.
2. **Mid-market Chainlink ±0.5% band (G4)** — Constitution.sol const
   `KAWASE_MAX_BAND_BPS = 50`. Solidity `deposit()` revert + Pregel
   cell `kawase_fx_oracle_watcher` halt on out-of-band. Constitutional
   — cannot be widened by governance.
3. **NO spread profit (G5)** — `silenKawaseReview.spreadProfitMkoto`
   const **0** at the Lexicon schema layer. Audit-time mirror of the
   Solidity invariant that rate is locked at `deposit()` time.
4. **NO commercial remittance MSB integration (G7)** — Wise /
   TransferWise / Western Union / MoneyGram / Remitly / WorldRemit /
   Xoom / Revolut / OFX / Currencies Direct / Ria / Paysend /
   Atlantic Money / Sendwave / Boss Revolution / PayPal-Xoom
   **PROHIBITED** per Charter Rider §2(e) + §2(c). Build-time enforced
   by `70-tools/scripts/lint/verify_no_commercial_remittance.py`
   (lefthook + `.github/workflows/kawase-yui-r0-audit.yml` CI gate)
   — 3-layer defense (lint + CI + audit-time const-0).
5. **Per-jurisdiction Council Lv7+ unanimity (G14)** — Pregel cell
   `kawase_jurisdiction_compliance` is the SOLE enforcement point.
   `KawaseYuiPool.sol` has NO direct jurisdiction check; the cell
   consults `jurisdictionAttestation` Lexicon records that require all
   5 Council seats to sign. R1 launch = USA + JPN (Founder seat 1).
6. **NO chargeback / NO fraud reversal (G11)** — pool contract has no
   `reverse()` / `unwind()` function. On-chain finality per
   ADR-2605172100. Disputes route to `chigiri.disputeMediation`
   cooperative-first (ADR-2605262700 G10).
7. **NO new token (Charter Rider §2(b) + ADR-2605172100 Alt C +
   ADR-2605282100 N2)** — settlement uses canonical Base L2
   stablecoins (USDC + EURC at R1; +JPYC R2; +KRWO/GBPe/CHFe R3).
   mKOTO is non-transferable and only appears in the operator-DID
   compute-cost layer.

## Architecture

7 layers materialized across iters 1-9. Each layer enforces a subset
of the 14 gates:

```
L1  per-DID intent Quads  ────────────────┐
L2  per-currency pool state Quads ────────┤  kotoba (ADR-2605262130)
L3  Chainlink mid-market oracle ──────────┤
L4  Pregel match engine ──────────────────┤
L5  Adherent surface (kotoba_kawase) ─────┤
L6  KawaseYuiPool.sol contract family ────┤  Base L2 (USDC/EURC native)
L7  (audit) silenKawaseReview ────────────┘
```

**5 Pregel cells** at `40-engine/kotoba/crates/kotoba-kotodama/cells/kawase_*/` (R0 scaffold
— RuntimeError on import per kotodama convention):

```
kawase_pool_match              ── L4 continuous bipartite matcher
kawase_fx_oracle_watcher       ── L3 Chainlink subscriber + band halt
kawase_rebalance_proposer      ── pool-drift watcher → Council L7
kawase_jurisdiction_compliance ── G14 SOLE enforcement point
kawase_silen_review            ── L7 quarterly Council audit
```

**1 Solidity contract** at `50-infra/etzhayyim-kawase-pool/src/
KawaseYuiPool.sol` — one deploy per stable. R0 = scaffold with
NotYetImplemented stubs; R1 fills in bodies.

**1 Python facade** at `40-engine/kotoba_kawase/` — sibling of
kotoba_murakumo per the ADR-2605282300 downstream-consumer relocation
pattern (lives outside the kotoba subrepo).

**8 Lexicons** at `00-contracts/lexicons/com/etzhayyim/kawase/` —
deposit / withdraw intent / match execution / fx rate / pool state /
rebalance / jurisdiction / silen review.

## Tests (60+ passing, 10-iter cumulative)

| Layer | Tests | Pass count |
|---|---|---|
| KawaseYuiPool.sol | forge tests (constructor + G4 plumbing + G9 plumbing + R0 honesty) | 4/4 |
| kotoba_kawase R0 | pytest (surface + R0 honesty + introspection + hierarchy + frozen dataclasses) | 16/16 |
| kotoba_kawase composition | pytest (cross-layer + cross-actor + documentation) | 16/16 |
| G7 lint hook | pytest (each vendor caught + URL caught + docstring not caught + allow-list + unguarded path) | 23/23 |
| Constitution wave | forge test (kawase consts wired + invariant assertion) | +1 |

Plus all lefthook hooks pass on every commit (17 hooks total; G7
hook joined the matrix this loop wave).

## R1 Activation Triggers

1. **ADR-2605282200 Council Lv6+ ≥3 ratify** (RFP for Bootstrap
   Council Seats 2-5 closes 2026-06-19);
2. **Public Fund Safe seed grant** for the R1 reserve buffer (≈$5,000
   per pool USDC + EURC = $10,000 total) — Council Lv6+ ≥4/7 approval
   required per ADR-2605192145;
3. **Chainlink price-feed allow-list ratified** — Council Lv6+ ≥3
   attestation listing the canonical Chainlink USD/EUR aggregator
   address on Base L2 for `kawase_fx_oracle_watcher` to subscribe;
4. **chigiri R1 active** — `chigiri.ipLicenseClaim` cell must be live
   for `kawase_jurisdiction_compliance` to read at G14 enforcement;
5. **toritate R1 active** — `toritate.ledgerEntry` for every kawase
   flow; `toritate.annualReport` cross-references `silenKawaseReview`;
6. **wakai R1 active** — sibling mutual-aid framing precedent;
7. **Founder + at least one Bootstrap Council Seat 2-5 filled** — Lv7+
   unanimity for the R1 USA + JPN `jurisdictionAttestation` records
   (Founder seat 1 covers both).

## R1 Cell Activation Order

1. `kawase_fx_oracle_watcher` (lowest-risk; read-only Chainlink
   subscription; emits `fxRateAttestation`; halts on out-of-band but
   has no settlement responsibility);
2. `kawase_pool_match` (continuous bipartite matcher; activated after
   ≥1 hour of stable `fxRateAttestation` emission);
3. R2 adds `kawase_rebalance_proposer` (drift watcher; gated on first
   poolStateReport with |driftBps|>500) + `kawase_jurisdiction_
   compliance` (G14 expansion to multi-juris pairs);
4. R3 adds `kawase_silen_review` (after ≥1 completed annual cycle of
   matchExecution records).

## R1 Deposit / Claim Body Wiring (post Council ratify)

`KawaseYuiPool.deposit()` R1 body:

```solidity
function deposit(bytes32 intentCid, address recipientDid,
                 uint256 srcAmountMinor, uint256 fxRateBps,
                 bytes32 fxRateAttestationCid)
    external
    onlyAdherent(msg.sender)
    onlyAdherent(recipientDid)
{
    require(intents[intentCid].senderDid == address(0), "intent replay");
    (uint80 _r, int256 ans, , uint256 upd, ) = priceFeed.latestRoundData();
    require(block.timestamp - upd < 1 hours, "stale oracle");
    uint256 chainlinkBps = uint256(ans) * BPS_DENOMINATOR /
                           10**priceFeed.decimals();
    uint256 maxBand = uint256(constitution.getConstant(maxBandBpsKey));
    uint256 diff = fxRateBps > chainlinkBps
                 ? fxRateBps - chainlinkBps : chainlinkBps - fxRateBps;
    if (diff > (chainlinkBps * maxBand) / BPS_DENOMINATOR)
        revert OutOfBandFx(fxRateBps, chainlinkBps, maxBand);
    // ... per-month cap check ...
    // ... transferFrom ...
    // ... intents[intentCid] = Intent{...} ...
    emit Deposited(intentCid, msg.sender, recipientDid,
                   srcAmountMinor, fxRateBps, fxRateAttestationCid);
}
```

R0 → R1 transition: the scaffold reverts `NotYetImplemented`. The
R1 body lands as a separate ADR (ADR-2605282200-R1) once Council
ratifies the R0 charter + Public Fund grants the seed buffer.

## Cross-Actor Coordination Patterns

### kawase-yui ↔ wakai (sibling mutual aid)

Both inherit Charter §2(b) speculative-finance discipline + §1.7
反個人主義 mutual-aid framing. Functional split:

- **wakai**: in-jurisdiction health / disability / unemployment /
  disaster mutual aid (ADR-2605263500). Pool USDC-only on Base L2.
- **kawase-yui**: cross-border adherent-to-adherent multi-stable
  remittance (this actor). Pool USDC + EURC + JPYC + ...

R3 composite-flow integration: medical-evacuation = wakai medical
event + kawase international transfer composed into a single
SBT-signed flow.

### kawase-yui ↔ chigiri (G14 + G11)

- `kawase_jurisdiction_compliance` reads `chigiri.ipLicenseClaim` +
  `chigiri.taxReceipt` records to determine which juris pairs are
  Council-Lv7+-active (G14 SOLE enforcement point);
- Every G11 dispute (cannot be reversed on-chain) routes to
  `chigiri.disputeMediation` cooperative-first (chigiri G10 ≤3
  rounds before any arbitration channel may be invoked);
- chigiri has no kawase-side dependency at R0; the cross-actor link
  is read-only from kawase's perspective.

### kawase-yui ↔ toritate (accounting)

- Every kawase flow records `toritate.ledgerEntry` with
  `purpose: "kawase-mutual-aid"` (separate category from
  donation / tithe / grant);
- `toritate.annualReport` consumes `silenKawaseReview` quarterly
  records for the religious-corp-wide annual transparency disclosure;
- R1 reserve buffer seed is a Public Fund Safe disbursement →
  `toritate` records as `grant` category → Council Lv6+ ≥4/7 attests.

### kawase-yui ↔ mKOTO economy (ADR-2605282100)

- `kawase_pool_match` cell tick = mKOTO debit against operator DID
  via ADR-2605282100 L1 meter;
- Royalty credit flows to indexer DIDs symmetrically (every Quad
  write emits a royalty record per CitationLedger);
- mKOTO is the operator-side compute-cost layer; settlement layer
  uses canonical Base L2 stablecoins (per ADR-2605172100 Alt C +
  ADR-2605282100 N2 mKOTO non-transferability).

## Iter-Summary (10-iter R0 wave, 2026-05-28)

| Iter | Commit | Coverage delivered |
|---|---|---|
| 1 | `6644094ba` | G7 lint hook + 23 pytest + lefthook `no-commercial-remittance` |
| 2 | `f732e4bc4` | Constitution wiring (KAWASE_MAX_BAND_BPS const + KAWASE_PER_MONTH_CAP_USD_MINOR mutable) + 1 forge test |
| 3 | `4a312d500` | KawaseYuiPool.sol R0 scaffold (interface + modifiers + immutables) + Foundry project + 4 forge tests |
| 4 | `e105d146d` | kotoba_kawase Python facade (5 exceptions + send/claim + frozen dataclasses) + 16 pytest |
| 5 | `aecf9b262` | 5 Pregel cell R0 scaffolds (RuntimeError on import) — drops 5 (reserved) markers |
| 6 | `0889371b4` | Actor root README + manifest.jsonld — drops the FINAL (reserved) marker |
| 7 | `4a1187b90` | GitHub Actions workflow (`kawase-yui-r0-audit`) + 10 cross-layer composition tests |
| 8 | `2ca8ef437` | Cross-actor reverse-references (wakai + chigiri + toritate manifests) + 4 symmetry tests |
| 9 | `1ee9c8fb9` | Lexicon-dir README + ADR-2605282200 index entry + 2 documentation discoverability tests |
| 10 | (this) | Operator-facing CLAUDE.md + R1 activation runbook + Iter-Summary table |

## Build & Deploy

**R0 status**: Scaffold landed. No live cells, no deployed Solidity, no
Python facade in production. R0 cells RuntimeError on import per
kotodama convention; Python facade `send` + `claim` raise
`NotYetImplemented`.

R0 smoke tests (run from repo root):

```bash
# 1. G7 lint hook (no commercial remittance MSB integration)
python3 70-tools/scripts/lint/verify_no_commercial_remittance.py
# → "no-commercial-remittance gate: clean..."

# 2. G7 hook's 23 pytest
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest \
  70-tools/scripts/lint/test_verify_no_commercial_remittance.py -q
# → 23 passed

# 3. kotoba_kawase R0 + composition tests
cd 40-engine/kotoba_kawase
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 PYTHONPATH=. python3 -m pytest tests/ -q
# → 32 passed (16 R0 + 16 composition)

# 4. KawaseYuiPool forge tests
cd 50-infra/etzhayyim-kawase-pool
forge test
# → 4 passed

# 5. Each kawase_* Pregel cell raises RuntimeError on import
for cell in kawase_pool_match kawase_fx_oracle_watcher \
            kawase_rebalance_proposer \
            kawase_jurisdiction_compliance kawase_silen_review; do
  python3 -c "
import sys; sys.path.insert(0, '40-engine/kotoba/crates/kotoba-kotodama/cells/$cell'); import cell"
done
# → Each prints a RuntimeError with 'scaffold-only' + 'ADR-2605282200'
```

R1 deploy (post-Council ratify) lands as a separate runbook ADR.

## Related Files

- `/20-actors/kawase-yui/manifest.jsonld` — ActorManifest JSON-LD
- `/20-actors/kawase-yui/README.md` — Full inventory + 14 gates + R0→R3 ladder
- `/00-contracts/lexicons/com/etzhayyim/kawase/` (8 Lexicons + README)
- `/40-engine/kotoba_kawase/` (Python facade + 32 pytest)
- `/50-infra/etzhayyim-kawase-pool/` (Solidity scaffold + 4 forge tests)
- `/40-engine/kotoba/crates/kotoba-kotodama/cells/kawase_*/` (5 Pregel cell scaffolds)
- `/70-tools/scripts/lint/verify_no_commercial_remittance.py` (G7; 23 pytest)
- `/.github/workflows/kawase-yui-r0-audit.yml` (4-lane CI gate)
- `/90-docs/adr/2605282200-kawase-yui-multi-stable-adherent-remittance-mutual-aid.md` — Master ADR
- `/90-docs/adr/2605282100-kotoba-mkoto-economy-and-modal-billing-parity.md` — Compute-cost layer
- `/90-docs/adr/2605263500-wakai-mutual-aid-tier-b-actor-r0.md` — Sibling actor
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — G14 + G11 cross-actor
- `/90-docs/adr/2605262900-toritate-accounting-audit-tier-b-actor-r0.md` — Accounting cross-actor
- `/CHARTER-RIDER.md` — §2(b) + §2(c) + §2(e) sources for G7
- `/CLAUDE.md` — Religious-corp Status table (kawase-yui row to be added in a future commit)
