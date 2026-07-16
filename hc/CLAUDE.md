# etzhayyim-project-hc — Human Computing Platform (スキマバイト + マイクロタスク)

## Overview

Timee-style ギグワーク + MTurk-style マイクロタスク のハイブリッドプラットフォーム。
日本の労働基準法に準拠。全通知・手続きは Matrix protocol 経由。

- **URL**: https://hc.etzhayyim.com
- **API**: https://hc0mp7ng.etzhayyim.com/xrpc

## Architecture

```
Browser (SuperApp Mobile-First)
  ├─ HTML/JS → hc.etzhayyim.com (static delivery)
  └─ API → hc0mp7ng.etzhayyim.com/xrpc → Envoy Gateway
              ↓
       App: hc-nhc0mp7ng (TS Native)
              ├─ HCCommandService — compatibility ingress for shifts/tasks/bookings/compliance
              ├─ HCQueryService — browse, list, stats
              ├─ actor.SendRoomEvent() → Matrix MessagingService (全通知)
              └─ kotodama WIT → SQL graph (Arrow schema)
```

## Event Stream Services

| Service | Path |
|---|---|
| HCCommandService | `/xrpc/etzhayyim.human_computing.v1.HCCommandService/*` (compatibility ingress) |
| HCQueryService | `/xrpc/etzhayyim.human_computing.v1.HCQueryService/*` |

### Transport Rule

- 正規 command contract は Matrix `org.etzhayyim.command.hc.*`
- typed read は XRPC `HCQueryService`
- この文書時点では notifications は Matrix、business mutation は compatibility endpoint から段階移行対象

## Arrow Tables (sql graph/sql graph)

| Table | DocID | Purpose |
|---|---|---|
| `hc_shifts` | `id` | Timee-style gig shifts (location, time, hourly rate) |
| `hc_shift_bookings` | `id` | Worker bookings for shifts |
| `hc_tasks` | `id` | MTurk-style micro-tasks (HIT) |
| `hc_assignments` | `id` | Worker assignments for tasks |
| `hc_workers` | `worker_id` | Worker profiles (prefecture, ratings, earnings) |
| `hc_compliance_log` | `id` | 労働基準法 compliance audit trail |
| `hc_disputes` | `id` | Assignment disputes |
| `hc_stats` | `stat_key` | Platform-wide statistics |

## Japanese Labor Law Compliance (労働基準法)

| Check | Law | Rule |
|---|---|---|
| 最低賃金 | 最低賃金法 | 都道府県別の最低賃金以上 (47都道府県対応) |
| 1日の労働時間 | 労基法32条 | 8時間/日上限 (実労働時間) |
| 週の労働時間 | 労基法32条 | 40時間/週上限 |
| 休憩時間 | 労基法34条 | 6h→45分、8h→60分 |
| 深夜労働 | 労基法37条 | 22:00-05:00 は25%割増警告 |

Compliance checks are:
- **CreateShift**: 自動検証。fail なら作成拒否 + Matrix 通知
- **BookShift**: 週間労働時間チェック。40h 超過なら予約拒否
- **CheckCompliance**: 事前チェック API (シフト作成前の検証用)

## Matrix Protocol Integration

全通知・手続きは Matrix protocol (`actor.SendRoomEvent`) 経由。

| Room | Purpose |
|---|---|
| `hc-general` | 一般チャンネル |
| `hc-shifts` | シフト公開通知 |
| `hc-bookings` | 予約確定/キャンセル通知 |
| `hc-tasks` | タスク公開/受注/承認通知 |
| `hc-compliance` | コンプライアンス違反警告 |
| `hc-attendance` | チェックイン/チェックアウト |
| `hc-disputes` | 紛争通知 |

## Shift Flow (Timee-style)

1. Poster → `CreateShift` (compliance auto-check) → Matrix 通知
2. Worker → `BookShift` (weekly hours check) → Matrix 通知
3. Worker → `CheckIn` at shift start → Matrix 通知
4. Worker → `CheckOut` at shift end → 自動給与計算 → Matrix 通知
5. Poster → `RateShift` / Worker → `RateShift`

## Task Flow (MTurk-style)

1. Requester → `CreateTask` → USDC/USDT escrow → Matrix 通知
2. Worker → `AcceptTask` → Matrix 通知
3. Worker → `SubmitWork` → Matrix 通知
4. Requester → `ApproveAssignment` → 支払い → Matrix 通知
5. Rejection → `CreateDispute` → Matrix 通知

## Payment

| Type | Currency | Method |
|---|---|---|
| Shift work | JPY | 日本円 (銀行振込 / 即時払い) |
| Micro-tasks | USDC/USDT | Ethereum escrow → worker wallet |

