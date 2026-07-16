# danjo (弾正) — Maturity Ledger

`/loop` (30分毎) の進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに
記録する。honest framing: できていないことは「未」と明記する。

- Actor: `did:web:danjo.etzhayyim.com` · ADR-2605301600 (+ ADR-2605302245 global
  fiscal-flow extension) · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 · live ingestion/dispatch なし ·
  **NON-adjudicating (G4)** — 犯罪/不正/法令違反 を断定しない、verdict なし ·
  **passive-only ingestion (G3)** — 既公開 IPFS-pinned `gov.dataset.*` のみ、portal 再 scrape 禁止 ·
  source-provenance 必須 (G5) · open method (G6) · Transparent Force discipline (G11/§1.12) ·
  Murakumo-only inference (ADR-2605215000) · G8 非捏造 · G14 verified-source-only ·
  コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605301600 (master) + ADR-2605302245 (fiscal-flow) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | Lexicon skeletons (`com.etzhayyim.danjo.*`) | ✅ | init |
| 4 | worldwide fiscal-source registry seed (`registry/sources.seed.json`, 全件 unverified-seed) | ✅ | seed |
| 5 | fail-closed registry invariants test + G14 VERIFICATION.md | ✅ | この iter |
| 6 | `run_tests_clj.sh` の3 suite (`test_budget_ledger.clj`/`test_kotoba.clj`/`test_autorun.clj`) が dormant (実行不能) | ✅ 3/3解消 (`test_budget_ledger.clj`+`test_kotoba.clj`+`test_autorun.cljc` すべて green、`run_tests_clj.sh` 全11 suite green) | 2026-07-10 |

## イテレーション記録

### 2026-07-10 (loop) — `test_autorun.cljc` 復旧(classpath設定のみ、コード側は無傷)
#6 の3件目(前々回・前回で1,2件目が別PRで対応済み)。今回は**単純なclasspath未設定が原因**、
budget_ledger/kotobaで見つかったようなAPIドリフトやランタイムバグは無かった:

- `test_autorun.cljc` は sibling test群(load-file方式)と違い
  `(:require [danjo.methods.autorun :as autorun] [danjo.methods.kotoba :as kotoba])` という
  namespace-qualified require を使う(このファイルだけ `clojure.test`/`deftest`/`is` の正規フレームワークを
  使っており、その方が自然な形)。`methods/` から `bb test_autorun.cljc` すると
  `danjo/methods/autorun.cljc` を classpath 上で解決できず即失敗していた——ns `danjo.methods.autorun`
  が指す実ファイルパスは `20-actors/danjo/methods/autorun.cljc` なので、classpath root は
  `20-actors/`(= `methods/` から2階層上、`../..`)である必要があった。
- `run_tests_clj.sh` を修正: `test_autorun.cljc` の実行時だけ bb に `-cp ../..` を渡す(他 suite は
  load-file方式のため無変更・無影響)。JVM(`CLJ_RUNNER=clojure`)側は `-Sdeps '{:paths ["." "../.."]}'`
  を渡すよう対応したが、**honest な既知の限界**: `test_autorun.cljc` の `-main` ガードは
  `(= *file* (System/getProperty "babashka.file"))` という **bb 固有**のプロパティを見ているため、
  JVM clojure 経由では `-main` が呼ばれず 0 テストのまま exit 0 で静かに終わる(このPRとは無関係の
  既存挙動、bb がこの suite の default/実質唯一の実行経路であることに変わりはない — 未修正のまま残す)。
- suite 名も `run_tests_clj.sh` 側で `test_autorun.clj`(存在しない)→ `test_autorun.cljc`(実体)に修正。

**結果**: `bb -cp ../.. test_autorun.cljc` → **7 tests, 27 assertions, 0 failures, 0 errors**。
`./run_tests_clj.sh` フル実行で **全11 suite green**(#3022 の `test_kotoba.clj` 修正とあわせ、#6 の
3 suiteすべて解消)。

### 2026-07-10 (loop) — `test_kotoba.clj` 復旧(訂正: `kotoba.cljc` に実バグは無かった)
#6 の2件目を修正。**今回はテスト側のアクセスパターンのみが原因で、`kotoba.cljc` 自体にバグは無かった**
— 前回の診断ノートで「`kotoba.cljc` の tamper-detection ロジック自体に未検証の潜在バグがある可能性」
と書いたが、これは誤りだったので訂正する(honest, G8):

