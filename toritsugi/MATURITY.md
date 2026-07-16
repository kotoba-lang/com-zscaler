# toritsugi (取次) — Maturity Ledger

`/loop` (30分毎) の進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに
記録する。honest framing: できていないことは「未」と明記する。

- Actor: `did:web:toritsugi.etzhayyim.com` · ADR-2605312030 · **R0 scaffold → R1 技術ビルド完了 (ratify-pending, 2026-07-09)**
- 不変条件(全イテレーション厳守): R0 では cell 非実行(import時 RuntimeError) ·
  提出/dispatch なし · PII平文禁止(G6) · Murakumo-only(G7) · 行政書士法/UPL境界(G5) ·
  G8 非捏造 · G14 verified-procedure-only · G15 self-submit-default · コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605312030 (master) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | 6 Lexicons (`com.etzhayyim.toritsugi.*`) | ✅ | init |
| 4 | procedure registry seed (6件, unverified-seed) | ✅ | init |
| 5 | registry 更新 (root CLAUDE.md / adr README / deps.toml) | ✅ | init |
| 6 | **7 cell scaffold** (`kotodama.cells.toritsugi_*`, import時 RuntimeError) | ✅ | **iter-1** |
| 7 | cell ↔ manifest 整合 invariants test (`70-tools/scripts/audit/test_toritsugi_invariants.py`) | ✅ | **iter-2** |
| 8 | 憲法ゲート G1–G15 を機械検証する node guard (`70-tools/scripts/lint/toritsugi-procedure-gates.mjs`) | ✅ | **iter-5** |
| 9 | chigiri / himotoki / toritate manifest への toritsugi cross-actor boundary 追記 | ✅ | **iter-3** |
| 10 | procedure seed の根拠法令・provenance 精査 + 出典URL検証ワークフロー | ✅ | **iter-7** |
| 11 | kotoba KG seed への toritsugi エンティティ追加 | 不可(node-local) | iter-4 調査 |
| 12 | lexicon validator (nsid-lexicon-exists / lexicon-primary-types) green 確認の固定化 | ✅ | **iter-6** |
| 13 | 各 cell dir の README.md (tsukuroi パターン parity) | ✅ | **iter-4** |
| 14 | murakumo fleet.toml への toritsugi cell placement (10ノード) | R0では時期尚早(R1延期) | iter-8 調査 |
| 15 | procedure registry の自治体横展開設計 (1,700+ 市区町村 curation 方針) | ✅ | **iter-9** |
| 16 | procedure registry の **worldwide 多管轄展開** (US/EU/UK-CW/INTL-ROW; 全件 unverified-seed) | ✅ | **iter-10** |

## イテレーション記録

### iter-1 (2026-05-31)
**上げた項目: #6 — 7 cell scaffold。** `40-engine/kotoba/crates/kotoba-kotodama/cells/toritsugi_*/cell.py` を
tsukuroi/himotoki パターンに合わせて7本作成(procedure_registry / eligibility_match /
intake は reuben、guide / draft は gad、submit / status_track は naphtali)。各 cell は
R1 activation gate の定数(全て `None`)により **import時に `RuntimeError("toritsugi R0
scaffold: …")`** を送出する。憲法ゲートを docstring + gate 定数に反映: procedure_registry=G8/G14、
eligibility_match=G3/G5/G12、intake=G3/G4、guide=G5/G8、draft=G5/G6/G8、submit=G10/G14/G15
(+ `DAIKOU_R3_GATE_TX` で代行を構造的に二重ゲート)、status_track=G6/G11。検証: 7/7 が
正しい例外を送出(直接 import で確認)。
**次の候補: #7** — `test_toritsugi_invariants.py`(7 cell が import時 raise / manifest が
15 gates + 6 lexiconNamespaces を disk と一致 / lexicon に float(`type: number`)無し /
applicationDraft.assistMode が input-assist のみ / submissionRecord.mode 既定 self-submit
を pin)。tsukuroi の `test_tsukuroi_invariants.py` を雛形にする。

