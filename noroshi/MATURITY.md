# noroshi (烽) — Maturity Ledger

`/loop` 進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに記録する。
honest framing: できていないことは「未」と明記する。

- Actor: `did:web:etzhayyim.com:actor:noroshi` · ADR-2606051600 · Tier-B · **R0+R1 (offline)**
- 光電融合 (photonics-electronics convergence) comms chip + ISAC (integrated sensing &
  communication) + packaging robotics。link-budget/CPO + OFDM-radar JCAS + active-alignment。
- 不変条件(厳守): **civilian-only**(兵器/軍事 RF-seeker は表現不能) · **object-not-person**
  (ISAC センシングは物体のみ、人物追跡なし) · **active-alignment under laser-safety**
  (IEC 60825) · **open-EDA**(プロプライエタリ EDA を SoR にしない) · **Murakumo-only**
  inference(ADR-2605215000) · **no-server-key**(ADR-2605231525) · live actuation は G8-gated
  (`.solve()` は R0 で RuntimeError)。

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了 |
|---|---|---|---|
| 1 | ADR-2606051600 (master) + dividend coupling (2606032130) | ✅ | init |
| 2 | manifest + CLAUDE.md + 5 Lexicons (`com.etzhayyim.noroshi.*` — photonicDevice/opticalLinkBudget/isacWaveform/senseEstimate/packagingJob) | ✅ | init |
| 3 | **7 method impl を cljc に移行** (active_alignment / cable_endpoint / fibre_loop / isac_sim / kami_isac_bridge / link_budget / pic_layout) — substrate-native, py pruned | ✅ | port-wave |
| 4 | **11 cljc テストスイート green** — `run_tests.sh` で **163 tests / 552 assertions / 0 fail**(active-alignment / cable-endpoint / **charter-invariants** / consistency / fibre-loop / governance / isac-sim / kami-isac-bridge / lexicons / link-budget / pic-layout) | ✅ | port-wave |
| 5 | charter-invariants テストが civilian / object-not-person / laser-safety / open-EDA ゲートを assert | ✅ | port-wave |
| 6 | run_tests.sh が fleet green-check に wired(全 cljc ns を bb で実行) | ✅ | port-wave |
| 7 | kami-engine ISAC bridge(`kami_isac_bridge.cljc` — OFDM-radar JCAS を kami-genesis 物理で検証) | ✅ | port-wave |
| 8 | **本台帳 (MATURITY.md)** を新設 — 成熟度の honest 追跡を開始 | ✅ | **iter (this)** |
| 9 | R1 live legs (実 PIC レイアウト→fab handoff / 実 fibre 整列 actuation) — G8 Council Lv6+ + operator gated | 未(R1+) | — |
| 10 | Murakumo-fleet autorun heartbeat(他 observatory actor の shionome パターン parity) | 未 | — |
| 11 | 実 link-budget/ISAC データの kotoba Datom log 投影 + commit-DAG | 未 | — |
| 12 | **`device_design` + `reliability_qual` を pure `.edn` scaffold から coded cell へ成熟** — `methods/device-design.cljc`(civilian-gate G1/G3/N1 + open-EDA plan生成、`methods/pic-layout` 呼び出し)+ `methods/reliability-qual.cljc`(Telcordia GR-468 SHAPE-only PASS/FAIL エンジン、4試験種 thermal-cycling/damp-heat/mechanical-shock/fibre-pull、`:representative` 閾値、G10)+ 両セルの `cells/{device_design,reliability_qual}/{cell,state_machine}.cljc`。coded cell が 1→3 に増加(`active_alignment` + 新2件)。`methods/active-alignment.cljc` に IEC 60825 `classify-laser-class`(power-mw/wavelength-nm からの ground-truth 再計算、G10 representative AEL)を追加し、`cells/active_alignment/state_machine.cljc` が任意で claimed class を独立検証(既存呼び出しには後方互換、新規オプトイン引数のみ) | ✅ | **iter (this)** |
| 13 | `reliability_qual` 用に `kotoba/schema.edn` + `00-contracts/schemas/photonic-convergence-ontology.kotoba.edn` に `:qual/*` 属性追加、`kotoba/seed.edn` に representative qual-plan 1件 | ✅ | **iter (this)** |
| 14 | 17 cljc テストスイート green — `run_tests.sh` で **233 tests / 751 assertions / 0 fail**(#4 の 11 に加え test-device-design / test-reliability-qual / cells.device-design.test-state-machine / cells.reliability-qual.test-state-machine の 4 件、既存 test-consistency / test-governance / test-lexicons / test-active-alignment も 3-coded-cell 前提へ更新の上 green) | ✅ | **iter (this)** |
| 15 | live GR-468 環境チャンバー試験・live IEC 60825 実測分類・photonic EDA ツール実連携(GDSFactory 実インストール) — 全て G8 Council Lv6+ + operator gated | 未(R1+) | — |

## イテレーション記録

### iter (this) — 2026-06-18
**上げた項目: #8 — MATURITY.md を新設。** noroshi は既に実質的に成熟(7 cljc method impl + 5
Lexicons + 11 テストスイート **163 tests / 552 assertions green**、ISAC sim + kami-engine bridge)
だが成熟度台帳が無かった。fleet-wide な `run_tests.sh` reflex sweep で noroshi の reflex が green
であること(163/552、0 fail)を確認した上で、現状を honest に記録(✅ 済み項目と R1 以降の「未」を
明記)。ゲートは一切触れず — charter-invariants テストが civilian/object-not-person/laser-safety/
open-EDA を assert 済みであることを台帳に記録しただけ。`.solve()` は R0 で RuntimeError のまま
(live actuation は G8-gated、未)。

