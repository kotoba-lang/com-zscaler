# sonae (備え) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで **1項目** だけ成熟度/coverage を上げ、ここに記録する。
honest framing (G8): できていないことは「未」と明記する。

- Actor: `did:web:sonae.etzhayyim.com` · ADR-2606091200 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行(import時 RuntimeError) ·
  **PRE-disaster only**(予防/減災/備え/早期警戒。response は kazaori、N3 フェーズ境界を越えない) ·
  **NO false authority**(G8 — 公式 地震/津波 警報を発出しない。earlyWarningRelay は
  relay-only + authoritativeSource 必須) · **NO unilateral declaration**(G10 — emergency
  state は kazaori Council Lv6+ ≥4/7 が所有、sonae は signal/recommend のみ) ·
  CIVILIAN-ONLY(G5+N1) · open gov feeds のみ(G4) · no surveillance / no individual data(G6) ·
  community-scale(G3) · Murakumo-only inference(G7) · コミットはユーザー明示時のみ

## 現状サマリ

- 整合: `manifest-lexicon-drift` audit で **sonae はドリフト 0**(manifest の 6
  lexiconNamespaces が 6 ファイルへ解決)。
- coverage: fail-closed invariants test **計20件緑**(Python 版、併存)— 憲法ゲート
  10件(`test_sonae_lexicon_invariants.py`)+ warning-source registry 10件
  (`test_sonae_warning_sources_seed.py`)。**2026-07-10 に native cljc 版
  `methods/test_charter_gates.cljc`(bb `run_tests.clj`)へ移植 — 22 test /
  249 assertion 緑**(runtime-priority debt 解消。repo-root CLAUDE.md の
  kotoba wasm > clojurewasm > cljs > nbb > 降格 JVM/bb ラダーに Python は無い)。
- data asset: `registry/warning-sources.seed.json` — 権威ある警報発信元の
  allowlist **20件 / 16 jurisdictions**(G8 relay 元)。
- 未: cell solver(R1 で実装、現状 path-reserved のみ) · 実 feed 連携(R1) ·
  Sendai 自己評価 / 訓練 / Council 承認(R1 起動ゲート) · registry VERIFICATION.md
  (kazaori 同様の G14 三層 + 人手チェックリストは未着手) · 全件 unverified-seed
  (人手検証 0、honest)。

## イテレーション記録

- 2026-06-09 invariants coverage: sonae の憲法ゲートを schema レベルで pin する
  fail-closed invariants test `70-tools/scripts/audit/test_sonae_lexicon_invariants.py`
  (10 test、緑)を新設。pin 内容 — manifest が did:web:sonae.* の ActorManifest /
  cells == 6(全 naphtali)/ lexiconNamespaces == 6(全 com.etzhayyim.sonae.* で
  ファイル解決 + id 一致)/ **G8** earlyWarningRelay.relayOnly const true +
  authoritativeSource required / **G6+G4** hazardSignalRecord.openDataOnlyAttested
  const true + sourceFeed ⊆ open-gov-feed allowlist / **G3+G6**
  siteRiskProfile.communityScaleAttested const true + 個人レベルフィールド名の不在 /
  **G6** drillAttestation.participantsOptIn const true / **G4 negative** 禁止商用ベンダ
  (One Concern / FloodFlash / Jupiter Intelligence / Tomorrow.io / Everbridge / OnSolve /
  AlertMedia / RMS)が allowed `knownValues` に出現しない / manifest が
  falseAuthorityInvariant + phaseBoundaryInvariant + N3(not response)を宣言。
  併せて `manifest-lexicon-drift` audit で sonae ドリフト 0 を確認。
  test-only・network-free・cell 非実行で **R0 ceiling 不変**。