### iter-2 (2026-05-31)
**上げた項目: #7 — 憲法 invariants ロックイン test。** `70-tools/scripts/audit/test_toritsugi_invariants.py`
を tsukuroi 雛形から作成、**9 test 全 pass**(`python3 -m pytest`)。pin した不変条件:
(1) 7 cell が import時 `RuntimeError("…R0 scaffold…")` を送出 / (2) manifest が gates 厳密に
G1..G15・lexiconNamespaces 6本(disk 一致)・cells 7本(module path → disk dir 一致) /
(3) lexicon に float(`type: number`)無し / (4) G8+G14: `procedure` が legalBasis+provenance+
verificationStatus を required、verificationStatus knownValues が3 tier ちょうど / (5) G5:
`applicationDraft.assistMode` が `["input-assist"]` のみ(作成代理 表現不能)+ memberConfirmed
required / (6) G15: `submissionRecord.mode` required・{member-self-submit, agent-on-behalf}・
先頭=self-submit・councilGateRef 存在 / (7) G14+G8: seed 全件 unverified-seed かつ
legalBasis+provenance 明示 / (8) G15: submit cell が `DAIKOU_R3_GATE_TX: str | None = None`
を pin(代行の構造的二重ゲート) / (9) G6: PII保持 record(draft/benefitMatch/status/submission)
が暗号ポインタのみ露出・inline 平文フィールド無し。これで以後のリファクタが憲法不変条件を
silent に弱められない。
**次の候補: #9** — chigiri / himotoki / toritate manifest への toritsugi cross-actor boundary
追記(双方向 boundary の明示。toritsugi 側 manifest には既に記載済みなので、相手側 3 manifest の
`crossActorBoundary` に toritsugi エントリを足し、整合を test で固定するか手動確認)。または #8
(G1-G15 guard を covenant-transparency guard 方式で機械検証)も候補。

### iter-3 (2026-05-31)
**上げた項目: #9 — 相手側 3 manifest への双方向 cross-actor boundary 追記。** toritsugi 側 manifest
には既に 3 actor への boundary が記載済みだったので、相手側を補完して双方向化:
(a) **chigiri** — `crossActorProcedure` list 末尾に toritsugi 追加(toritsugi が chigiri の
procedure template を PULL、chigiri が form+legal characterization+作成代理+appeal を所有、toritsugi
は案内+draft-assist+(gated)submit で **助言しない** G5 行政書士法/UPL 境界)。(b) **himotoki** —
`crossActorBoundary` list に sibling 追加(himotoki=開示請求 data OUT、toritsugi=申請/届出 member
INTO a procedure、共有 dispatch discipline・disjoint purpose)。(c) **toritate** —
`crossActorBoundary` list に税務境界追加(toritsugi は確定申告/e-Tax の **操作案内のみ**、税務
characterization と税理士法-reserved 税務代理/税務書類作成 は toritate+有資格税理士へ route、
toritsugi G5/N13)。検証: 4 manifest 全て JSON valid、各相手 manifest に `toritsugi.etzhayyim.com`
1件ずつ、toritsugi invariants 9 passed 維持。**注(honest)**: actor ごとに boundary キー名が
異なる(toritate/himotoki/toritsugi = `crossActorBoundary` list、chigiri = `crossActorData`+
`crossActorProcedure` list)。将来 cross-actor 整合を test で固定するなら、このキー名差異を吸収する
必要がある — 現状は手動確認に留める(未自動化)。
**次の候補: #11(kotoba KG seed への toritsugi エンティティ追加)** — 既存 KG seed
(`kg-seed-v1.ndjson` 等、ADR-2605301030 参照)に tier-b-actor として toritsugi を他 28 actor と
同形で1行足す。まず seed ファイルの所在とスキーマ(エンティティ行の形)確認から。または #8
(G1-G15 guard 機械検証)、#13(各 cell dir の README parity)。

