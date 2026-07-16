# himotoki (繙き) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで成熟度を上げ、ここに記録する。
honest framing: できていないことは「未」と明記する。

- Actor: `did:web:himotoki.etzhayyim.com` · ADR-2605302130 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 / dispatch なし ·
  DSAR = own-data-only + consent + DID/SBT binding (G3) · 真の requester 明示・
  pretext 禁止 (G4) · 法的助言禁止 → chigiri + external counsel (G5) · 開示 PII は
  `com.etzhayyim.encrypted.*` DID-bound envelope のみ・MST 平文禁止 (G6) ·
  Murakumo-only (G7) · mass-file/flood 禁止 (G8) · lawful official channel only (G10) ·
  **G14 verified-procedure-only**(unverified-seed には live dispatch 不可)·
  G8 非捏造 · コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605302130 (master) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | disclosure-target registry seed (unverified-seed) | ✅ | init |
| 4 | DSAR/FOIA 主要 data-controller 窓口 (JP/US 中心) | ✅ | init |
| 5 | **worldwide multi-jurisdiction 拡張** (US/EU/UK-CW/INTL-ROW) | ✅ | **iter-1** |
| 6 | cell scaffold (`kotodama.cells.himotoki_*`, import時 RuntimeError) | 未 | — |
| 7 | cell ↔ manifest 整合 invariants test | 未 | — |
| 8 | G3–G14 機械検証 node guard | 未 | — |
| 9 | provenance URL 再フェッチ検証ワークフロー (medium → high 昇格) | 未 | — |
| 10 | kotoba KG seed への himotoki エンティティ追加 | 未(node-local) | — |
| 11 | murakumo fleet.toml への cell placement | R1延期 | — |

## イテレーション記録

### iter-1 (2026-06-02) — worldwide coverage 拡張
**上げた項目: #5 — disclosure-target registry の worldwide multi-jurisdiction 拡張。**
`registry/targets.seed.json` に新規 **25 件**を merge(既存 6 件は全保持、合計 **31 件**)。
追加 bloc 内訳:
- **US** (6): 連邦 FOIA (5 U.S.C. §552) / Privacy Act (§552a) / California CCPA-CPRA (Right to
  Know, to the business) / California Public Records Act (Gov.Code §7920) / New York FOIL
  (POL Art.6) / Texas PIA (Gov.Code ch.552)。
- **EU** (6): GDPR Art.15 SAR (any controller) / Reg.1049/2001 EU institution documents /
  Germany IFG (federal FOIA) / Germany Art.15 DSGVO + §34 BDSG / France droit d'accès RGPD
  (CNIL fallback) / France CADA (CRPA Livre III)。
- **UK-CW** (7): UK SAR (UK GDPR Art.15, ICO) / UK FOI Act 2000 / Canada ATIP (Privacy Act +
  ATIA) / Australia APP 12 access / Australia FOI Act 1982 / India RTI Act 2005 (RTI Online) /
  Singapore PDPA Access & Correction (ss.21–22)。
- **INTL-ROW** (6): Brazil LGPD Art.18 DSAR / Brazil LAI via Fala.BR / Mexico ARCO (LFPDPPP,
  post-INAI) / Mexico PNT SISAI 2.0 (LGTAIP + LGPDPPSO) / South Korea PIPA Art.35 / South
  Korea OIDA via open.go.kr。

正規化: 各エントリを既存スキーマ (`organization` / `jurisdiction` / `regime` / `altRegimes` /
`channelType` / `portalUrl` | `contactEmail` | `postalAddress` / `formRef` /
`statutoryDeadlineDays` / `language` / `provenance` / `lastVerified` / `verificationStatus` /
`notes`) に写像。研究由来の `authority` / `legalBasis` は追加フィールドとして保持し、`title` /
`channel` / `id` は notes と既存フィールドへ畳み込み。`_comment` を worldwide 拡張を反映して更新。

