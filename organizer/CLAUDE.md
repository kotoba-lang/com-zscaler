# etzhayyim-project-organizer — organizer.etzhayyim.com

> **kotoba-native (ADR-2606072400).** Canonical manifest is now `manifest.edn`; data model in
> `kotoba/schema.edn`; logic + tests in `py/` (14 green). Free auto-organize file commons:
> content-addressed dedup (Blake3), per-vault isolation, encrypted blobs, member-signed, and
> **no content mining**. Legacy `actor-manifest.jsonld` (RisingWave) is DEPRECATED
> (`DEPRECATED-jsonld.md`); subscription-discovery pipeline retained as a follow-up. Below is historical.

**Upload → AI Auto-Organize** — アップロードすれば AI が自動で整理。フォルダ手動整理不要。

## Architecture

→ nanoid / domain: `deps.toml [[mitama_actors]]`

| 項目 | 値 |
|---|---|
| Runtime | **Single Worker** (TS Native) |
| performerType | `service` (default sensitivity: `confidential`) |
| uiType | `appview` (Protocol Canvas card) |
| Replaces | `etzhayyim-project-drive` (Connect RPC + base64, 旧設計) |

## Multi-DID Architecture `[DESIGN]`

| DID | 用途 |
|---|---|
| `did:web:organizer.etzhayyim.com` | Controller (app 本体) |
| `did:web:organizer.etzhayyim.com:vault:{nanoid}` | ユーザー個人 vault |
| `did:web:organizer.etzhayyim.com:collection:{nanoid}` | 共有コレクション |

## Design E 3-Tier Write

| Tier | 用途 | 関数 | Collection NSID |
|---|---|---|---|
| **1 Social** | 整理完了通知 | `AppBskyFeedPost(vaultDID, text)` | `app.bsky.feed.post` |
| **2 Domain** | item/classification/tag/collection/rule | `ComAtprotoRepoCreateRecord(kind, payload)` | `com.etzhayyim.apps.organizer.*` |
| **3 State** | 表示設定、AI preferences | `Preferences()` | server-side |

## Domain Record Types (Tier 2, camelCase) `[DESIGN]`

| Kind | NSID | 内容 |
|---|---|---|
| `item` | `com.etzhayyim.apps.organizer.item` | アップロードアイテム (blob_ref, filename, content_type, size, blake3, vault_did) |
| `classification` | `com.etzhayyim.apps.organizer.classification` | AI 分類結果 (item_rkey, category, subcategory, labels[], confidence, model) |
| `tag` | `com.etzhayyim.apps.organizer.tag` | タグ (item_rkey, name, source: "ai"/"manual") |
| `collection` | `com.etzhayyim.apps.organizer.collection` | コレクション定義 (name, description, visibility, auto_rules[]) |
| `collection_item` | `com.etzhayyim.apps.organizer.collection_item` | コレクション↔アイテム関連 |
| `organize_rule` | `com.etzhayyim.apps.organizer.organize_rule` | 自動整理ルール (condition, action, priority) |

## Subscription Discovery Pipeline (mailer → organizer → kaiyaku) `[DESIGN]`

**メール課金通知からサブスクを自動検出し、不要契約の解約を kaiyaku に委譲する。**

