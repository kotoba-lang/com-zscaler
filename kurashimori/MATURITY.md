# kurashimori (暮らし守) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで成熟度を上げ、ここに記録する。honest
framing: できていないことは「未」と明記する (G8 — 誇張より正直な被覆)。

- Actor: `did:web:kurashimori.etzhayyim.com` · ADR-2605312500 · **R0 scaffold**
- 不変条件 (全イテレーション厳守): R0 では cell 非実行 · 送付/dispatch なし ·
  PII 平文禁止 (G6) · Murakumo-only (G7) · 弁護士法/司法書士法/UPL 境界 (G5) ·
  G8 非捏造 · G14 verified-procedure-only (`unverified-seed` には live send 不可) ·
  G15 self-send-default (代行は R3 gated) · コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605312500 (master) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | remedyTarget registry seed (JP 5件, unverified-seed) | ✅ | init |
| 4 | **registry worldwide 横展開 (4 ブロック, 全 unverified-seed)** | ✅ | **iter-worldwide (2026-06-02)** |
| 5 | cell scaffold (`kotodama.cells.kurashimori_*`, import 時 RuntimeError) | 未 | — |
| 6 | cell ↔ manifest 整合 invariants test | 未 | — |
| 7 | 憲法ゲート機械検証 node guard | 未 | — |
| 8 | chigiri / himotoki / wakai / warifu 双方向 cross-actor boundary | 未 | — |
| 9 | remedy seed の根拠法令・provenance 精査 + verification ワークフロー | ✅ (VERIFICATION.md authored) | iter-verification-doc |
| 10 | murakumo fleet.toml への cell placement | R1 延期 | — |
| 11 | **R1 cooloff 純計算コア + tests (ゲート閉のまま先行実装)** | ✅ | **iter-r1-core (2026-06-02)** |

## イテレーション記録

### iter-worldwide (2026-06-02)
**上げた項目: #4 — remedyTarget registry を JP-only から WORLDWIDE へ横展開。**
既存 JP 5件 (`jp-houmon-cooloff` / `jp-rensa-cooloff` / `jp-tsushin-return` /
`jp-shohi-center-188` / `jp-tekikaku-adr`) は **全件そのまま保持**し、新規 29件を
既存スキーマ (`remedyId` / `title` / `jurisdiction` / `remedyKind` /
`statutoryWindowDays` / `windowStart` / `formRef` / `deliveryChannel` /
`escalationForum` / `legalBasis` / `language` / `provenance` / `lastVerified` /
`verificationStatus` / `notes`) に正規化してマージ。合計 **34件**。

**追加した 4 ブロック (29件):**
- **US — United States (7件)**: FTC Cooling-Off Rule (3営業日) · FTC Mail/Internet
  Order Rule (30日発送) · FCBA billing-error chargeback (60日) · Magnuson-Moss
  Warranty Act · CA Song-Beverly Lemon Law (代表州例) · NY door-to-door 解約 (代表
  州例) · TX DTPA + 60日 pre-suit notice (代表州例)
- **EU — European Union (6件)**: CRD 14日撤回権 · Sale of Goods 2年法定保証 ·
  ECC-Net 越境苦情 · Consumer ADR Directive (旧 ODR platform は 2025-07 廃止を
  drift 警告) · DE BGB §312g/§355 Widerrufsrecht · DE Universalschlichtungsstelle ·
  FR Code conso L221-18 rétractation · FR SignalConso (DGCCRF)
- **UK-CW — UK + Commonwealth (7件)**: UK CCR 2013 14日 cancellation · UK CRA 2015
  30日 reject faulty · UK Section 75 + FOS chargeback · Ontario CPA 2002 10日
  cooling-off (代表州例) · AU ACL consumer guarantees (ACCC) · IN CPA 2019
  NCH/eDaakhil · SG CPFTA Lemon Law (CASE)
- **INTL-ROW — Rest of World + 国際機関 (7件)**: BR Consumidor.gov.br · BR CDC
  art.49 7日 arrependimento · MX PROFECO Concilianet · MX LFPC art.56 5営業日
  revocación · KR KCA 1372 · KR 전자상거래법 art.17 7日 청약철회 · INTL
  econsumer.gov/ICPEN (OECD/UNCTAD 連携)

**G8 honest framing — 全件 unverified-seed**: 新規 29件は **全て**
`verificationStatus: "unverified-seed"` · `lastVerified: "2026-06-02T00:00:00Z"` ·
言語コード (en/de/fr/pt/es/ko) · 公式 provenance URL · そして notes に共通境界
caveat「**弁護士法 / 司法書士法 / UPL: self-help diagnosis + drafting-assist only.
Cooling-off windows are INFORMATIONAL date computation, NOT a legal opinion. No
representation, no claims-buying, no fees.**」を必ず含む。各国の境界類似法 (German
RDG · French monopole de l'avocat / loi n°71-1130 · Advocates Act 1961 · Legal
Profession Act · Law Society Act · 변호사법) も notes に明記。