Platform fee: 2.5% (250 bps)

### yoro Credit Rewards (CRITICAL)

**hc.etzhayyim.com のタスク完了は Murakumo クレジットとしても付与される。** 人間は HC タスクをこなすことで yoro.etzhayyim.com で AI Agent に質問・投稿できる。

HC タスク完了時、`approve-assignment` handler が `Invoke("murakumo", "reward-compute-credits", ...)` で Murakumo にクレジット付与を要求する。

| HC Task Category | yoro Credits (¥相当) | Murakumo contribution_type |
|---|---|---|
| text-classification, image-annotation, data-entry | ¥2 | `hc_micro` |
| content-moderation | ¥1 | `hc_moderation` |
| translation | ¥3 | `hc_translation` |
| survey | ¥0.5 | `hc_survey` |
| code-review | ¥5 | `hc_code_review` |

**二重報酬**: HC タスクは USDC/USDT の直接報酬 + yoro クレジット の両方を受け取る。クレジットは追加インセンティブ。

## Contracts (CRITICAL — 日本法準拠, etzhayyim 有利設計)

**全契約の準拠法は日本法。専属管轄は東京地方裁判所。** 国別ローカライズは各国強行法規の最低限遵守のみ。

**権威ソース**: `wasm/etzhayyim-wasm-hc-hc0mp7ng/svelte/src/lib/legal/contracts.ts`

### 契約書 4 種

| 契約 | DID | 対象 | etzhayyim 有利ポイント |
|---|---|---|---|
| **Worker Agreement** | `did:web:hc.etzhayyim.com:legal:worker-agreement` | ギグ/タスクワーカー | 成果物 IP 全帰属 (著作権法27条28条含む), 人格権不行使, 免責最大化, 責任上限=直近12ヶ月報酬, 即時アカウント停止権, 競業避止1年, 秘密保持無期限+違約金100万円/件, 規約変更14日 |
| **SP Service Agreement** | `did:web:hc.etzhayyim.com:legal:sp-service-agreement` | OEM 製造者/工場 | 品質保証義務+瑕疵担保2年は SP 側, 不合格品費用 SP 負担, 納期遅延0.5%/日, PL法負担は SP, 監査権(3営業日通知), 競業避止2年, 秘密保持5年, 一方的解除権(30日), IP 改良は etzhayyim 帰属 |
| **Task Terms** | `did:web:hc.etzhayyim.com:legal:task-terms` | マイクロタスク個別 | reject 時報酬なし, auto-approve 期限後の黙示承認, 紛争裁定は etzhayyim 最終・拘束力あり・不服申立不可, 禁止行為違反で未払報酬没収 |
| **Shift Terms** | `did:web:hc.etzhayyim.com:legal:shift-terms` | シフトワーク個別 | 当社はマッチングのみ(契約当事者でない), シフト中事故の免責, 即時払い3%手数料, 手数料率変更権 |

### 国別ローカライズ (13 locale)

| locale | 地域 | 主要強行法規 |
|---|---|---|
| `ja` | 日本 | ベース契約 (追加なし) |
| `us` | 米国 | FLSA独立請負人, CCPA/CPRA, Class action waiver, CA Civil Code §1542 |
| `eu` | EU/EEA | GDPR, Platform Work Directive 2024/2831, Unfair Terms Directive 93/13/EEC |
| `gb` | 英国 | UK GDPR, Employment Rights Act 1996, Unfair Contract Terms Act 1977 |
| `cn` | 中国 | PIPL, 労働法/労働合同法, 著作権法, 出口管制法, CIETAC 仲裁 |
| `kr` | 韓国 | 개인정보보호법, 근로기준법, 하도급법, 전자상거래소비자보호법 |
| `in` | インド | DPDP Act 2023, Code on Social Security 2020, FEMA 1999, Income Tax Act |
| `vn` | ベトナム | Nghị định 13/2023 (PDPA), Bộ luật Lao động 2019, SBV 外為規制 |
| `th` | タイ | PDPA 2562, 労働保護法 2541, BOT 外為規制 |
| `de` | ドイツ | BGB §§305-310 (AGB), LkSG (サプライチェーン), REACH/RoHS |
| `tw` | 台湾 | 個人資料保護法, 勞動基準法, 消費者保護法 |
| `id` | インドネシア | UU PDP 2022, 労働法 UU 13/2003, BI 外為規制 |
| `my` | マレーシア | PDPA 2010, Employment Act 1955, Consumer Protection Act 1999 |

### 設計原則