**G14/G8 honest framing**: 新規 **25 件すべて** `verificationStatus: "unverified-seed"`、
`lastVerified: "2026-06-02T00:00:00Z"`。全件 `provenance` URL・言語コード (en/de/fr/pt/es/ko)・
境界 caveat("Consent-gated, identity-bound, own-data-only (DSAR) or public-records (FOIA).
Transparent, non-pretextual, rate-limited, lawful-channel-only. No legal advice.")を notes に内包。
DSAR は own-data-only、FOIA は public-records と各 notes で明示区別。`organization` で dedup 済み
(重複ゼロ)。捏造の contact data は追加せず、調査で grounded な channel のみ採録(G8: inflated
count より honest coverage を優先)。Mexico 2 件は post-INAI 再編が 2026 時点で settling 中の
ため confidence medium として notes に明記。

検証: `python3 -c json.load` で JSON valid・31 entries・bloc 内訳・dedup・新規 25 件の
required field (verificationStatus / provenance / language / boundary caveat) 全 pass。

**注(honest)**: registry はあくまで routing/wayfinding scaffold。R0 では cell 非実行・dispatch
なしのため live use は構造的に不可。次イテレーション候補 = #6 (cell scaffold) または #9
(provenance 再フェッチで medium → high 昇格)。

### iter-2 (2026-06-02) — fail-closed registry invariants test 追加・green
`70-tools/scripts/audit/test_himotoki_registry_seed.py` を新規追加(R0-safe: test-only / network-free / cell 非実行)。7 invariant を fail-closed で検証 — (1) JSON parse + 非空 `targets`、(2) `organization` 一意(重複ゼロ)、(3) G14: 全件 `verificationStatus=unverified-seed`、(4) 全件 https `provenance` + `lastVerified`、(5) jurisdiction 多様性(>=5 distinct = worldwide regression guard)、(6) per-entry `notes` 非空 + registry 全体の境界 regime(own-data-only / lawful-channel)参照、(7) top-level integer `freshnessWindowDays`。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest ... -q` で **7 passed**(env の langsmith pytest-plugin が pydantic-core 不整合で autoload 失敗するため plugin autoload を無効化して実行;テスト自体はクリーン)。

### iter-4 (2026-06-02) — long-tail worldwide deepening: +34 entries (65 total), 41 distinct jurisdictions, test green
`registry/targets.seed.json` に long-tail worldwide DSAR/FOIA channel を新規 **34 件** merge(既存 31 件全保持、合計 **65 件**、`organization` dedup 重複ゼロ)。追加 bucket: **EU-REST**(SE/NO/NL/IE/ES/PT/IT/CH)・**ASIA-REST**(HK/TH/PH/CN/ID/MY/VN/TW)・**AMERICAS-REST**(AR/CL/CO/PE/Quebec-CA)・**MEA-OCEANIA**(ZA/NZ/IL/NG/KE/AE/SA/EG)。distinct jurisdiction 数 13 → **41**。全 34 件 `verificationStatus: unverified-seed` / `lastVerified: 2026-06-02T00:00:00Z` / https `provenance` / 言語コード(sv/no/nl/en/es/pt/it/de/zh/th/id/ms/vi/he/ar)/ 境界 caveat を notes に内包。invariants test の distinct-jurisdiction 閾値を `>= 5` → **`>= 12`** に引き上げ(実測 41 が十分上回る)。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest 70-tools/scripts/audit/test_himotoki_registry_seed.py -q` で **7 passed**(green)。捏造 contact なし(G8); post-INAI/新法施行中の jurisdiction は confidence medium として notes 明記。

### iter-5 (2026-06-02) — VERIFICATION.md authored (toritsugi-parity, G14 verify-workflow doc)
`registry/VERIFICATION.md` を新規追加 — toritsugi `registry/VERIFICATION.md` のテンプレに準拠した G14 verified-target-only 検証ワークフロー spec(R0: 全 65 件 `unverified-seed`、`himotoki_dispatch` は R1 Council 批准 + maintainer DID 登録まで非活性)。3-tier 表(unverified-seed → maintainer-verified[member self-directed dispatch を解放]→ council-verified)・本 registry 実フィールド名に対する per-field checklist・WORLDWIDE PROVENANCE per-jurisdiction 公式ドメイン判定(.go.jp/.gov/.gouv.fr/.gov.uk/europa.eu/.gob.*/.go.kr…; fail-closed)・freshnessWindowDays 180 staleness rule・boundary 再チェック(consent-gated/own-data-only DSAR・public-records FOIA・non-pretextual G3/G4・lawful-channel G10・no legal advice G5)・`test_himotoki_registry_seed.py` machine-enforced floor を記載。honest framing(G8): 未検証項目を明記。複数 ledger が「未」としていた toritsugi-parity 項目を解消。

### iter-3 (2026-06-02) — lexicon `disclosureTarget` を seed と整合(extended, additive/permissive, R0)
`00-contracts/lexicons/com/etzhayyim/himotoki/disclosureTarget.json` を **EXTEND**(既存フィールド・required は無改変、additive only / permissive R0、`additionalProperties:false` 不追加)。seed `registry/targets.seed.json` の全フィールド・全 knownValue を被覆 — 新規プロパティ **4 件**(`bloc` [JP/US/EU/UK-CW/INTL-ROW] / `altRegimes` [array] / `authority` / `legalBasis`)+ `regime.knownValues` を 6 → **30**(gdpr/ccpa/foia-us-5usc552/privacy-act-us-5usc552a/foia-us-{ca-cpra,ny-foil,tx-pia}/foia-eu-1049-2001/foia-de-ifg/bdsg-34/foia-fr-crpa/uk-gdpr-15/foia-uk-2000/privacy-act-ca/foia-ca-atia/privacy-act-au-app12/foia-au-1982/foia-in-rti-2005/pdpa-sg/lgpd-18/foia-br-lai/lfpdppp-arco/foia-mx-lgtaip/lgpdppso-arco/pipa-35/foia-kr-oida … 既存 6 値も全保持)、`jurisdiction.maxLength` 8 → 16(eu-wide 等を収容)。憲法境界を description 文に明記保持(UPL / 法的助言なし / informational routing only / no representation・fees・campaigning・official-channel)。クロスチェック: seed の regime/altRegime 28 値 + 全 bloc が lexicon knownValues に **MISSING NONE**。検証: `lexicon-primary-types.mjs` OK(17 files)・`nsid-lexicon-exists.mjs` OK 緑。`lexicon-const-name-collision-check.mjs` は本ファイル無関係の既存衝突(`com.etzhayyim.apps.ipaddress.analyzeIp`)で fail — 当該 validator は私の編集前から同一理由で fail(stash 確認済み)、本ファイルが原因の新規 fail なし。

**2026-06-02 R1 deadline core (gate closed)**: 新規 gated cell `kotodama.cells.himotoki_deadline_check` + 純コア `deadline.py` — DSAR/FOIA 応答期限の決定論計算(regime テーブル駆動: GDPR/UK-GDPR 1ヶ月+2ヶ月延長 · CCPA 45+45日 · LGPD 15日 · PIPEDA 30日 · US FOIA 20営業日 · APPI 不定=null で数値捏造せず G14)。`is_legal_opinion` は常に False(コード経路無し)、月計算は月末クランプ、営業日は祝日未モデルを note 明示。`test_deadline.py` 39件(35 pass+4 by-design skip)、registry 統合テスト込み。cell.py は import 時 RuntimeError 維持(Council 批准が唯一の活性化スイッチ)。敵対的検証: legal-rule + invariant 両 sound。

**2026-06-02 R1 target resolver (gate closed)**: `himotoki_deadline_check/target_resolver.py` — 管轄(+任意 regime gdpr-15/ccpa-110/foia 等; altRegimes も照合)→ 開示先の純 registry クエリ(confidence 有無を両対応、未graded も surface、未知管轄→空)。日付計算は sibling deadline.py に分離、本コアは routing のみ(`isDeadlineComputation`/`isEligibilityDetermination` False)。is_legal_opinion 常に False。`test_target_resolver.py` 44件 green。cell.py ゲート閉維持。敵対的検証: 両 sound。himotoki は deadline+target の2コア体制。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
既存 registry-seed テストが被覆していなかった **manifest G1–G14 + 5 lexicon の開示請求ゲート**を新設 `methods/test_charter_gates.cljc`(**6 tests green**, standalone・network-free)で固定: (1) manifest 厳密に G1–G14。(2) **G3** disclosureRequest が requesterDid + purpose + scope 必須、requestKind={dsar, foia}(own-data + true requester)。(3) **G4** requestDispatch が requesterDid + verificationStatusAtDispatch{maintainer-verified, council-verified} + deadlineAt 必須(**verified target にのみ dispatch**、unverified-seed flooding なし)。(4) **G14** disclosureTarget が regime + provenance + verificationStatus + jurisdiction 必須。(5) **G5** appealRecord が chigiriTemplateRef 必須 + appealChannel が shinsa-seikyu/dpa/foia-appeal 等(UPL境界、proper review channel へ)。(6) disclosureResponse が deadlineMet 必須(法定期限アカウンタビリティ)。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `himotoki.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
