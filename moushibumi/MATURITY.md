# moushibumi (申文) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで成熟度を上げ、ここに記録する。
honest framing: できていないことは「未」と明記する。

- Actor: `did:web:moushibumi.etzhayyim.com` · ADR-2605312400 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 · 提出/dispatch なし ·
  PII平文禁止(G6) · Murakumo-only(G7) · 行政書士法/UPL境界(G5) · G8 非捏造 ·
  G14 verified-procedure-only · G15 self-submit-default ·
  **G3 政治的中立(公選法-equivalent): 選挙項目は INFO-ONLY、推薦/評価/ランキング/GOTV 禁止** ·
  コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605312400 (master) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | participationTarget registry seed (JP 5件, unverified-seed) | ✅ | init |
| 4 | **registry の worldwide(多管轄)横展開** | ✅ | **iter-worldwide (2026-06-02)** |
| 5 | cell scaffold (`kotodama.cells.moushibumi_*`, import時 RuntimeError) | 未 | — |
| 6 | cell ↔ manifest 整合 invariants test | 未 | — |
| 7 | 憲法ゲート機械検証 node guard | 未 | — |
| 8 | seed の根拠法令・provenance 精査 + verification ワークフロー | 一部(iter-worldwide で出典付与) | — |
| 9 | murakumo fleet.toml への cell placement | R1延期 | — |

## イテレーション記録

### iter-worldwide (2026-06-02)
**上げた項目: #4 — participationTarget registry の worldwide 横展開。**
従来 JP-only(5件)だった `registry/targets.seed.json` を多管轄に拡張し、
既存 5 件を全て温存したまま **26 件を追加(合計 31 件)**。全 26 件は既存スキーマ
(`targetId` / `title` / `jurisdiction` / `channelKind` / `organ` / `channelType` /
`introducingMemberRequired` / `submissionForm` / `deadline` / `legalBasis` /
`language` / `provenance` / `lastVerified` / `verificationStatus` / `notes`、
任意 `portalUrl`)に正規化。研究入力の id→targetId、channel/authority/legalBasis/
provenance/notes を該当フィールドへ畳み込み、不明値は捏造せず汎用表現に留めた(G8)。

**カバー bloc / 管轄(追加 26 件)**:
- **US**(7): 連邦 Regulations.gov / Federal Register / Congress 請願連絡 /
  Vote.gov 選挙情報 + 州代表例 CA 住民発議 · NY 規則制定意見・請願 · TX Texas Register 意見
- **EU**(EU-level 3 + DE 1 + FR 3 = 7): European Citizens' Initiative · EP 請願 ·
  Have Your Say 協議 · Bundestag 公開請願 · Assemblée nationale 請願 ·
  Sénat e-請願 · France 公式選挙情報(中立リファレンス)
- **UK-CW**(8): UK Parliament e-petitions · GOV.UK consultations · Electoral
  Commission 選挙情報 · Canada ourcommons e-petitions · Australia APH e-petitions ·
  India 事前立法協議(MyGov)· Singapore REACH
- **INTL-ROW**(6): Brazil Senate e-Cidadania · Brazil Câmara e-Democracia ·
  Mexico Consulta Popular(citizen-initiated)· South Korea 국민동의청원 ·
  UN OHCHR Calls for Input

**全 26 件**: `verificationStatus=unverified-seed` · `lastVerified=2026-06-02T00:00:00Z` ·
言語コード付与(en/de/fr/pt/es/ko)· provenance は全件 https 公式ソース ·
notes に G3 境界文 "Political-neutrality (公選法-equivalent): INFO + procedure ONLY.
NO campaigning / endorsement / candidate ranking / GOTV targeting. Election entries
are neutral reference to OFFICIAL sources only." を明記。

**G8(非捏造 / honest coverage)**: 研究入力に EU European Citizens' Initiative が
2 重(EU bloc 版 `eu-european-citizens-initiative` と INTL-ROW 版
`intl-row-eu-european-citizens-initiative`)で含まれていたため、**INTL-ROW 重複版を
ドロップ**(同一公式チャネルの二重計上を回避、targetId dedup)。水増しより正直な
カバレッジを優先。