### iter-4 (2026-05-31)
**調査: #11 はリポジトリでは実施不可と判定 → #13 に切替。** #11 を着手しようと KG seed の
所在を調べたところ、(a) `kg-seed-v1.ndjson` / `rehydrate.sh`(ADR-2605301030 の SoT)は
**リポジトリに存在しない** — ライブ kotoba ノード上の node-local 成果物であり git 外。
(b) リポジトリ内の唯一の KG ファイル `90-docs/_registry/kotoba-quads.ndjson`(6,564 quads、
5/28 生成)は ADR-2605281700/281800 の **自動 content-addressed projection** で、手編集すると
再生成で上書きされる。よって #11 はコミット可能な対象が無く **不可(node-local)** と記録し、
チェックリストを更新。ライブノードへの反映は別途 `kg.ingest_batch` の運用作業(本 loop の
スコープ外)。
**上げた項目: #13 — 7 cell dir の README.md(tsukuroi parity)。** `40-engine/kotoba/crates/kotoba-kotodama/cells/
toritsugi_*/README.md` を tsukuroi 形式で7本作成(1行サマリ + Actor/Murakumo node/Gates/
Output Lexicon/Ceiling)。各 README は対応 cell.py の docstring と憲法ゲートを反映:
procedure_registry=G8/G14・eligibility_match=G3/G5/G12・intake=G3/G4・guide=G5/G8・draft=G5/G6/G8・
submit=G10/G14/G15(代行は `DAIKOU_R3_GATE_TX` で構造的二重ゲートと明記)・status_track=G6/G11。
検証: 7 cell 全てに cell.py + README.md が揃う(parity 0 欠落)、toritsugi invariants 9 passed 維持。
**次の候補: #8(G1-G15 guard 機械検証)** — covenant-transparency guard 方式で、憲法ゲートの
const/構造を CI で fail-closed に固定(invariants test と相補。test は pytest 依存、guard は
node スタンドアロンで lefthook 統合可)。または #12(lexicon validator green の CI 固定化)、
#10(procedure seed の根拠法令・出典URL 精査 + verification ワークフロー)。

### iter-5 (2026-05-31)
**上げた項目: #8 — G1–G15 を機械検証する node スタンドアロン guard。**
`70-tools/scripts/lint/toritsugi-procedure-gates.mjs` を作成(sibling の
`transparency-floor-and-gate.mjs` 方式に倣う)。pytest 非依存・node 単体で動くので
lefthook/CI に fail-closed で組み込み可能。6 check:(A) G5 — `applicationDraft.assistMode`
が厳密に `["input-assist"]`(作成代理 表現不能)+ memberConfirmed required /(B) G15 —
`submissionRecord.mode` required・{member-self-submit, agent-on-behalf}・先頭 self-submit・
councilGateRef 存在 /(C) G8+G14 — `procedure` が legalBasis+provenance+verificationStatus
required、verificationStatus が3 tier /(D) G6 — PII 4 record が暗号ポインタのみ・inline 平文
禁止 /(E) Lexicon v1 — float 無し /(F) G15 — submit cell が `DAIKOU_R3_GATE_TX: str | None = None`
を pin。検証: 実 repo で **clean(exit 0)**、negative test(assistMode に「作成代理」混入)で
**正しく exit 1 + 該当 violation 表示**。これで invariants test(pytest, import-raise 含む)と
guard(node, CI fail-closed)の二層で憲法不変条件が固定された。
**注(honest)**: `lefthook.yml` は pre-commit に複数 lint `.mjs`(substrate-boundary /
no-advertising / no-cookie / charter-rider-notice 等)を配線済み。ただし本 guard と sibling の
transparency-floor-and-gate guard はまだ lefthook commands に **未配線**(現状 ad-hoc/CI 実行)。
配線は将来の lint-suite wiring で実施予定(本 loop では guard 本体の作成+検証に留め、過剰配線は
避けた)。
**次の候補: #10(procedure seed の根拠法令・出典URL 精査 + verification ワークフロー設計)**
または #12(lexicon validator green の CI 固定化)。#14(murakumo fleet.toml への cell placement)は
fleet 構成の理解が要るので後回し。

### iter-6 (2026-05-31)
**上げた項目: #12 — lexicon validator green の固定化。** まず repo の 2 validator を実行し現状を
確認:`lexicon-primary-types: OK (17 files)`、`nsid-lexicon-exists: OK (5 static refs, 6723
lexicons)`、両 exit 0。toritsugi 6 lexicon の id↔namespace 整合も全 OK(手動 python 確認)。
ただし「手動確認」では drift を防げないので、invariants test に **3b ケースを追加**:6 lexicon
全てについて `lexicon==1` / `id == com.etzhayyim.toritsugi.{stem}` / `defs.main.type=="record"` /
`record.properties` が非空、を pin(tsukuroi の `test_each_lexicon_id_matches_namespace` 相当で、
toritsugi では欠けていた)。これで id rename や record def の脱落が validator 待ちでなく test で
即 fail する。検証: invariants **10 passed**(従来 9 + 新 1)。**注(honest)**: repo 全体の
`lexicon-const-name-collision-check` は **既存の** `com.etzhayyim.apps.ipaddress.analyzeIp` 衝突で
fail し続けるが、これは toritsugi と無関係(init 時から既知)。toritsugi 由来の const 衝突は無い。
**次の候補: #10(procedure seed 根拠法令・出典URL 精査 + verification ワークフロー設計)** —
seed 6件の legalBasis / provenance を再点検し、verification 手順(unverified-seed →
maintainer-verified の人手チェック項目)を文書化。または #15(自治体横展開の curation 方針)、
#14(fleet.toml cell placement)。

### iter-7 (2026-05-31)
**上げた項目: #10 — procedure seed 精査 + verification ワークフロー文書化。**
(a) seed 6件を再点検:全件 legalBasis + provenance + verificationStatus を保持、provenance は
全て `.go.jp` 公式ソース、全件 unverified-seed。根拠法令の条文番号を確認(住民基本台帳法 §12
[写し交付] / §22 [転入届] · 戸籍法 §49 [出生届] · 番号法 §17 [個人番号カード交付] · 児童手当法
§7 [認定請求] · 所得税法 §120 [確定申告])— いずれも正確。(b) **verification ワークフローを文書化**:
`20-actors/toritsugi/registry/VERIFICATION.md` を新設。3 tier(unverified-seed →
maintainer-verified → council-verified)の意味・flip 権限・unlock、freshnessWindowDays(180)の
staleness 規則、unverified-seed → maintainer-verified の **10項目人手チェックリスト**(title/所管/
根拠法令/channel+URL/必要書類/手数料/法定処理期間/provenance公式性[fail-closed]/lastVerified/
行政書士法-UPL再確認)、代行 eligibility の Council 追加要件を記述。(c) seed honesty test を強化:
`test_seed_all_unverified_and_cited` に **provenance は https かつ .go.jp 公式ソース必須**の assert
を追加(第三者ブログ/アグリゲータを fail-closed で排除)。検証: invariants **10 passed**、guard
clean、seed JSON OK。R0 不変条件維持(verificationStatus は全件 unverified-seed のまま — 実際の
verification 実行は R1 + maintainer DID 登録後)。
**次の候補: #15(自治体横展開 curation 方針)** か **#14(fleet.toml cell placement)**。残り未着手は
#14・#15 のみ(#11 は node-local で不可と確定済)。#15 は設計文書、#14 は fleet 構成理解が要る。

### iter-8 (2026-05-31)
**調査: #14 は R0 では時期尚早と判定(R1 延期)。** `50-infra/murakumo/fleet.toml` を精査した結果、
2点の事実が判明:(a) deployed node は naphtali/simeon/judah/zebulun/levi/joseph/issachar/dan/
benjamin/asher/gad(+jacob 制御)の11+1で、**reuben は存在しない**。toritsugi cells(と sibling の
himotoki manifest)が murakumoNode に挙げる "reuben" は未デプロイの 12-tribe 名。(b) fleet.toml が
配置しているのは ADR-2605192415 の **15 個の live 憲法層 governance cell のみ**(Charter*/Land*/
Tithe*/Eligibility/Treasury/PublicFund/Adherent*/CouncilLevel*/CouncilDeliberation/Force*/
EthicsContentClassifier)。**himotoki/danjo/tadori/tsukuroi/toritate/chigiri などの R0 tier-b actor
cell は fleet.toml に1つも載っていない** — これらは R1 activation 時に追加される。よって今 toritsugi
cells を fleet.toml に置くのは(i)実行不能な import-RuntimeError scaffold を live インフラ設定に入れ、
(ii)未デプロイの reuben を参照し、(iii)他 R0 tier-b actor の慣行に反する — いずれも R0 ceiling に
反する。#11 と同様 **時期尚早として記録のみ**、fleet.toml は編集しない(ブラインド編集回避)。R1 で
toritsugi 7 cell を fleet.toml に追加する際は、reuben→実ノード(asher が "replica + failover
(any cell)" 兼用なので overflow 先候補)の再マッピングが必要 = R1 の TODO。fleet 全体を確認済:
node は naphtali/simeon/judah/zebulun/levi/joseph/issachar/dan/benjamin/asher(10 tribe)+
etzhayyim-murakumo(meta)+ evo-x2(推論バックエンド)、`[cells.*]` catalog は 15 governance cell の
healthz_port/trigger 設定のみ、`[bootstrap].roadmap` も S0–S11 で governance cell + unispsc のみ —
tier-b actor cell の deploy step は無い。
**次の候補: #15(procedure registry の自治体横展開 curation 方針)** — 唯一の純設計文書タスクで
fleet/インフラ非依存。1,700+ 市区町村 × 手続きの scaling 方針(per-自治体 resolve 戦略・seed の
階層化・verification 負荷分散)を `registry/SCALING.md` 等に文書化。残り実質未着手は #15 のみ
(#11/#14 は R0 不可/延期と確定、他は完了)。

### iter-9 (2026-05-31)
**上げた項目: #15 — 自治体横展開 curation 方針(設計文書)。** `20-actors/toritsugi/registry/
SCALING.md` を新設。核心は **2-tier registry 設計**:(1) national `procedure`(現行 lexicon、
根拠法令/法定期間/書類type/channel kind、`authority`=generic のまま)+(2) FUTURE
`municipalBinding`(R2+、未作成。procedure×自治体 の concrete 窓口/様式CID/手数料/online URL、
全国地方公共団体コード keyed、sparse)。flat denormalize(6×1,741≒10,000 行)を避け、layer 境界で
split。curation は **demand-driven(member が必要な自治体のみ binding 作成、bulk crawl 禁止 = G12/
himotoki anti-mass-enum 整合)・template-first(binding 0 でも national template + 「要確認」で
graceful degrade、G8 で窓口を捏造しない)・verification は VERIFICATION.md の10項目継承(自治体は
`.lg.jp` 公式)・PII は binding に入れない(binding は OPEN 手続きデータ)**。スケール梃子として
**マイナポータル ぴったりサービス**(オンライン申請可否の national 正規化ソース)を read-only pivot に
使い 1,741 サイト crawl を回避(G14 maintainer-verified ゲートは維持)。**未(honest)**:
`municipalBinding` lexicon は未作成(R2+。今作ると未使用 schema になる)・自治体コード表/ぴったり
サービス連携/binding 実体ゼロ・provenance allow-list への `.lg.jp` 追加は binding lexicon と同時に
実施(seed は national `.go.jp` のみ)。設計のみ・JSON/lexicon 変更なし。検証: invariants 10 passed・
guard clean(既存成果に影響なし)。

### iter-10 (2026-06-02)
**上げた項目: #16 — procedure registry の worldwide 多管轄展開。** `registry/procedures.seed.json`
を JP のみ(6件)から **worldwide 多管轄(34件)** へ拡張。既存6件は一字一句そのまま保持し、
新規 **28件** を既存スキーマ(`procedureId` / `title` / `jurisdiction` / `regime` / `authority` /
`channelType` / `onlineUrl` / `requiredDocuments` / `formRef` / `legalBasis` / `language` /
`provenance` / `lastVerified` / `verificationStatus` / `notes`)に正規化してマージ。新ブロック:
- **US(7)**: SSN(SS-5)・旅券(DS-11)・連邦所得税(1040)・REAL ID 運転免許・有権者登録・
  SNAP・Medicaid/Marketplace。CA/NY/TX の state variation を notes に明記(州別税・州別 DMV・
  州別取引所等)。
- **EU(7)**: Single Digital Gateway / Your Europe・EHIC・独 Anmeldung・独 Personalausweis・
  仏 CNI・仏 déclaration des revenus(2042)・GDPR DSAR。
- **UK-CW(7)**: 英 NINO・英旅券・加 SIN・豪 TFN・印 Aadhaar・星 NRIC・星旅券。
- **INTL-ROW(7)**: 伯 CPF・伯 Título de Eleitor・墨 CURP・墨 INE 在外投票・韓 ARC(외국인등록)・
  韓 연말정산・欧州人権裁判所(ECHR)個人申立。

通貨手数料は管轄別フィールド(`feeUsd`/`feeEur`/`feeGbp`/`feeCad`/`feeAud`/`feeInr`/`feeSgd`/
`feeBrl`/`feeMxn`/`feeKrw`)で表現し、不明な額は捏造せず `null`(G8 非捏造)。**全28件が
`verificationStatus="unverified-seed"`・`lastVerified="2026-06-02T00:00:00Z"`・適切な
language code・https provenance(各管轄の公式一次ソース)・notes に行政書士法/UPL-equivalent
境界キャベアト("information + wayfinding + form-fill assist ONLY; never advice, never 作成代理.
Member self-submits.")を含む**。各管轄の reserved-practice 境界も notes に追記(US paid-preparer
PTIN・独 RDG・仏 monopole du droit・韓 행정사/세무사・ECHR は qualified counsel 強推奨)。
検証: `procedureId` で dedup 済(34 unique, 0 dupe)・JSON valid・新28件の field assert 全 pass。
**注(honest)**: 全件 unverified-seed のため G14 により live submission 不可。fee/deadline/channel
は多管轄ドリフトが大きく、各 entry の DRIFT WARNING に明記。`regime`/`fee*` の新値・通貨フィールドは
既存 lexicon の strict validation 対象になりうる(seed は `$schema` 参照のみで lexicon record と
1:1 ではない)— R1 で lexicon を多管轄対応させる際に整合確認が必要(本 iter ではデータ拡張のみ、
lexicon/cell 変更なし)。
**次の候補**: R1 ゲート(Council ratify)待ち。多管轄 lexicon 拡張(`regime` knownValues・通貨手数料
ユニオン)・per-jurisdiction provenance allow-list(`.gov`/`.gouv.fr`/`.gov.uk`/`europa.eu` 等)の
VERIFICATION.md 反映が R1 の TODO。

### iter-11 (2026-06-02)
**fail-closed registry invariants test を追加(緑）。** worldwide seed registry 専用の
`70-tools/scripts/audit/test_toritsugi_registry_seed.py`(7 test)を新設し、`procedures.seed.json`
の憲法不変条件を fail-closed に固定: ①JSON parse + `procedures` 非空 ②`procedureId` 一意(重複で fail）
③全件 `verificationStatus="unverified-seed"`(G14)④全件 https provenance + ISO-8601 `lastVerified`
⑤多管轄(≥5 distinct jurisdiction; JP-only 退行ガード)⑥全件 notes 非空 + 行政書士法/UPL 境界 regime 参照
⑦top-level `freshnessWindowDays` 整数。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest …` で
**7 passed**(test-only・network-free・cell 実行なし、R0 ceiling 不変）。既存 `test_toritsugi_invariants.py`
の seed honesty は `.go.jp` 固定で JP-only 前提のため、worldwide データの seed 不変条件は本 sibling suite が担う。

---

## R0 phase 総括(iter-9 時点)

リポジトリ内で R0 として積める成熟度は **ほぼ出尽くした**。チェックリスト15項目の状態:
- ✅ **完了 12**: #1–10, #12, #13(ADR/manifest/README/CLAUDE.md/6 lexicon/seed/registry 更新/
  7 cell scaffold/invariants test[10]/node guard[6 check]/双方向 cross-actor boundary/seed 精査+
  VERIFICATION.md/lexicon validator green 固定/cell README parity/自治体 SCALING.md)