- 2026-06-09 warning-source registry seed: G8(relay-only)を支える「権威ある警報
  発信元の allowlist」`registry/warning-sources.seed.json`(**20 source / 16
  jurisdictions**)を新設 — PHIVOLCS / PAGASA / JMA / PTWC / NOAA-NWS / USGS /
  BMKG / GDACS / Copernicus EMS / IMD / GeoNet+NEMA / BOM+JATWC / SHOA / SASMEX /
  AFAD / INGV / CWA / IOC-ITIC / NOA / INCOIS。全件 `isAuthoritativeIssuer=true` +
  `verificationStatus="unverified-seed"`(G14)+ notes が relay-only 境界を再宣言。
  併せて fail-closed test `70-tools/scripts/audit/test_sonae_warning_sources_seed.py`
  (10 test、緑)を新設 — JSON parse + sourceId 一意 + 全件 unverified-seed +
  accessUrl/provenance が http(s) + lastVerified ISO-8601 Zulu + ≥12 jurisdictions +
  sourceKind タクソノミ + **G8** 全件 authoritative かつ notes が relay/G8 を再宣言 +
  freshnessWindowDays 整数 + **G8 cross-lexicon** earlyWarningRelay の
  authoritativeSource enum(catch-all 除く {phivolcs, jma, usgs, ptwc, noaa-nws})が
  registry の `authoritativeSourceToken` で全て realize される + registry token が
  lexicon enum 語彙内。registry は kazaori と同じく OBSERVATIONAL/relay-only、
  自前 alert 発信なし。test-only・network-free・cell 非実行で **R0 ceiling 不変**。

- 2026-06-09 SSoT registration + ADR as-built closing: 機械可読 SoT `deps.toml`
  に sonae ブロックを追加(`[[adrs]]` 2606091200 + actor module + lexicon-namespace
  module の計2 module;sibling_actors / phase_gate / 8 憲法フラグ含む)。ADR
  `2606091200` に「R0 scaffold status (as-built)」節を追記(オンディスク成果物 +
  20 test 緑 + drift 0 + 未達 R1 ゲートを honest に列挙)。最終検証:sonae test
  **20件緑** / drift 0 / deps.toml + 全 sonae JSON parse OK。R0 ceiling 不変。
  → R0 scaffold は **登録・整合・検証まで closing**。次の成熟は R1 ゲート
  (Council 承認 / 実 feed / Sendai 自己評価 / 訓練)か registry VERIFICATION.md。

- 2026-07-10 runtime-priority debt closed (native cljc port): repo runtime 優先順位
  (kotoba wasm > clojurewasm > cljs > nbb > 降格 JVM/bb; Python はこの梯子に無い)
  に沿って、既存 20 件の Python fail-closed invariants(`test_sonae_lexicon_invariants.py`
  10件 + `test_sonae_warning_sources_seed.py` 10件)を chigiri/narashi と同形の
  substrate-native `20-actors/sonae/methods/test_charter_gates.cljc` +
  `run_tests.clj`(bb)へ移植。ゲート内容は sonae 自身の manifest.jsonld/CLAUDE.md/
  ADR-2606091200 から再導出(Python の盲目転写ではない)— **22 test / 249
  assertion、全緑**: manifest identity + 構造カウント(6 cells 全 naphtali / 6
  lexiconNamespaces / **G1..G12** 12 gates / **N1..N12** 12 non-goals、cell 名集合
  drift guard 追加)/ 全 namespace が id 一致でファイル解決 / **G8** relayOnly
  const true + authoritativeSource 必須 / **G6+G4** hazardSignalRecord
  openDataOnlyAttested const true + sourceFeed ⊆ open-feed allowlist / **G3+G6**
  siteRiskProfile communityScaleAttested const true + 個人レベルフィールド不在 /
  **G6** drillAttestation participantsOptIn const true / **G4 negative** 禁止
  ベンダ不在 / manifest が falseAuthorityInvariant + phaseBoundaryInvariant +
  civilianOnlyInvariant + N3(not response)を宣言 / **G10 新規**: 6 lexicon 全件
  emergencyDeclared 等の宣言フィールド構造的不在 / **新規**: drillAttestation が
  kazaori R1 drill gate 用 satisfiesKazaoriR1DrillGate を露出 / registry
  (warning-sources.seed.json)10 invariants(unique sourceId / 全件
  unverified-seed / URL+ISO8601 timestamp / 16 jurisdictions ≥12 / sourceKind
  taxonomy / G8 authoritative+relay notes / freshnessWindowDays / lexicon
  vocabulary の双方向カバレッジ)。Python 版は本イテレーションでは削除せず併存
  (退役は native 版の CI 実証後のフォローアップ)。test-only・network-free・
  cell 非実行で **R0 ceiling 不変**。
