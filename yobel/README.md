# yobel (יובל) — Collective Debt Release Rite Actor

**Status**: S0 — spec + lexicon + actor scaffold (no rite declared, no release executed). Apache-2.0 + Charter Rider v2.0.

Per [ADR-2605201800](../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md).
Tier-B per-domain leader actor for **集合的・教義的・政治的債務免除 rite**:
שמיטה (shmita 7yr) / יובל (yobel 49yr) / 徳政令 / Catholic Jubilee / modern political amnesty を統一データモデル + 5 cell で扱う。

Charter Mission §1 (構造的労働解放) の monetary-debt 局面における doctrinal runtime。
Charter Rider v2 §2(b) (speculative finance / predatory lending 禁止) の **structural antithesis** — one-way debt forgiveness only。

Name origin: יובל (Hebrew "ram's horn") — Lev 25:9-10 で Jubilee 年の到来を告げる horn の名。「自由を国中の全住民に布告する」(Lev 25:10 ESV) の道具。Re-read in religious-corp context as
"the signal that periodically rebalances accumulated debt-coercion across the community" — NOT "a financial product offering forgiveness as a service".

## Phase cells

| Cell | Phase | Murakumo leader | Trigger | Solidity contracts |
|---|---|---|---|---|
| [`cells/rite_declaration/`](cells/rite_declaration/) | 0 (gate) | judah | `declareRite` request (Council Lv6+ gated) | `CouncilRatification`, `ChartersComplianceRegistry` |
| [`cells/creditor_enrollment/`](cells/creditor_enrollment/) | 1 | gad | `declareRite` accepted (status=active) MST | (none — ERC725 EIP-712 signature verify only) |
| [`cells/debtor_enrollment/`](cells/debtor_enrollment/) | 1 | issachar | `declareRite` accepted MST | `CouncilSBT` (Lv1+ membership gate) |
| [`cells/release_settlement/`](cells/release_settlement/) | 2 | asher | both enrollments paired + DMN `eligibility` pass | `EtzhayyimPaymaster`, `TitheRouter` (release is reverse-tithe-neutral) |
| [`cells/audit_witness/`](cells/audit_witness/) | continuous | reuben | every super-step + every release MST | `Phenotype` (feedback only) |

Tier A (per-rite `YobelRiteAgent`) は code-generated per the
[ADR-2605171300](../../90-docs/adr/2605171300-open-unispsc-generative-agent-fleet.md) pattern; not catalogued here.

Tier C escalation は generic
[`kotodama/cells/council_deliberation/`](../kotodama/cells/council_deliberation/) per
[ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md)。
`declareRite` は **必ず** Tier C を通過する (rite declaration = doctrinal act、Three-Tier Enforcement tier 3 同等 — ADR-2605192230)。

## Lexicon namespace

`com.etzhayyim.apps.etzhayyim.yobel.*` — 8 lexicons under
[`00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/yobel/`](../../00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/yobel/):

| Lexicon | Type | Phase | Encryption |
|---|---|---|---|
| `declareRite.json` | procedure | 0 (gate) | public (rite scope is open by design — Charter §1.3 transparent) |
| `enrollCreditor.json` | procedure | 1 | XChaCha20-Poly1305 for `debts[].principalMicroUsdc` + `debts[].debtorDid` (ADR-2605181100; creditor-debtor relations sensitive) |
| `enrollDebtor.json` | procedure | 1 | XChaCha20-Poly1305 for `eligibilityProof` (may contain PII) |
| `verifyEligibility.json` | query | 0/1 | public response (eligible boolean only) |
| `recordRelease.json` | procedure | 2 | public for `baseL2TxHash` + `releasedMicroUsdc` (on-chain anyway); encrypted for `debtId` → debtor link |
| `listRites.json` | query | — | public (aggregate metrics only) |
| `getRite.json` | query | — | public |
| `listReleases.json` | query | — | public (aggregate; debtor identity redacted unless caller=debtor) |

## BPMN

[`bpmn/yobel-rite-lifecycle.bpmn`](bpmn/yobel-rite-lifecycle.bpmn) — declare → ratify (Council Lv6+) → enroll (creditor + debtor) → eligibility gate (DMN) → release → audit → expire (or supersede).

