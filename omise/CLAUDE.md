# etzhayyim-project-omise

omise.etzhayyim.com — Shopify-like multi-tenant EC marketplace。Platform / Seller (出店者) / Buyer (購入者) の三者構造。出店→商品登録→販売→決済→出荷→精算の全フローを AI Actor が自律運営。

## Architecture

```
Browser → omise.etzhayyim.com (Svelte CSR)
  → XRPC /xrpc/com.etzhayyim.apps.omise.*
    ↓
  App: etzhayyim-wasm-omise-om1s3sh0p
    ├─ Platform:  approveSeller / suspendSeller / listPendingSellers / resolveDispute / platformAnalytics
    ├─ Seller:    registerSeller / updateSellerProfile / getSellerProfile
    │   ├─ Catalog:     createProduct / updateProduct / archiveProduct / listSellerProducts / updateInventory
    │   ├─ Fulfillment: listSellerOrders / acceptOrder / rejectOrder / markReadyToShip / requestPickup
    │   ├─ Finance:     getSellerBalance / requestPayout / listSettlements / getSellerRevenue
    │   └─ Marketing:   createCoupon / deactivateCoupon / listCoupons / applyCoupon
    ├─ Buyer:     searchProducts / getProduct / addToCart / removeFromCart / getCart / clearCart
    │   ├─ Checkout:  createOrder (SAGA) / getOrder / listOrders
    │   └─ Review:    submitReview / listReviews
    ├─ Logistics: createShipment / updateShipmentStatus / getShipment / listShipments
    └─ kagami graph (RisingWave Hyperdrive, P10v2 GraphAr)
```

## Data Path (kagami)

```
Write: sdk.pds.dispatch({ type: "com.atproto.repo.createRecord", collection, record })
  → PDS commit pipeline → Graph Worker → Hyperdrive RisingWave
  → graphar.vertex_{label} INSERT

Read: createKyselyDb(env.HYPERDRIVE).selectFrom("vertex_{label}").where(...).execute()
  → Hyperdrive → RisingWave
  → Hyperdrive RisingWave SELECT from graphar.vertex_{label}
```

(SQL transpiler は 2026-04-13 に archived。Kysely + raw SQL のみ。)

## Actor Composition (Multi-DID) — 三者構造

```
did:web:omise.etzhayyim.com                              (controller — marketplace)

# ── Platform (marketplace governance) ──
did:web:omise.etzhayyim.com:actor:platformAdmin           (ISCO 1120 — seller approval, suspension, governance)
did:web:omise.etzhayyim.com:actor:platformSupport         (ISCO 4222 — dispute resolution, escalated CS)

# ── Seller 出店者 ──
did:web:omise.etzhayyim.com:actor:sellerOnboarding        (ISCO 3339 — registration, KYC, profile setup)
did:web:omise.etzhayyim.com:actor:sellerCatalog           (ISCO 5221 — product listing, pricing, inventory)
did:web:omise.etzhayyim.com:actor:sellerFulfillment       (ISCO 4321 — order processing, packing, ship-ready)
did:web:omise.etzhayyim.com:actor:sellerFinance           (ISCO 3313 — settlement, payout, revenue)
did:web:omise.etzhayyim.com:actor:sellerMarketing         (ISCO 2431 — coupons, promotions, campaigns)

# ── Buyer 購入者 ──
did:web:omise.etzhayyim.com:actor:buyerAssistant          (ISCO 5223 — cart, checkout, order tracking)
did:web:omise.etzhayyim.com:actor:buyerReview             (ISCO 2641 — product reviews, ratings)

# ── Shared ──
did:web:omise.etzhayyim.com:actor:logistics               (ISCO 4323 — shipment, carrier integration)
did:web:omise.etzhayyim.com:actor:analyst                  (ISCO 2120 — platform + seller KPI analytics)
```

## ISCO Actor → Command Mapping

### Platform

| Actor | ISCO | Commands |
|---|---|---|
| `:actor:platformAdmin` | 1120 | approveSeller, suspendSeller, listPendingSellers |
| `:actor:platformSupport` | 4222 | resolveDispute |

### Seller (出店者)

| Actor | ISCO | Commands |
|---|---|---|
| `:actor:sellerOnboarding` | 3339 | registerSeller, updateSellerProfile, getSellerProfile, listSellers |
| `:actor:sellerCatalog` | 5221 | createProduct, updateProduct, archiveProduct, listSellerProducts, updateInventory |
| `:actor:sellerFulfillment` | 4321 | listSellerOrders, acceptOrder, rejectOrder, markReadyToShip, requestPickup |
| `:actor:sellerFinance` | 3313 | getSellerBalance, requestPayout, listSettlements, getSellerRevenue |
| `:actor:sellerMarketing` | 2431 | createCoupon, deactivateCoupon, listCoupons, applyCoupon |