**検証**: JSON valid · targetId 重複なし(31/31 unique)· 全 26 新規が必須フィールド
(verificationStatus / lastVerified / language / provenance https / G3 境界文)を
具備。**R0 不変条件維持** — 全件 unverified-seed のまま、cell 実行/提出/dispatch なし。

**未(honest)**: cell scaffold(#5)/ invariants test(#6)/ node guard(#7)/
fleet placement(#9)は未着手。非 JP 26 件の lastVerified は 2026-06-02 の best-effort
公開リファレンスであり、多くの notes に DRIFT WARNING(閾値・条文番号・締切は管轄ごとに
ドリフトしうる)を付記済み。confidence medium の項目(TX 条文 / FR 仏議会条文 /
KR 国会法条文 / BR Câmara Ato da Mesa)は live use 前に再確認が必要。
verification(unverified-seed → maintainer/council-verified への昇格)は R1 + maintainer
DID 登録後の作業で、現 R0 では実行しない。

- 2026-06-02: fail-closed registry invariants test を追加 — `70-tools/scripts/audit/test_moushibumi_registry_seed.py`(7 関数: JSON/非空 targets · targetId 一意 · 全件 unverified-seed(G14) · provenance https + lastVerified · 多管轄カバレッジ ≥5(現在 14) · notes 境界 caveat + 公選法中立 regime(G3) · freshnessWindowDays int)。network-free・cell 非実行の R0-safe。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest .../test_moushibumi_registry_seed.py -q` で 7 passed green(plain 実行は環境側の langsmith/pydantic plugin 破損で collection 失敗するため autoload 無効化で回避; 当 test 自体の問題ではない)。チェックリスト #6(invariants test)着手。

- 2026-06-02: long-tail worldwide deepening — 4 新 bucket（EU-REST: FI/NL/IE/CH/AT/ES/PT · ASIA-REST: TW/ID/PH/TH/HK/CN/MY · AMERICAS-REST: AR/CL/CO/PE/CA-BC · MEA-OCEANIA: NZ/ZA/IL/KE/NG/AE）から **27 件を追加（合計 58 件）**。既存 31 件は全て温存。重複の eu-wide European Citizens' Initiative はドロップ（既存 `eu-european-citizens-initiative` と同一公式チャネル、targetId/管轄+slug dedup）。全 27 件を既存スキーマに正規化（id→targetId · channel→submissionForm/portalUrl · authority→organ · channelKind/channelType/introducingMemberRequired を内容から導出、捏造せず；MY/CA-BC は MP/MLA 経由のため introducingMemberRequired=true）。全件 `verificationStatus=unverified-seed` · `lastVerified=2026-06-02T00:00:00Z` · 言語コード（fi/nl/en/de/es/pt/zh/id/th/ms/he/ar）· https provenance · notes に G3 境界文（公選法-equivalent）を付与。distinct jurisdiction = **38**（従来 14 → 38）。invariants test の多管轄しきい値を `>= 5` → `>= 12` に引き上げ（実測 38 が十分上回るため 12 で固定）；`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest .../test_moushibumi_registry_seed.py -q` → **7 passed green**。R0 不変条件維持（cell 非実行 / 提出 dispatch なし）。honest（G8）: SA/EG は federal レベルで検証可能な公開民主参加チャネルが確認できず、水増しせず除外。

- 2026-06-02: lexicon ↔ worldwide seed reconciliation — `00-contracts/lexicons/com/etzhayyim/moushibumi/participationTarget.json` を EXTENDED（additive のみ、R0 permissive、`additionalProperties:false` なし／required 不変／既存 field 削除・改名なし）: `channelKind.knownValues` に `citizen-initiative` 追加（petition/public-comment/election-info/citizen-initiative の4種で seed 全件カバー）、`channelType.knownValues` に `web-form-or-postal-or-phone` / `web-portal-via-mp` / `web-portal-with-signature-collection` 追加、`jurisdiction` maxLength 8→16（eu-wide/intl-un 等）、`organ` 300→400、`legalBasis` 300→600 に拡張（多管轄 legal basis 文字列を収容）。description に UPL / 政治的中立(G3) / informational-only / zero-compensation・non-partisan(G9) / citizen-initiative は wayfinding-only の憲法境界を明記。seed 全14件が props 存在・maxLength・knownValues に conform することを確認。validators green: `lexicon-primary-types.mjs` OK(17 files) / `nsid-lexicon-exists.mjs` OK。`lexicon-const-name-collision-check.mjs` は当 file と無関係の既存 collision（`com.etzhayyim.apps.ipaddress.analyzeIp`）で fail — 当 file を stash した状態でも同一に fail するため当変更が原因ではない（unrelated、修正せず honest report）。

**2026-06-02 R1 participation-window core (gate closed)**: `kotodama.cells.moushibumi_status_track/window.py` 純コア — 募集ウィンドウの開閉判定(open_date+window_days or 明示 close_date の2入力モード、inclusive/exclusive 暦)。G5(is_legal_opinion)+ G9(renders_advice / 公選法中立)を常に False でコード担保。**敵対的検証が pre-open バグを捕捉**(as_of<open_date で is_open=True 誤報)→ `not_yet_open` 追加 + is_open を [open,close] 範囲に修正 + 回帰テスト4件追加。`test_window.py` green。cell.py ゲート閉維持。

**2026-06-02 R1 opportunity resolver (gate closed)**: `moushibumi_opportunity_match/opportunity_resolver.py` — 管轄(+任意 channelKind petition/public-comment/citizen-initiative/election-info)→ 参加チャネルの純 registry クエリ(confidence→title→organ 決定論ソート=partisan/relevance スコア無し G3、未知管轄→空)。is_legal_opinion AND renders_advice を常に False、record に politicallyNeutral=True・officialSourcesOnly=True・isEligibilityDetermination=False を assert。`test_opportunity_resolver.py` green(全体 163 passed)。cell.py ゲート閉維持。注: ワークフローの構造化出力返却は失敗したがファイルは健全で、手動レビューにより sound 確認。moushibumi は window+opportunity の2コア体制。

- 2026-06-02: registry verification ワークフロー doc を authored — `20-actors/moushibumi/registry/VERIFICATION.md`(toritsugi-parity、複数台帳が「未」と記録していた項目#8の verification ワークフロー部分)。3-tier(unverified-seed → maintainer-verified=member self-submit / council-verified=gated 代行 `moushibumi_submit`)・実フィールド名(`targetId`/`title`/`jurisdiction`/`channelKind`/`organ`/`channelType`/`introducingMemberRequired`/`portalUrl`/`submissionForm`/`deadline`/`legalBasis`/`language`/`provenance`/`lastVerified`/`notes`)16項チェックリスト・WORLDWIDE per-jurisdiction provenance(.go.jp/.gov/.gouv.fr/.gov.uk/europa.eu/.gob.*/.go.kr/官製ドメイン、third-party blog/aggregator 不可、fail-closed)・freshnessWindowDays 180 staleness rule・G3 政治的中立(公選法-equivalent、election-info は公式選挙管理機関のみ、introducingMemberRequired は member が紹介議員確保・moushibumi は lobbying せず)を first-class 記載・machine-enforced floor として `test_moushibumi_registry_seed.py` を引用。R0 honest: 検証済みエントリは皆無、全件 unverified-seed のまま、execution は R1。チェックリスト #8 の verification-workflow 部分着手(seed 精査自体は依然 best-effort で未完)。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
既存 registry-seed テストが被覆していなかった **manifest G1–G15 + 6 lexicon の民主参加ゲート**を新設 `methods/test_charter_gates.cljc`(**6 tests green**, standalone・network-free)で固定: (1) manifest が厳密に G1–G15。(2) **drafting-assist のみ** — voiceDraft.assistMode={drafting-assist} + memberConfirmed + encryptedDraftRef(G6)。(3) **self-submit default** — submission が consentRef 必須、mode={member-self-submit, agent-on-behalf}(代行は R3 ゲート)。(4) **G14** — participationTarget が legalBasis + provenance + verificationStatus{unverified-seed/maintainer-verified/council-verified} + organ 必須。(5) **G3 political neutrality** — channelKind={petition, public-comment, election-info}(選挙は公式情報への中立ポインタのみ、campaigning/GOTV/endorsement 不在)。(6) **G4 own-voice** — 全稼働 record が memberDid 必須。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `moushibumi.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