- **日本法が全契約のデフォルト**: 各国 addendum は強行法規 override のみ
- **IP は全部 etzhayyim**: 成果物・改良・発明すべて。報酬に対価含む
- **免責は最大化**: AS IS 提供, 間接損害免責, 責任上限あり
- **解除は当社優位**: 理由不問の即時停止権 (ワーカー), 30日通知の理由不問解除 (SP)
- **紛争は東京地裁**: 仲裁は米国・中国等の強行法規対応としてのみ追加
- **規約変更権**: 14日 (ワーカー) / 30日 (SP) の一方的変更権

## Service Provider Registration Pipeline (CRITICAL)

**OEM 製造者・工場の登録は HC 経由で実施。** KYC/KYB 検証 → 工場監査タスク → tsukuru manufacturer-registry 正式登録 の一貫パイプライン。

### Registration Flow

```
1. SP 申請 → HC `create_sp_application` (基本情報 + 書類)
   → com.etzhayyim.apps.hc.sp_application record 作成
   → KYC/KYB verification task 自動生成 (kyc49bb7)

2. KYC/KYB 検証 → HC worker が書類確認タスクを実行
   → legal-entity.etzhayyim.com で法人存在確認
   → yabai.etzhayyim.com で制裁スクリーニング
   → 結果: approved / rejected / needs_more_info

3. 工場監査 → HC `factory-audit` タスク生成
   → 現地監査員 (SGS/Intertek/TUV) をアサイン
   → 監査結果を submit → QC レポート記録

4. tsukuru 登録 → KYC + 監査 approved の場合
   → Invoke(tsukr8u0, "register-manufacturer", {...})
   → manufacturer_did + factory_did 発行
   → verification tier = BASIC → VERIFIED 昇格

5. okaimono 利用可能 → tsukuru に登録済み manufacturer を
   → okaimono catalog_upsert の manufacturer_did として指定可能
```

### HC Task Categories (Service Provider)

| Category | 用途 | Difficulty | Reward |
|---|---|---|---|
| `sp-kyc-review` | KYC/KYB 書類審査 | medium | ¥500-1000 |
| `sp-factory-audit` | 現地工場監査 | expert | ¥50,000-200,000 |
| `sp-quality-inspection` | OEM 製品品質検査 | hard | ¥10,000-50,000 |
| `sp-certification-verify` | ISO/CE/UL 等認証確認 | medium | ¥1,000-3,000 |
| `sp-sanctions-review` | 制裁リスト照合レビュー | hard | ¥3,000-5,000 |

### Record Kinds

| Kind | AT Lexicon NSID | 用途 |
|---|---|---|
| `hc_sp_application` | `com.etzhayyim.apps.hc.sp_application` | SP 登録申請 |
| `hc_sp_verification` | `com.etzhayyim.apps.hc.sp_verification` | KYC/KYB 検証結果 |
| `hc_sp_audit` | `com.etzhayyim.apps.hc.sp_audit` | 工場監査結果 |
| `hc_sp_registration` | `com.etzhayyim.apps.hc.sp_registration` | tsukuru 登録完了 |

### Cross-Project Dependencies

| 連携先 | 用途 | Direction |
|---|---|---|
| `tsukuru.etzhayyim.com` | manufacturer/factory DID 登録 | HC → tsukuru (Invoke) |
| `legal-entity.etzhayyim.com` | 法人存在確認 | HC → legal-entity (Invoke) |
| `yabai.etzhayyim.com` | 制裁スクリーニング | HC → yabai (Invoke) |
| `trust.etzhayyim.com` | DID trust score | HC → trust (Invoke) |
| `okaimono.etzhayyim.com` | 登録済み SP を OEM 製造元として利用 | okaimono → tsukuru (query) |
| `credits.etzhayyim.com` | SP 関連 HC タスク完了のクレジット付与 | HC → credits (Invoke) |

### Governance

- **KYC/KYB 必須**: 全 SP は書類検証なしに tsukuru 登録不可
- **制裁スクリーニング**: OFAC/EU/UN リスト照合。hit した場合は即 reject + yabai 通知
- **監査周期**: 初回登録時 + 年次更新。GOLD/DIAMOND tier は半年ごと
- **品質検査**: OEM 製品出荷前に HC worker による inline/final inspection タスクを生成可能

## UI (SuperApp Mobile-First)

- SuperAppTabBar (Home / Talk)
- Sidebar 禁止
- max-w-[600px] モバイル幅統一
- Home タブ: シフト/タスク切替表示
- Talk タブ: Matrix ThreadPanel (全チャンネル)

## Build & Deploy

```bash
cd wasm/etzhayyim-wasm-hc-hc0mp7ng/svelte
pnpm install && pnpm build
cd ..
etzhayyim build
etzhayyim deploy --smoke-url https://hc0mp7ng.etzhayyim.com/health
```