- 🚫 **R0不可・延期 3**: #11(kotoba KG seed=node-local、git外)・#14(fleet.toml=live インフラ、
  reuben 未デプロイ+他 R0 tier-b actor も未登録のため R1 延期)
- 二層の機械検証(pytest invariants 10 + node guard 6 check)+ 2 運用文書(VERIFICATION/SCALING)で
  憲法不変条件は fail-closed に固定済み。
**これ以降の成熟度向上は R1 ゲート(Council Lv6+ ≥3 ratify、Bootstrap Council Seat 2-5 RFP 2026-06-19
クローズ後)を待つ** — cell 実行/提出/dispatch/fleet 配置/municipalBinding は全て R1+ の作業で、
R0 ceiling(import-RuntimeError・no dispatch・PII平文禁止)を破らずには進められない。次回 loop 以降は
既存成果の green 維持確認 + 文書の軽微改善に限定し、過剰実装(未使用 schema 等)は避ける。

- 2026-06-02 lexicon reconciliation: EXTENDED `com.etzhayyim.toritsugi.procedure` to cover the worldwide seed (`registry/procedures.seed.json`) — added per-currency authority-fee fields (feeUsd/feeEur/feeGbp/feeCad/feeAud/feeInr/feeSgd/feeBrl/feeMxn/feeKrw) + `confidence`, and extended `regime` knownValues to the JP/US/EU/DE/FR/UK/CA/AU/IN/SG/BR/MX/KR + CoE set. Additive/backward-compatible/permissive only (no additionalProperties:false, no new required, UPL / political-neutrality / informational-only / zero-toritsugi-fee boundary preserved in descriptions). Validators green: lexicon-primary-types ✅, nsid-lexicon-exists ✅; lexicon-const-name-collision FAILS on a pre-existing unrelated collision (`com.etzhayyim.apps.ipaddress.analyzeIp`), confirmed identical with this edit stashed — not caused by this file.