### iter (this) — 2026-07-08
**上げた項目: #12-14 — `device_design` + `reliability_qual` を coded cell へ成熟、`:qual/*` schema
追加、IEC 60825 ground-truth 分類の追加。** 6セット中2セットが「`.edn` scaffold のみ、実装ゼロ」
だったのを実質コード化。設計判断(honest に記録):

- **GR-468 は SHAPE only(G10)。** `methods/reliability-qual.cljc` の `default-suite` にある温度
  範囲・サイクル数・湿度・時間・衝撃g・引張力は全て「一般公開されているエンジニアリング文献で
  よく引用される代表値」であり、有償の Telcordia GR-468-CORE 本文の検証済み引用ではない。各値に
  `:representative true` を付し、実運用では operator が実際のライセンス済み閾値に差し替える前提
  を docstring に明記。
- **IEC 60825 レーザークラス判定も同様に representative。** `classify-laser-class` の AEL 閾値
  (`representative-ael-mw`)は公開文献のオーダー感であり、波長・曝露時間依存の正式な AEL 表の
  検証済み引用ではない(1M/2M 発散ビームサブクラスも省略)。`cells/active_alignment/state_machine`
  は `laser_power_mw`/`wavelength_nm` が供給された場合のみ独立再計算し、claimed class がそれを
  過小申告していれば拒否する — 供給されない既存呼び出し(全既存テスト含む)は完全後方互換。
- **アーキテクチャ上の意図的な逸脱。** `active_alignment`/`fibre_loop` の既存 state machine は
  `methods/` 側の計算関数を一切呼ばない(pre-computed な数値を state dict 経由で受け取るだけ)。
  今回の2セルは意図的にこの前例から外れ、state machine が `methods/device-design`・
  `methods/reliability-qual` を直接呼ぶ — 「本物の PASS/FAIL 判定エンジン」が今回のゴールその
  ものであるため。
- **`.solve()` は今回も一切変更していない。** 2セルとも R0 scaffold のまま `RuntimeError` を
  投げる — Council ADR 承認まで live activation はしない(G8)。live 環境チャンバー・live
  レーザー測定は今回のスコープ外(項目#15)。
- Python 版セル(`cell.py`/`state_machine.py`)はあえて追加しなかった — root CLAUDE.md の
  「新規オペレーショナルコードは clj/bb、Python/shell は禁止」規約(2606072802)に従い、
  `active_alignment`/`fibre_loop` の py+cljc 二重実装パターンより新規則を優先。