### Buyer (購入者)

| Actor | ISCO | Commands |
|---|---|---|
| `:actor:buyerAssistant` | 5223 | addToCart, removeFromCart, getCart, clearCart, createOrder, getOrder, listOrders |
| `:actor:buyerReview` | 2641 | submitReview, listReviews |

### Shared

| Actor | ISCO | Commands |
|---|---|---|
| `:actor:logistics` | 4323 | createShipment, updateShipmentStatus, getShipment, listShipments |
| `:actor:analyst` | 2120 | platformAnalytics, getSellerRevenue |

## kagami Graph Labels (P10v2 GraphAr-native)

1 AT record = 1 row (typed columns, val 廃止)。RisingWave `graphar.vertex_{label}` に自動ルーティング。

| SQL Label | RisingWave Table | Key Columns | RLS |
|---|---|---|---|
| `OmiseSeller` | `graphar.vertex_OmiseSeller` | seller_id, owner_did, store_name, description, category, currency, commission_rate, bank_info, status | org_id, user_id, actor_id, created_at |
| `OmiseProduct` | `graphar.vertex_OmiseProduct` | product_id, seller_id, name, description, price, currency, inventory, category, image_url, variants_json, status | org_id, user_id, actor_id, created_at |
| `OmiseCart` | `graphar.vertex_OmiseCart` | cart_id, user_did, items_json | org_id, user_id, actor_id, created_at |
| `OmiseOrder` | `graphar.vertex_OmiseOrder` | order_id, seller_id, user_did, items_json, subtotal, discount, total_amount, currency, status, payment_method, payment_intent_id, shipping_address, coupon_id | org_id, user_id, actor_id, created_at |
| `OmiseShipment` | `graphar.vertex_OmiseShipment` | shipment_id, order_id, seller_id, carrier, tracking_number, status | org_id, user_id, actor_id, created_at |
| `OmiseSettlement` | `graphar.vertex_OmiseSettlement` | settlement_id, seller_id, order_id, gross_amount, commission, net_amount, status | org_id, user_id, actor_id, created_at |
| `OmiseCoupon` | `graphar.vertex_OmiseCoupon` | coupon_id, seller_id, code, discount_type, discount_value, min_order_amount, max_uses, used_count, status, expires_at | org_id, user_id, actor_id, created_at |
| `OmiseReview` | `graphar.vertex_OmiseReview` | review_id, product_id, seller_id, user_did, rating, comment | org_id, user_id, actor_id, created_at |
| `OmiseDispute` | `graphar.vertex_OmiseDispute` | order_id, resolution, refund_amount | org_id, user_id, actor_id, created_at |
| `OmisePayout` | `graphar.vertex_OmisePayout` | payout_id, seller_id, amount, currency, status | org_id, user_id, actor_id, created_at |
| `OmisePickupRequest` | `graphar.vertex_OmisePickupRequest` | order_id, carrier, pickup_date, status | org_id, user_id, actor_id, created_at |

## Checkout SAGA (createOrder)

```
validateCart → checkInventory → reserveStock → applyCoupon (discount)
  → Invoke("did:web:kakin.etzhayyim.com", "processPayment", {amount, currency, method})
    → kakin: payment intent → stripe/credits
  → confirmOrder → sellerFulfillment notification
  → acceptOrder (seller) → markReadyToShip → requestPickup
  → createShipment (logistics) → delivered → settlement

Compensation:
  payment failure → releaseStock
  seller reject → refundPayment + releaseStock
```

## Settlement Flow (Seller Finance)

```
order completed + delivered confirmed
  → platform commission deduction (rate from OmiseSeller.commission_rate)
  → OmiseSettlement record (settlement_id, seller_id, order_id, gross_amount, commission, net_amount)
  → requestPayout (seller) → Invoke("did:web:kakin.etzhayyim.com", "transferPayout")
```

## Integration

| Service | Role |
|---|---|
| kakin.etzhayyim.com | Payment intent + confirm + refund + seller payout |
| credits.etzhayyim.com | Murakumo credit ledger |
| stripe.etzhayyim.com | Card auth execution |
| i18n.etzhayyim.com | Product description translation |
| moderator.etzhayyim.com | Seller KYC verification |

## Build & Deploy

```bash
cd 60-apps/etzhayyim-project-omise/wasm/etzhayyim-wasm-omise-om1s3sh0p/svelte
pnpm install && pnpm build
cd ..
etzhayyim build
etzhayyim deploy --smoke-url https://om1s3sh0p.etzhayyim.com/health
```