> **kaiyaku now exists** (ADR-2606112201, `20-actors/kaiyaku/`, 🟡 R0): 縁切り executor —
> en-ledger + burden analyzer (this pipeline's disclosed thresholds) + dependency
> cascade-guard + T1/T2/T3 severance plans (karakuri tiers; dry-run only, execution
> Council-gated). Canonical DID `did:web:etzhayyim.com:actor:kaiyaku`
> (aka `did:web:kaiyaku.etzhayyim.com` referenced below).

### Data Flow

```
mailer.etzhayyim.com (inboundEmail record, Follow-based input)
  → organizer handleComAtprotoSyncSubscribeReposCommit
    → Murakumo LLM 分類: "subscriptionBilling" 検出
    → ComAtprotoRepoCreateRecord("subscriptionItem", {...})
    → 同一 sender 反復パターン検出 → スコアリング
    → ComAtprotoRepoCreateRecord("subscriptionAnalysis", {...})
    → AppBskyFeedPost("3件の未使用サブスクを検出")
    → WprotoConvoCreateDm(userDID, "subscription-review", recommendations)
      → User 承認 (yoro convo chat)
        → Invoke("did:web:kaiyaku.etzhayyim.com", "start-cancellation", params)
          → kaiyaku browser automation → 解約完了
```

### Subscription Record Types (Tier 2, camelCase)

| Kind | NSID | 内容 |
|---|---|---|
| `subscriptionItem` | `com.etzhayyim.apps.organizer.subscriptionItem` | 検出したサブスク (sender, serviceName, amount, currency, billingCycle, firstSeenAt, lastSeenAt, emailCount) |
| `subscriptionAnalysis` | `com.etzhayyim.apps.organizer.subscriptionAnalysis` | 分析結果 (subscriptionItemRkey, usageScore, costPerMonth, recommendation: "keep"/"review"/"cancel", reason) |
| `subscriptionReviewJob` | `com.etzhayyim.apps.organizer.subscriptionReviewJob` | ユーザーへのレビュー依頼ジョブ (status: "pending"/"reviewed"/"actioned", userDecision) |

### Subscription Graph Schema

| Node Label | ID Prefix | 用途 |
|---|---|---|
| `SubscriptionItem` | `org:sub-` | サブスクサービス (serviceName, sender, amount, cycle) |
| `SubscriptionAnalysis` | `org:ana-` | 月次分析スナップショット (usageScore, recommendation) |

| Edge | 意味 |
|---|---|
| `DETECTED_FROM` | SubscriptionItem → Item (元メール) |
| `ANALYZED_BY` | SubscriptionAnalysis → SubscriptionItem |
| `CANCELLATION_REQUESTED` | SubscriptionItem → KaiyakuService (kaiyaku graph) |

### Subscription Detection (Reactive, Follow-based)

```
mailer inboundEmail commit → handleComAtprotoSyncSubscribeReposCommit
  → collection == "com.etzhayyim.apps.mailer.inboundEmail"
    → Murakumo LLM 分類:
      - subject/body パターン: "ご利用明細", "自動更新", "subscription", "invoice", "receipt"
      - 抽出: serviceName, amount, currency, billingCycle
    → G("SubscriptionItem").Match(Eq{"serviceName": name}).Query()
      → 既存あり: lastSeenAt/emailCount 更新
      → 新規: ComAtprotoRepoCreateRecord("subscriptionItem", {...})
```

### Monthly Analysis (Shinka heartbeat 駆動)

```
resolveHeartbeatCadence → mood: "analytical" (月初)
  → G("SubscriptionItem").Match(Eq{"org_id": orgId}).Return("*").Query()
  → 各 subscription:
    - usageScore 算出 (メール頻度, 最終利用日, 月額コスト)
    - recommendation: usageScore < 20 && costPerMonth > 500 → "cancel"
    - recommendation: usageScore < 50 → "review"
    - else → "keep"
  → ComAtprotoRepoCreateRecord("subscriptionAnalysis", {...})
  → "cancel"/"review" あり → WprotoConvoCreateDm + AppBskyFeedPost
```

### Subscription WIT Capability

```wit
interface subscription-discovery {
    detect-subscription: func(params: string) -> result<string, string>;
    analyze-subscriptions: func(params: string) -> result<string, string>;
    get-recommendations: func(params: string) -> result<string, string>;
}
```

### Subscription Cross-actor Integration

| From | To | Interface | 用途 |
|---|---|---|---|
| organizer | mailer | `Follow` (Design E) | inboundEmail commit を reactive 受信 |
| organizer | kaiyaku | `Invoke("did:web:kaiyaku.etzhayyim.com", "start-cancellation", params)` | ユーザー承認後に解約ジョブ投入 |
| kaiyaku | organizer | `AppBskyFeedPost` (agent mention) | 解約完了通知 → subscriptionItem を archived に更新 |
| organizer | stripe | `Invoke("did:web:stripe.etzhayyim.com", "list-transactions", params)` | カード明細取得 (Stripe Issuing 利用者のみ) |

### Subscription Sensitivity & Consent

| 対象 | Sensitivity | Consent |
|---|---|---|
| inboundEmail 読取 | `confidential` | mailer → organizer Follow (ユーザー有効化時) |
| subscriptionItem | `confidential` | vault owner のみ |
| kaiyaku 解約実行 | `confidential` | **明示承認必須** — convo chat approve |
| stripe 明細取得 | `confidential` | `kotodama:consent` GNAP grant 必須 |

## Reactive Pipeline (ComAtprotoSyncSubscribeRepos) `[DESIGN]`

- `com.etzhayyim.apps.organizer.item` create → Murakumo vision/NLP auto-classify → classification + tag + collection auto-assign
- `com.etzhayyim.apps.organizer.classification` create → パターン分析 → organize_rule 提案

## Sensitivity & Governance `[DESIGN]`

| 対象 | Sensitivity | 理由 |
|---|---|---|
| Item blob | `confidential` | 個人ファイルデフォルト非公開 |
| Classification | `internal` | AI 推論結果 (vault owner のみ) |
| Collection (private) | `confidential` | owner のみ |
| Collection (shared) | `internal` | 共有先 DID のみ |
| Collection (public) | `public` | Tier 1 Social |

## Blob Storage `[DESIGN]`

- FormData + multipart 必須 (base64 禁止)
- Client Blake3 事前計算 → checkBlobExists → linkBlob (dedup) or upload
- >64 MiB: 自動 multipart chunked parallel
- B2 path: `blobs/{vault_did_hash}/{blake3}.{ext}`

## File Structure

```
60-apps/etzhayyim-project-organizer/
├── CLAUDE.md
├── wit/organizer/package.wit        # Domain WIT capability
└── wasm/etzhayyim-wasm-organizer-org4n1z3/
    ├── src/app.ts                      # TS Native — Design E reactive pipeline
    ├── go.mod
    ├── kotodama.jsonld
    └── wit/world.wit                # Component WIT (contract + capability export)
```