- 2026-06-02 long-tail worldwide deepening: merged 32 long-tail entries into `registry/procedures.seed.json` (34 → 66) across 4 buckets — EU-REST (SE/NL/ES/PL/IT/IE/CH/DK), ASIA-REST (CN/TW/HK/TH/ID/PH/VN/MY), AMERICAS-REST (AR/CL/CO/PE), MEA-OCEANIA (AE/SA/IL/ZA/NG/KE/EG/NZ): resident/address & population registration, national-ID/civil-status, social-security identifiers, voter registration, passport, income-tax filing. Distinct jurisdictions 13 → 41. Every new entry ships verificationStatus=unverified-seed + https provenance + language code + 行政書士法 / UPL boundary caveat; medium-confidence/in-flux entries flagged UNVERIFIED-for-live-use; requiredDocuments left as resolve-at-guide-time (not fabricated). Invariants test `70-tools/scripts/audit/test_toritsugi_registry_seed.py` distinct-jurisdiction threshold raised 5 → 12; all 7 tests green.

**2026-06-02 R1 filing-deadline core (gate closed)**: `kotodama.cells.toritsugi_status_track/deadline.py` 純コア — 法定届出期限の決定論計算(window は verified/member-confirmed INPUT、暦/営業日 counting)。行政書士法/UPL + G5 を docstring/コードで担保、is_legal_opinion 常に False。敵対的検証の指摘を反映: 統合テストの概念混同(`statutoryProcessingDays`=当局処理時間 ≠ 届出 window)を修正し member-confirmed INPUT へ、bool-as-int 拒否を追加。`test_deadline.py` green。cell.py ゲート閉維持。

