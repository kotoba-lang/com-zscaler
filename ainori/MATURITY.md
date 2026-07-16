# ainori (相乗) — Maturity Ledger

`/loop` 進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに記録する。
honest framing: できていないことは「未」と明記する。

- Actor: `did:web:etzhayyim.com:actor:ainori` · ADR-2606071500 · **R0+R1 (offline)**
- Pooled passenger-mobility commons — Uber の charter-clean inversion。members が空席を提供、
  riders は実費(燃料/摩耗)を cost-share、platform は `cash≡0`。human-pooled + autonomous-pooled。
- 不変条件(厳守): **no-gig** (`driverWageMinor≡0`, `gig:const false`) · **no-surge**
  (cost-share split のみ、需要で上がらない) · **no person-tracking** (G7/G12 — 連続位置なし、
  origin/destination + ephemeral state のみ) · **SAE-L4 envelope** (G3 — 超過/ODD外は refuse、
  clamp しない、todoke envelope 共有) · **no-server-key** (G5 — settlement は member-signed、
  server origin 拒否)。

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了 |
|---|---|---|---|
| 1 | ADR-2606071500 + dividend coupling (2606032130) | ✅ | init |
| 2 | manifest + CLAUDE.md + 2 Lexicons (`rideRequest` / `rideMatch`) | ✅ | init |
| 3 | `methods/pooled_route.cljc` — todoke route core (NN+2-opt) を再利用、parity-pinned | ✅ | init |
| 4 | `methods/test_pooled_route.cljc` green (9 tests / 16 assertions) — todoke の `plan_last_mile` 順序と一致 | ✅ | init |
| 5 | `py/agent.py` matching + cost-share + settlement — **15 py tests green** | ✅ | init |
| 6 | **settlement G10/G5 no-auto-execute 修正** (本セッション) | ✅ | **iter 2026-06-18** |
| 7 | run_tests.sh が **py(agent: settlement G5/G10 guards)+ cljc(pooled_route)両方** を fleet green-check に wired | ✅ | **iter 2026-06-18**(従来は cljc のみ、settlement の py reflex が抜けていた) |
| 8 | live dispatch / actuation(実 ride matching の外界実行) — Council Lv6+ + operator gated (G10);autonomous-pooled near-persons = Lv7+ | 未(R2+) | — |
| 9 | warifu Phase-2 settlement(intent → 実 USDC+TitheRouter)| 未(warifu R2 待ち) | — |

## イテレーション記録

### iter 2026-06-18 — settlement の R2-autonomous auto-execute 退行を修正(red→green)
**上げた項目: #6。** `py/test_agent.py::test_driver_wage_zero_and_exact_split` が RED だった。根本原因は
SEVERE-3 と同じ "R2 Autonomous" 退行: `build_settlement_intent` が `state="executed"` を**無条件**で
セットし(`# R2 Autonomous`)、未署名の settlement を auto-executed にして、operator-gate (G10) と
member-signed `authorize_settlement` (G5/G7) の両方を迂回していた。**修正**:
- `build_settlement_intent`: `state = "executed" if operator_ref else "intent"`(G10 operator-gated
  execution;operator が無ければ member 署名が実行する `intent` に留まる)。
- `authorize_settlement`: member 署名された intent を `→ executed` に遷移(member が write author)。
- stale な「operator_ref no longer required」docstring を honest な G10/G5/G7 文に修正。

`py/test_agent.py` **15/15 green**(`test_driver_wage_zero_and_exact_split` = no-operator ⇒ `intent`;
`test_broadcast_needs_operator` = operator ⇒ `executed`;`test_only_member_signature` = server 拒否、
member 署名)。systemic finding `90-docs/260617-...` で ainori RESOLVED に更新、ADR-2606181200 が
6-actor pattern を ratify。ゲートは弱めず — no-auto-execute (G5/G7) + operator-gated-execution (G10)
を復元・test-lock しただけ。`.solve()` 相当の live dispatch は依然 R2+ Council-gated(未)。