- `kotoba.cljc` の house style(ns docstring に明記)は EAVT の op タグ・全マップキーが **verbatim
  string**(Python port の `':ns/name'` 文字列をそのまま保持、keyword ではない) — 例:
  `[":db/add" e a v]`(4要素ベクタの先頭が文字列 `":db/add"`)、`make-tx` が返す `{":tx/cid" ... ":tx/count" ...}`
  も string キー、`verify-chain` が返す `{"ok" .. "length" .. "broken_at" ..}` は **アンダースコア**
  (`broken_at`、ハイフンではない)の string キー。
- 旧テストは `(:ok v)`/`(:broken-at v)`/`(:tx/cid tx)`/`(= :db/add (first %))` 等、全箇所で keyword
  アクセスしていた — `broken-at`(ハイフン)は `broken_at`(アンダースコア)とも綴りが違うため、
  keyword化しても一致しない。`(>= nil 0)` の例外は「フィールド名の綴り違い」が原因で、
  tamper-detection ロジックの欠陥ではなかった。
- 全 keyword アクセスを文字列リテラル比較 / `(get ... "field")` に書き換えるだけで解決。
  `kotoba.cljc` 側は**無修正**。

**結果**: `bb test_kotoba.clj` 16/16 green。`test_autorun.clj` の解消は別PR(上記エントリ参照)で対応。

