# 20-actors/yobel — CLAUDE rules

Tier-B per-domain leader actor for collective debt release rite.
Per [ADR-2605201800](../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md).

## Boundaries (NON-NEGOTIABLE)

| Concern | Allowed | Prohibited |
|---|---|---|
| State / records | AT MST + IPFS + Base L2 anchor via `@etzhayyim/sdk` | RisingWave / Postgres / Kysely / centralized DB |
| Settlement | USDC on Base L2 + ERC725 Smart Wallet + `TitheRouter.route()` (10% Tithe reverse-neutral for releases) | Stripe / PayPal / fiat / 銀行決済 |
| Identity | path-based DID `did:web:etzhayyim.com:yobel:...` | server JWTs without DID binding |
| Substrate clients | only via `@etzhayyim/sdk` / `kotodama` | direct `@atproto/api` / `viem` / IPFS / `@noble/ciphers` / libsignal |
| Lexicon operations | one-way debt forgiveness (declare → enroll → release) | 貸付 (loan origination) / 利息計算 (interest accrual) / margin call / liquidation / arbitrage / 担保差し押さえ |
| Eligibility | SBT membership (Council Lv1+) ∧ rite-type DMN gate | open-to-all enrollment |
| Rite declaration | Council Lv6+ × 3 ratification + Lv9 chair (per DMN) | unilateral steward declaration |
| `riteType` extension | requires ADR amending this actor + Council Lv9 vote | hardcoded scaffold-time additions |

## Cell pattern (per ADR-2605192415 §B)

Each cell directory follows the standard layout:

```
cells/{cell_name}/
├── README.md                 # input/output Lexicon + state schema + DMN gates referenced
├── cell.py                   # LangGraph StateGraph (entrypoint) — NOT YET IMPLEMENTED (S0)
├── nodes.py                  # node functions                    — NOT YET IMPLEMENTED (S0)
├── prompts/                  # LLM prompts (only audit_witness uses LLM)
└── tests/
    └── test_cell.py          # NOT YET IMPLEMENTED (S0)
```

Common deps (when implementation lands in S1):

- Checkpointing — `kotodama.checkpointer.MstCheckpointSaver` ([ADR-2605191559](../../90-docs/adr/2605191559-ameno-mst-checkpointer-stage-2-activation.md))
- MST listener — `kotodama.listener.MstListener` subscribes to `com.etzhayyim.apps.etzhayyim.yobel.*` (canonical NSID: `org.etzhayyim.yobel.*` post-cutover)
- Web3 — `kotodama.eligibility.web3_ports.{GethPrivatePort, BaseL2Port}` for `CouncilSBT` / `CouncilRatification` / `EtzhayyimPaymaster` / `TitheRouter`
- Signature verify — `kotodama.identity.erc725.verify_eip712_signed_consent(creditorDid, payloadHash, signature)`
- Anchor — `kotodama.anchor.AnchorBridge` (ADR-2605171800) for MST → IPFS → Base L2 batched anchor

## Witness invariant

`audit_witness` cell は **全 super-step + 全 release MST event** を encrypted append-only log として保持する。
読み出しは Council Lv6+ 署名要求。tampering 検知時は immediately superseded rite status + Public Fund (ADR-2605192145) audit grant 自動 emit。

## Schema invariant (Charter Rider §2(b) one-way enforcement)

Lexicon schema レベルで以下を担保:
- `enrollCreditor.debts[]` は **historical record only** — 新規債務を作成する method なし
- `recordRelease.releasedMicroUsdc` は対応する `enrollCreditor.debts[].principalMicroUsdc + accruedMicroUsdc` 以下に boundary check (utility increase 方向にのみ動く)
- `releaseMethod` enum に `liquidation` / `seizure` / `margin_call` を含めない (含めれば schema 自動拒否)

これらの invariant は lexicon JSON + cell `nodes.py` の両方で二重に gate される (defense in depth)。

## Intended use boundary

| Allowed | Prohibited |
|---|---|
| civilian / community / commons / 教区内 / 宗派内 | military debt / state-mandated debt service / corporate-coercive employment debt の "release" を介した強制労働解放偽装 |
| voluntary creditor consent | 強制収用 / 没収 / 第三者 debt の declare |
| historical record (徳政令 / Jubilee 2000 archive 等) の append-only audit log としての use | predictive market / debt forgiveness の derivative speculation |

## See also

- [`README.md`](README.md) — actor overview + cell catalog + lexicon list
- [`bpmn/yobel-rite-lifecycle.bpmn`](bpmn/yobel-rite-lifecycle.bpmn) — workflow
- [`dmn/eligibility-by-rite-type.md`](dmn/eligibility-by-rite-type.md) — DMN gate
- [`dmn/council-ratification-threshold.md`](dmn/council-ratification-threshold.md) — Council Lv 必要数
- [`dmn/tax-warning-by-jurisdiction.md`](dmn/tax-warning-by-jurisdiction.md) — per-jurisdiction COD income warning text