**2026-06-05 coverage深化 + 陳腐化テスト reconciliation (loop iter)**: (1) **カバレッジ向上** — 旅券(passport)申請を「国民ID/戸籍系 1件のみ」だった 26 法域へ第2手続きとして追加 (`registry/procedures.seed.json` 66 → 92 entries; 単一手続き法域 27 → 1[nzl は既存])。旅券は全世界で比較可能な普遍手続き; 公式当局ドメインを確信できるもののみ採用し、**手数料・statutoryProcessingDays・条文番号は捏造せず null/省略**(G8; notes に DRIFT WARNING + guide-time resolve)、requiredDocuments は普遍 honest セット + 「resolve at guide time / not fabricated here」。全件 verificationStatus=unverified-seed(G14) + https provenance + UPL/行政書士法 境界注記。distinct jurisdictions 41 維持。sibling invariants suite `test_toritsugi_registry_seed.py` **7/7 green**。 (2) **成熟度向上(red test 解消)** — `test_toritsugi_invariants.py::test_seed_all_unverified_and_cited` は HEAD 時点で既に赤(2026-06-02 worldwide 化で `.go.jp`固定 + legalBasis 全件必須が陳腐化、非 .go.jp 60 件で fail)。MATURITY 記載の責任分担(worldwide データ不変条件は sibling suite が担う)に従い**外科的に scope**: 全件には普遍チェック(unverified-seed/https/provenance非空)、厳格な `.go.jp`公式ドメイン + legalBasis 必須は **JP backbone 行に限定**(worldwide 行で公式ドメイン heuristic は canada.ca/government.nl/borger.dk 等を誤判定し、国別条文の捏造は G8 違反のため)。loosen ではなく scope。両 suite **17/17 green**。

