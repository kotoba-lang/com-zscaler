# warifu lexicons

Wire-level AT-Proto lexicons live in **`10-protocol/warifu/`** under the `com.etzhayyim.card.*`
namespace:

| Lexicon | Purpose |
|---|---|
| `com.etzhayyim.card.issue` | issue a soulbound card (WarifuCard, ERC-5192) bound to a holder smart account |
| `com.etzhayyim.card.authorize` | authorize (debit hold / credit reserve) |
| `com.etzhayyim.card.capture` | capture an authorized hold |
| `com.etzhayyim.card.settle` | on-chain settlement (T+0, fee 0) |
| `com.etzhayyim.card.refund` | reverse transfer (purpose `escrow-refund`) |
| `com.etzhayyim.card.dispute` | chargeback / dispute record → chigiri procedure |

Per-cell I/O contracts (`warifu.authorize`, `warifu.settle`, …) live in `../cells/lex/`.