## DMN gates

| Table | Purpose |
|---|---|
| [`dmn/eligibility-by-rite-type.md`](dmn/eligibility-by-rite-type.md) | rite type 別 eligibility 条件 (shmita = community member ∧ debt origination < rite cycle start; yobel = land tenure; tokusei = jurisdiction match; political_amnesty = sovereign decree scope) |
| [`dmn/council-ratification-threshold.md`](dmn/council-ratification-threshold.md) | Council Lv 必要数 (rite type + 影響額 + scope breadth で決まる。全 rite に最低 Lv6+ × 3 + Lv9 chair 1) |
| [`dmn/tax-warning-by-jurisdiction.md`](dmn/tax-warning-by-jurisdiction.md) | jurisdiction 別 COD income / tithe / Schuldenerlass 課税 warning text |

## Invariants (NON-NEGOTIABLE)

- **One-way debt forgiveness only.** 新規貸付・利息計算・margin・liquidation・arbitrage は不実装 (Charter Rider §2(b) compliance — schema レベル invariant、`enrollCreditor` の入力 `debts[]` は readonly historical record として扱われる)
- **Voluntary opt-in only.** creditor `signedConsent` 必須 (ERC725 EIP-712 or DPoP)。secular creditor 無視時は native `saisei` (自己破産・個人再生の自己申立て支援) fallback — 旧 vendor:bankruptcy.gftd.ai 参照は ADR-2607061800 で etzhayyim 側 saisei actor に relocate 済み
- **Doctrinal authority のみ.** secular law を override する主張は出さない。`declareRite.doctrinalBasis` 必須 field で根拠を強制
- **No fiat, no RW.** USDC on Base L2 only (ERC725 Smart Wallet); state は AT MST + IPFS + Base L2 anchor のみ
- **Council Lv6+ ratification.** rite declaration は Three-Tier Enforcement tier 3 同等の重要性 (ADR-2605192230)
- **Tax warning, not tax advice.** `verifyEligibility.warnings[]` で per-jurisdiction COD income warning。税務 advice は vendor:lawfirm.etzhayyim.com に delegate

## Tests

`bash run_tests.sh` runs every cljc test namespace via babashka (no Python). The
suite is fully ported off `web3`/`eth_account` — all Ethereum crypto/signing goes
through **eth-crypto-clj** (`sign-tx-legacy` EIP-155, `secp256k1-sign`,
`eip712-digest`/`ecrecover`, `keccak256`, RLP); the `web3`/`eth_account` Python
dependency is **fully removed**.

The EVM integration harness — `tests_integration/test_web3_roundtrip.cljc`
(bb port of the deleted `tests_integration/test_web3_roundtrip.py`) — is
**operator-run**: it needs foundry's `anvil` on PATH. When `anvil` is ABSENT it
**skips gracefully** (logs + passes with zero assertions, so CI stays green); its
ABI-selector verification, `sign-tx-legacy` signing path, and EIP-712 signed-consent
accept/reject legs still run unconditionally (no chain needed). With `anvil` present it
spawns it, deploys `YobelRiteRegistry` + `YobelReleaseRegistry` from the `abi/*.json`
bytecode via `eth_sendRawTransaction`, and runs the declare→ratify→release roundtrip +
the §2(b) over-cap revert through the cljc ports.

## See also

- [ADR-2605201800](../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md) — design SSoT
- [ADR-2605192100](../../90-docs/adr/2605192100-etzhayyim-mission-charter.md) — Charter §1 mission alignment
- [ADR-2605192230](../../90-docs/adr/2605192230-etzhayyim-three-tier-enforcement-implementation.md) — Council ratification framework
- [ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) — cell hierarchy
- Twin design ADR (vendor): `etzhayyim:90-docs/adr/2605201700-yobel-jubilee-shmita-debt-release-actor.md`
- [`../saisei/`](../saisei/) — mandatory legal procedure fallback for natural persons (self-bankruptcy/individual rehabilitation, R0: jp/us/uk/de), native etzhayyim actor per ADR-2607061800 (relocated off vendor:bankruptcy.gftd.ai, which never left S0 scaffold)
- vendor:lawfirm.etzhayyim.com — creditor consent letters, court filings, tax advice delegate