**2026-06-05 cross-actor parity (loop iter)**: chigiri が法律扶助でカバーするが toritsugi に欠けていた 6 法域(aut/bel/fin/grc/nor/prt)へ旅券申請手続きを実在公式当局で追加(Austria oesterreich.gv.at / Belgium FPS Foreign Affairs / Finland Poliisi / Greece Hellenic Police / Norway politiet / Portugal IRN-ePortugal)。手続き 92 → 98、distinct jurisdictions **41 → 47**。iter-1 と同一の honest 規律(fee/条文 null・DRIFT warning・requiredDocuments guide-time resolve・unverified-seed・https・UPL 境界)。両 suite **17/17 green**。結果: toritsugi(行政手続き)と chigiri(法律扶助)が**完全に同一の 47 法域**をカバー(toritsugi-only=0 / chigiri-only=0)。

**2026-06-05 cross-actor parity を fail-closed テストで固定 (loop iter, 成熟度)**: 新設 `70-tools/scripts/audit/test_gov_legal_coverage_parity.py`(R0-safe: test-only/network-free/cell 非実行)が、単一アクター suite では見えない**横断不変条件**を 3 つ pin: (1) 両 registry の jurisdiction コードは ISO-3166-1 alpha-3 lowercase または文書化済み擬似法域 `eu-wide` のみ(uk/USA/usa2 等のタイポ → coverage 断片化を fail-closed 検出; 負例で検証済)。(2) coverage floor — 各 registry ≥47 distinct 法域(2026-06-05 到達; 回帰=shrink で fail)。(3) parity floor — 両アクターの共有法域 ≥45(intersection は除去時のみ縮むため将来の片側 growth に対し非 brittle)。現値 toritsugi=47/chigiri=47/shared=47。関連 4 suite **27/27 green**。

**2026-06-05 主要欠落経済圏を両アクター同時追加 (loop iter, parity 保持拡大)**: 旅券当局＋法律扶助機関の双方を確信できる 5 か国を toritsugi/chigiri 双方へ追加し parity を保ったまま **47 → 52 法域**へ拡大: 🇹🇷tur(NVI/e-Devlet ‖ Türkiye Barolar Birliği adli yardım, Avukatlık Kanunu 176-181 + CMK)・🇷🇺rus(МВД/Gosuslugi ‖ FZ-324/2011 бесплатная юридическая помощь)・🇵🇰pak(DGIP ‖ Legal Aid and Justice Authority Act 2020)・🇧🇩bgd(e-Passport ‖ NLASO, Legal Aid Services Act 2000)・🇺🇦ukr(ДМС/Diia ‖ legalaid.gov.ua, Law on Free Legal Aid 2011)。全件実在機関 + 確信できる legalBasis(G8 捏造ゼロ; 不確実な閾値・地方経路は notes で guide-time resolve・confidence で明示)、unverified-seed(G14) + https provenance + 各境界注記。toritsugi 98→103 手続き / chigiri 66→71 referral / shared=52(片側ズレ 0)。parity + 既存 4 suite **27/27 green**。