**`_comment` 更新**: registry が JP-only から worldwide (multi-jurisdiction) へ
拡張されたこと、4 ブロックの内訳、全件 unverified-seed で live use 不可、誤った
日数が有害なため公式ソースでの検証必須を追記。

**検証**: JSON valid · 34件 · remedyId 重複 0 · 新規 29件すべてが必須フィールド
(verificationStatus / lastVerified / language / provenance(http) / 境界 caveat)
を保持することを `python3 -c` assert で確認。drift 警告を含む新規エントリ:
EU ODR platform 廃止 (2025-03 受付停止 / 2025-07 規則廃止) · EU 2023/2673 cancel
ボタン義務化 (~2026-06) · 各国の閾値/条文番号は改正されうるため要再検証。

**fail-closed registry invariants test 追加 (2026-06-02)**: `70-tools/scripts/audit/test_kurashimori_registry_seed.py` を新規作成 — JSON parse + 非空 `targets` + 整数 `freshnessWindowDays` · `remedyId` 重複ゼロ (fail-closed) · 全件 `verificationStatus=unverified-seed` (G14) · 全件 https provenance + `lastVerified` · `jurisdiction` ≥5 種 (worldwide 被覆、JP-only 回帰ガード) · 全件 notes 非空 + 弁護士法/司法書士法/UPL 境界 caveat (G5)。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest` で **6 passed (green)**。これにより #6 (cell↔manifest 整合 invariants) の registry-side 部分を先行カバー。

### iter-r1-core (2026-06-02)
**上げた項目: #11 — R1 cooloff 純計算コアをゲート閉のまま先行実装。**
`40-engine/kotoba/crates/kotoba-kotodama/cells/kurashimori_cooloff_check/cooloff.py` を新規作成 — 純
stdlib・ネットワーク無し・PII 非永続の日付計算コア。管轄別の起算規則を明示
パラメータ化: `calendar_inclusive` (JP 特商法 起算日=1日目) / `calendar_exclusive`
(EU CRD・DE Widerruf 翌日起算) / `business_inclusive`・`business_exclusive` (US FTC
営業日; 祝日は未モデル化で note に明示 = G8)。`compute_assessment` / `compute_deadline`
/ `to_assessment_record` を提供し、`coolingOffAssessment` lexicon 形状の dict を生成。

**G5 不変条件をコード構造で担保**: `is_legal_opinion` / `isLegalOpinion` は常に
`False` で、`True` にできるコード経路が存在しない (`to_assessment_record` は return 前に
`assert rec["isLegalOpinion"] is False`)。`computation_note` に「INFORMATIONAL date
computation per ADR-2605312500 G5 — NOT a legal opinion」+ 営業日計算の祝日未考慮
警告 + chigiri/有資格 counsel への routing を必ず含める。

**R0/R1 境界を厳守**: `cell.py` (`KurashimoriCooloffCheckCell`) の activation gate は
一切変更せず、**import 時 RuntimeError のまま**。純コアは sibling module として
gated wrapper を import せずにテスト可能。Council 批准 (Lv6+ ≥3, RFP 2026-06-19
クローズ後) 時に `super_step` が `cooloff.*` を呼ぶ。**本コア実装は cell を活性化
しない** (確認済: `cell.py` を直接 exec すると今も RuntimeError)。

**テスト**: `test_cooloff.py` 新規 — G5 不変 (全 counting で is_legal_opinion False) ·
JP 8日 inclusive (起算 06-01 → 期限 06-08, 境界日 within / 翌日 expired) · EU 14日
exclusive · US FTC 3営業日 inclusive/exclusive + 週末スキップ + 祝日警告 note ·
window≤0 / 不正 counting で ValueError · `to_assessment_record` の isLegalOpinion=False
+ ISO 整形 · worldwide registry 統合 (cooling-off ≥5件・`jp-houmon-cooloff`=8日)。
`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest` で **18 passed (green)**。
cell dir に空 `__init__.py` を追加 (リポジトリ規約; gated cell.py は import しない)。

**未 (honest)**: #5–8 (cell scaffold · cell↔manifest invariants · gate guard · cross-actor
boundary) は未着手 — toritsugi パターン parity に向けて後続イテレーションで実施。
#9 は provenance を公式ソースに限定済だが、unverified-seed → maintainer-verified
の人手チェックリスト文書 (VERIFICATION.md) は未作成。#10 fleet.toml 配置は R1 延期
(他 R0 tier-b actor と同様、live インフラに import-RuntimeError scaffold は載せない)。
全件 unverified-seed のため、本イテレーションで live send 能力は一切付与していない。

**2026-06-02 lexicon reconciliation**: `com.etzhayyim.kurashimori.remedyTarget` lexicon EXTENDED (additive, R0-permissive) to cover the worldwide `registry/targets.seed.json` — `remedyKind` knownValues += `chargeback`, `warranty` (all seed `remedyKind`/`statutoryWindowDays`/`windowStart`/`escalationForum` values now covered); description amended for multi-jurisdiction scope while preserving the UPL / informational-only / no-representation / no-fees boundary. No fields removed/renamed/required; no `additionalProperties:false`. Validators green: lexicon-primary-types OK, nsid-lexicon-exists OK; const-name-collision-check fails only on pre-existing unrelated `com.etzhayyim.apps.ipaddress.analyzeIp` collision (reproduces with this edit stashed — not caused here).

**2026-06-02 long-tail worldwide deepening**: merged 31 long-tail entries across 4 buckets (EU-REST: SE/DK/NL/IT/PL/IE/ES/CH · ASIA-REST: CN/TW/HK/TH/ID/PH/VN/MY · AMERICAS-REST: AR/CL/CO/PE/CA-Québec + AR legal-warranty · MEA-OCEANIA: NZ/AE/SA/IL/ZA/NG/KE/EG) into `registry/targets.seed.json`. Total entries 34 → 65; distinct jurisdictions 13 → 41. 0 dups dropped. All new entries ship verificationStatus=unverified-seed, lastVerified 2026-06-02, an https provenance, a language code, and the 弁護士法 / 司法書士法 / UPL boundary caveat in notes. Invariants test threshold raised 5→12 distinct jurisdictions; `test_kurashimori_registry_seed.py` **6 passed (green)**.

**2026-06-02 R1 escalation resolver (gate closed)**: `kurashimori_escalation/escalation_resolver.py` — 管轄→消費者センター/ADR(remedyKind∈{escalation-public,escalation-adr})の純 registry クエリ(registry の verificationStatus を ranking 信号に、未知管轄→空)。**ROUTE-NOT-REPRESENT**: 代理・法的意見・claims-buying・手数料なし、日付計算フィールドも漏らさない。is_legal_opinion/isRepresentation/isEligibilityDetermination を False 担保。`test_escalation_resolver.py` 40件 green。cell.py ゲート閉維持。敵対的検証: 両 sound。kurashimori は cooloff+escalation の2コア体制。

**2026-06-02 VERIFICATION.md authored (#9 ✅, toritsugi-parity)**: `registry/VERIFICATION.md` を新規作成 — toritsugi `registry/VERIFICATION.md` の三層 (unverified-seed → maintainer-verified → council-verified) idiom を本 actor に適応。`maintainer-verified` が `kurashimori_send` (本人送付 R2) を、`council-verified` が 代行 (R3, Council Lv7+ + 司法書士法/行政書士法 clearance) を unlock。65件 (41 管轄) の実フィールド名 (`remedyId`/`title`/`jurisdiction`/`remedyKind`/`statutoryWindowDays`/`windowStart`/`formRef`/`deliveryChannel`/`escalationForum`/`legalBasis`/`formRef`/`provenance`/`lastVerified`/`notes`) に対する 13項チェックリスト。CRITICAL emphasis: 誤った `statutoryWindowDays`+`windowStart` は有害ゆえ官報/公式条文での再検証を前景化。WORLDWIDE provenance は per-jurisdiction official-source (.go.jp/.gov/.gouv.fr/.gov.uk/europa.eu/.gob.*/.go.kr 等) check, fail-closed (公式確認不可なら unverified-seed 据え置き)。`freshnessWindowDays`=180 staleness rule。弁護士法/司法書士法/UPL 境界 (診断+起草補助のみ・日数は INFORMATIONAL date computation で法的意見でない・代理/claims-buying/手数料なし) を再検証ステップに encode。machine-enforced floor として `70-tools/scripts/audit/test_kurashimori_registry_seed.py` を引用。R0 honest framing: いまだ verified エントリは 0件、全件 unverified-seed、実行は R1 から。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
既存テスト(registry-seed + cooloff/escalation cell)は被覆していなかった **manifest G1–G15 + 7 lexicon の UPL/消費者保護ゲート**を新設 `methods/test_charter_gates.cljc`(**7 tests green**, standalone・network-free)で固定: (1) manifest が厳密に G1–G15。(2) **UPL** — coolingOffAssessment const `isLegalOpinion=false`(クーリングオフ判定は法的意見でなく見積)。(3) **drafting-assist のみ** — remedyDraft.assistMode が {drafting-assist} のみ(代理作成なし)+ memberConfirmed + encryptedDraftRef(G6)必須。(4) **self-send default** — dispatch が consentRef 必須、mode={member-self-send, agent-on-behalf}(代行は R3 ゲート)。(5) **G14** — remedyTarget が legalBasis + provenance + verificationStatus{unverified-seed, maintainer-verified, council-verified} 必須。(6) **G5 escalation** — forum に shohi-seikatsu-center + chigiri-counsel + hotline-188。(7) **own-matter** — 全稼働 record が memberDid 必須。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `kurashimori.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