### 2026-07-10 (loop, 前回) — `test_budget_ledger.clj` 復旧 + `budget_ledger.cljc` の実バグ修正
前 iteration の診断(#6 の1件目)を実際に修正。**単純なロードパス修正だけでは済まなかった** — 2つの
独立した問題があった:

1. **テスト側のAPIドリフト**: `test_budget_ledger.clj` は `(load-file "budget_ledger.clj")`(実体は
   `.cljc`)と `bl/canonical-json`(現行は `canonical-json-utf8`、`defn-` private)、`(:cid ln)` 等の
   keyword アクセス(現行の `normalize-record`/`build-ledger` は **string キー**を返す — JSON lexicon
   フィールド名と1:1)を前提にしていた。ロードパスを `.cljc` に、alias を `danjo.methods.budget-ledger`
   に修正し、直接 private 関数を呼んでいた3件の canonical-json 単体テストは削除(record-cid の
   byte-identical golden 経由で間接的に検証済みのため削除しても coverage は落ちない)、残り全ての
   keyword アクセスを `(get ... "field")` に書き換え。
2. **`budget_ledger.cljc` 自体の実バグ**: 修正したテストを実行したところ
   `normalize-record` が `(long (get rec "fiscalYear" 0))` で `ClassCastException` — `fiscalYear` が
   文字列("2024")で来ると `long` は String を直接キャストできず落ちる。docstring は「coerces
   fiscalYear to int」と明記しており(= 文字列入力を想定した設計)、実データ(`gov-fiscal-seed.jp.json`)
   はたまたま int 型のため今まで踏まれていなかった潜在バグ。`as-long`(String/Number 両対応、
   `#?(:clj (Long/parseLong v) :cljs (js/parseInt v 10))`)を追加して修正。
3. Golden CID値(`record-cid` の synthetic record CID / 実データ先頭行CID)は**修正前後で完全に一致**
   することを確認済み — canonical-json-utf8 のハッシュロジック自体は無傷、ドリフトはテストのアクセス
   パターンと fiscalYear 型ハンドリングの2点のみ。

**結果**: `bb test_budget_ledger.clj` 14/14 green。`./run_tests_clj.sh` 全体では
`test_kotoba.clj`/`test_autorun.clj` は前回診断のまま **未解決**(honest — 別の独立した根本原因、
今回の1項目原則の範囲外)。

### 2026-07-10 (loop, 前回) — dormant test-suite drift の正確な診断(修正は未実施、honest framing)
`run_tests_clj.sh` の3 suiteが `FileNotFoundException` で全滅していた件(前 iteration で発見)を実際に
調査。**単純なファイル名ズレではなく、複数の独立した根深い問題と判明**したため、今回は診断のみに留め、
誤った"fix"を主張しない:

1. **`test_budget_ledger.clj`**: `(load-file "budget_ledger.clj")` は存在しない(実体は
   `budget_ledger.cljc`)。ロードパスを `.cljc` に直しても、テストが呼ぶ `bl/canonical-json` は
   現行の `budget_ledger.cljc` に存在しない — 実際の関数名は `canonical-json-utf8`(かつ `defn-`
   private)。`record-cid`/`normalize-record`/`build-ledger`/`load-seed` 等、他の呼び出しも
   現行APIと1件ずつ突き合わせが必要(未実施)。
2. **`test_kotoba.clj`**: 同様に `(load-file "kotoba.clj")` → `kotoba.cljc` へのロードパス修正で
   起動はするが、末尾の "tamper located at the corrupted tx index" チェックで
   `(ko/verify-chain path)` の `:broken-at` が `nil` を返し `(>= nil 0)` が例外になる —
   ロードパスを直して初めて実行された結果、`kotoba.cljc` の tamper-detection ロジック自体に
   **未検証の潜在バグがある可能性**が判明(このテストは .cljc 移行後、一度も実行されていなかった)。
3. **`test_autorun.clj`**: 実体は存在せず `test_autorun.cljc` のみ。こちらは単純な rename では済まず、
   `(:require [danjo.methods.autorun :as autorun] [danjo.methods.kotoba :as kotoba])` という
   namespace-qualified require を使っており(他の sibling test は load-file 方式)、bb 実行時に
   classpath 上で `danjo/methods/autorun.cljc` を解決できず失敗する — `bb.edn`/`deps.edn` の
   `:paths` 整備か、他ファイルと同じ load-file 方式への変更が必要(未実施)。

**honest (G8)**: この3 suiteは now も dormant のまま — 今回のiterationでは "1項目" の範囲を
「壊れたテストを直す」から「壊れ方を正確に診断し、誤ったgreen主張をしない」に絞った。次のiteration
候補: (a) `budget_ledger.cljc` の現行public APIに合わせて `test_budget_ledger.clj` を書き直す、
(b) `kotoba.cljc` の `verify-chain`/`:broken-at` ロジックを個別に検証する、(c) `test_autorun.cljc`
の require を load-file 方式に統一するか `bb.edn` を追加する。いずれも本iterationの「1項目」原則を
超える(複数ファイル・複数根本原因)ため、意図的に見送った。

## イテレーション記録 (承前)

### worldwide fiscal-source catalog hardening (2026-06-02)
**WORLDWIDE fiscal-source 台帳の fail-closed 固定 + G14 検証ワークフロー文書化。**
既存の `registry/sources.seed.json`(166 件 / 34 distinct jurisdiction + 国際機関
[IMF / World Bank / OECD / UN / IATI / OGP] / sourceKind 6種: audit-institution /
budget-portal / intl-aggregator / legislature-record / open-spending /
procurement-system)に対し、sibling toritsugi 方式で 2 層の hardening を追加:
(1) `70-tools/scripts/audit/test_danjo_registry_seed.py`(**8 test, 全 green**)—
①JSON parse + `sources` 非空 ②`sourceId` 一意(重複で fail)③全件
`verificationStatus="unverified-seed"`(G14)④全件 https provenance + ISO-8601
`lastVerified` ⑤≥12 distinct jurisdiction(worldwide guard; JP-only 退行ガード)
⑥全件 `sourceKind` が allowed catalog set 内 ⑦全件 notes 非空 + 台帳が
NON-adjudicating / observational 境界を参照 ⑧top-level 整数 `freshnessWindowDays`。
test-only・network-free・cell 実行なし(R0 ceiling 不変)。
(2) `20-actors/danjo/registry/VERIFICATION.md` — G14 三層(unverified-seed →
maintainer-verified → council-verified)の人手チェックリスト。per-field 10項目 +
**per-jurisdiction official-domain provenance check**(.gov / .go.jp / .gouv.fr /
.gov.uk / europa.eu / .gob.* / .go.kr / 国際機関ドメイン、fail-closed)+
NON-adjudicating / observational 境界 re-check を明記。
**honest (G8)**: **検証済みソースは 0 件** — 全件 unverified-seed のまま。台帳は
既公開公式データの ingestion scaffold であり authoritative inventory ではない。
実際の verification 実行は R1(Council ratify + fiscal-source-verification
maintainer DID 登録後)。danjo finds + cross-references; kanae renders; neither
adjudicates。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
新設 `methods/test_charter_gates.cljc`(**7 tests green**)で manifest G1–G13 + 4 lexicon の非裁定ゲートを固定: G4 discrepancyObservation/oversightReport const nonAdjudicatingNotice=true + 全lexicon に verdict/accusation/guilt/ruling フィールド不在(censor's eye, never sword)/ G5 observation が sourceRecordCids + methodNoteCid、crossReferenceLink が basisRecordCids(≥2 source)/ G6 methodNote が definition+inputs+version / G11 publiclyNamedBasis={procurement-awardee, diet-member-on-record, budget-recipient, contracting-authority} / governance oversightReport が councilAttestations + councilReviewCid + oneSbtOneVoteChainCid。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `danjo.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