**2026-06-05 自動生成カバレッジ・ダッシュボード新設 (loop iter, observability)**: 新 generator `70-tools/scripts/coverage/gen_gov_legal_coverage.py`(ooyake COVERAGE.md パターンに倣う)が registry から committed `COVERAGE.md` を両アクター分生成。toritsugi/chigiri はこれまでダッシュボードも scripts も無く、カバレッジが registry を直接読まないと不可視だった点を解消。toritsugi: 103 手続き/52法域 + **procedure kind 内訳**(passport 42 / national-id・residence 24 / civic 11 / social-security 10 / tax 7 / civil 2 / other 7) + confidence 内訳(high 72 / medium 25) + 全 unverified-seed。chigiri: 71 body/52法域 + bloc 内訳 + confidence + 全 unverified-seed。両者に cross-actor parity 行(shared 52, parity test 参照)。**G5/G8/G14 honesty を各 doc 冒頭・末尾に明示**(全 unverified-seed wayfinding scaffold = authoritative coverage ではない; chigiri は UPL/referral-only/zero-compensation, toritsugi は 行政書士法/no-advice 境界)。audit suite 全 green。working-tree edits only。

**2026-06-05 新カテゴリ「事業者登記 business/company registration」追加 (loop iter, depth)**: 起業=高価値の行政手続きとして、各国の実在公式登記所で 22 法域分を追加 (jpn 法務局商業登記/会社法 · gbr Companies House/CA2006 · deu Handelsregister/HGB · usa SoS+EIN · fra INPI guichet unique · can Corporations Canada · aus ASIC · sgp ACRA BizFile+ · ind MCA SPICe+ · nld KVK · ita Registro Imprese · esp Registro Mercantil · kor startbiz · bra Redesim · mex RPC · nzl/irl/swe/nor/che/pol/zaf 各登記所)。全て既存 52 法域内 → **parity 不変**。手続き 103 → 125、新 procedure-kind が分類器(coverage dashboard)に追加(business/company registration)。全件 unverified-seed + https + 司法書士法/UPL 境界(商業登記の代理申請は司法書士/弁護士の専管と明記) + DRIFT、fee/条文は捏造せず resolve-at-guide-time(legalBasis は確信できる国のみ)。完成済み投影パイプライン経由で **atlas にも自動反映**(gen_intl_procedures.py 再実行 → 投影 93→114; jpn 事業者登記は intl skip=hand-authored 領域)。3 dashboard 再生成。toritsugi/parity/ooyake-integrity/freshness 全 green、フル監査 **492 passed / 0 failed**。

**2026-06-05 新カテゴリ「婚姻 civil marriage registration」追加 (loop iter, life-event depth)**: 主要ライフイベントの欠落(出生✓事業✓身分証✓旅券✓に対し婚姻が未カバー)を埋め、各国の civil registry/戸籍で 20 法域分を追加 (jpn 市区町村戸籍/戸籍法+民法 · usa county clerk · gbr GRO · deu Standesamt · fra mairie état civil · esp/ita/bra/mex Registro Civil/Cartório · ind state registrar · aus/can/nzl/sgp/kor/nld/irl/swe/zaf/phl 各 civil registry)。**国家への民事婚姻届のみ**(宗教/covenant ceremony は scope 外→musubi と notes 明記)。全て既存 52 法域内 → parity 不変。全件 unverified-seed + https + UPL 境界 + DRIFT、捏造なし(fee/条文 resolve-at-guide-time、legalBasis は確信国のみ)。coverage 分類器に marriage/civil-status カテゴリ追加。完成済みパイプライン経由で atlas 投影に自動波及。check()=CLEAN、フル監査 494 passed/0 failed。

**2026-06-05 新カテゴリ「運転免許 driving licence」追加 (loop iter, depth)**: 世界トップ需要の行政手続きとして 19 法域分を追加 (jpn 公安委員会/道路交通法 · gbr DVLA · fra ANTS · deu Fahrerlaubnisbehörde · ind Parivahan Sarathi/MVA1988 · esp DGT · ita Motorizzazione · nld RDW/CBR · kor 도로교통공단 · bra DETRAN/CNH · nzl Waka Kotahi · irl NDLS · swe Transportstyrelsen · nor Statens vegvesen · pol prawo jazdy · sgp Traffic Police · zaf DLTC · aus 州当局 · can 州当局)。usa は既存 REAL ID 免許と重複のため除外。全て既存 52 法域内 → **parity 不変**。手続き 125 → 164(うち本 iter +19 dl; 別途 parallel で +20 cs 婚姻カテゴリ)。coverage 分類器に driving-licence カテゴリ追加。全件 unverified-seed + https + UPL 境界 + DRIFT、捏造なし(fee/条文 resolve-at-guide-time、legalBasis 確信国のみ)。投影パイプライン経由で atlas 自動反映(投影 114→151)。3 dashboard 再生成。check() CLEAN・freshness・integrity・parity 全 green、フル監査 **494 passed / 0 failed**。
