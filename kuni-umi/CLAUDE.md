# 20-actors/kuni-umi — CLAUDE rules

Tier-B per-domain leader actor for the planetary infrastructure robotics fleet.
Per [ADR-2605201400](../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md).

## Boundaries (NON-NEGOTIABLE)

| Concern | Allowed | Prohibited |
|---|---|---|
| State / records | AT MST + IPFS + Base L2 anchor via `@etzhayyim/sdk` | RisingWave / Postgres / Kysely / centralized DB |
| Payments | USDC on Base L2 + `TitheRouter.route()` (10% Tithe) + ERC-4337 paymaster | Stripe / PayPal / fiat |
| Identity | path-based DID `did:web:etzhayyim.com:kuniumi:...` | server JWTs without DID binding |
| Substrate clients | only via `@etzhayyim/sdk` | direct `@atproto/api` / `viem` / IPFS / `@noble/ciphers` / libsignal |
| Hard-RT motion | Giemon firmware (open-robo + open-ot field tier WAMR) | LangGraph cells driving motion at > 10 Hz |
| Safety-critical functions | certified parallel safety PLCs (IEC 61508 / 61511) | kuni-umi cells implementing SIL functions |
| `intendedUse` | civilian / community / commons | military / proprietary closed-design |

## Cell pattern (per ADR-2605192415 §B)

Each cell directory:

```
cells/{cell_name}/
├── README.md                 # input/output Lexicon + state schema
├── cell.py                   # LangGraph StateGraph (entrypoint)
├── nodes.py                  # node functions
├── prompts/                  # LLM prompts (if cell uses LLM)
└── tests/
    └── test_cell.py
```

Common deps:

- Checkpointing — `kotodama.checkpointer.MstCheckpointSaver` ([ADR-2605191559](../../90-docs/adr/2605191559-ameno-mst-checkpointer-stage-2-activation.md))
- MST listener — `kotodama.listener.MstListener` (subscribes to `com.etzhayyim.kuniUmi.*`)
- Web3 — `kotodama.eligibility.web3_ports.{GethPrivatePort, BaseL2Port}` for `ChartersComplianceRegistry` / `TitheRouter`
- UNSPSC procurement sub-graph — invoke via `kotodama.unispsc.dispatch(commodityCode, intent="procure", qty=...)`
- Giemon fleet driver — invoke via `kotodama.open_robo.fleet.dispatch(fleetDid, taskCid)`

## Witness invariant

`recordConstructionProgress` と `recordPhysicalAuditEvent` は必ず N ≥ 2 independent robot DID 署名を carry する。N=1 で MST に書き込もうとした場合 `MstListener` が即 reject + Council escalation `Lv6+` 通知。これは constitutional invariant。

## Phasing gate

S0 is this scaffold. S1+ requires:

- a `LandRegistry` (or `OceanStewardship` / etc.) registered plot
- ≥ 1 Giemon Otete unit with valid `did:web:etzhayyim.com:kuniumi:robot:<serial>` Ed25519 key
- Council Lv6+ 3 名以上の sign-off on the first site (per ADR-2605201400 §7)

Do NOT skip phases. Each S transition is its own ADR.

## See also

- ADR-2605201400 (master)
- ADR-2605192415 (3-tier actor + Murakumo placement)
- ADR-2605171800 (checkpoint pipeline)
- `40-engine/kotoba/crates/kotoba-kotodama/cells/README.md` (sibling cell catalog)
