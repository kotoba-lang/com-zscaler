# okaimono 御買物 — CLAUDE.md

Global **product-discovery + provisioning-commons** actor at `okaimono.etzhayyim.com`.
ADR-2606012100.

This is the answer to "build an Amazon-like service inside etzhayyim." It is **not** an
Amazon clone — a naïve port is unbuildable here because the charter prohibits Amazon's
three pillars (external `purchase` inflow §1.3, ads/affiliate Charter-Rider §2,
engagement/dark-patterns Wellbecoming §1.13) and the 反個人主義/多世代 ontology. okaimono
instead **inverts** Amazon: each prohibited term is replaced by its charter-aligned dual,
producing a *provisioning commons* organized as three concentric rings.

## The three rings

A member's need enters at Ring 0 and only falls outward when the inner ring cannot satisfy it.

- **Ring 0 — commons-first (the best purchase is no purchase).** borrow/share
  (library-of-things) · repair (→ hodoki) · durable secondhand (SBT↔SBT) · surplus
  redistribution (UNSPSC organism `surplus-routing`). The Wellbecoming inversion of "buy more".
- **Ring 1 — internal economy (SBT↔SBT — clean storefront, ships for real).** Full
  list→compare→basket→checkout over etzhayyim's own producing actors (mitsuho/makura/
  yakushi/tsutae/futawa/hikari/…) + member-to-member surplus. Permitted **now** by
  ADR-2605192115 §3. Settle USDC + ERC-4337 + warifu; TitheRouter 10% → Public Fund.
- **Ring 2 — external world catalog (discovery/compare now; 代理-purchase gated).** Lists
  world products via open standards (GTIN/UNSPSC/GDSN) + vendor-direct feeds + official
  APIs (**data-only, affiliate-stripped**) + gated scraping. §1.3 forbids external purchase
  value flowing INTO etzhayyim, so R0 = **self-checkout handoff** (member pays externally);
  代理-checkout is R3-gated (Lv7+ amendment OR vendor arm, ADR-2605301036).

Every product carries an end-of-life route (`lifecycle`): hodoki / kanayama / haraedo /
kurashimori / wakai. Provisioning history is a kotoba `as-of` Wellbecoming trajectory —
**no final "consumer" state** (mirrors spirit-ontology non-final-state, ADR-2606011500).

## Cells

1. **catalog** (datalog/kotoba): world product registry — internal-actor goods +
   open-standard normalized + vendor-direct + API-data-only + (gated) scraped → `:product/*`.
2. **discover** (langgraph/WASM): need → intent → Ring 0 commons-first → Ring 1 internal →
   Ring 2 external candidate set. Murakumo semantic match (G5).
3. **compare** (langgraph/WASM): Wellbecoming-axis comparison (landed cost / durability /
   repairability / labor-provenance / carbon); aggregate-first, no paid placement (G3).
4. **basket** (langgraph/WASM): multi-source / household / multi-gen basket; landed-cost
   roll-up incl shipping + tariff + tithe.
5. **provision** (langgraph/WASM): checkout router — Ring 1 internal (USDC+TitheRouter+
   warifu, executes) / Ring 2 external (R0 self-checkout handoff; R3-gated 代理).
6. **lifecycle** (datalog/kotoba): end-of-life routing + Wellbecoming `as-of` trajectory.

## Gates (per ADR-2606012100 §Gates)

| Gate | Name | Rule |
|---|---|---|
| G1 | consent-bound | DID-signed consent before need-capture / provisioning |
| G2 | value-inflow-boundary | no external purchase inflow; internal SBT↔SBT only; 代理 R3-gated |
| G3 | no-ads-no-affiliate | product APIs data-only; affiliate stripped; no paid placement |
| G4 | wellbecoming-anti-dark | commons-first; no urgency/scarcity/dark-patterns |
| G5 | murakumo-only | KotobaLLM 127.0.0.1:4000; no external LLM |
| G6 | kotoba-eavt-native | no RW/SQL/Lance as canonical |
| G7 | tithe-non-fiat | USDC+ERC-4337+warifu; TitheRouter 10% auto-split; no fiat |
| G8 | labor-dignity-provenance | etzhayyim logistics, no gig; provenance disclosed |
| G9 | pii-encrypted-envelope | need + history → com.etzhayyim.encrypted.*, DID-bound |
| G10 | catalog-sourcing-legality | robots.txt/ToS/rate-limit/public-only; :representative honesty |
| G11 | outward-gated | live scraping + real 代理-purchase = Council Lv7+ + operator |
| G12 | anti-individualism | household/multi-gen baskets + commons-share first |
| G13 | lifecycle-closure | repair/recycle/disposal route on every product; no final consumer state |
| G14 | member-principal | assisted checkout: member is buyer, okaimono never; no external inflow (§1.3 holds) |
| G15 | no-server-key | member signs each payment (passkey/ERC-4337); okaimono holds no key; server sig refused |

## Layout

```
20-actors/okaimono/
├── manifest.edn          actor manifest (gates, cells, lexicons, integrates)
├── cells/                cell definitions
│   ├── catalog.edn          world-product datalog registry
│   ├── discover.edn         need → 3-ring candidate set (langgraph)
│   ├── compare.edn          Wellbecoming-axis comparison (langgraph)
│   ├── basket.edn           multi-source / multi-gen basket (langgraph)
│   ├── provision.edn        checkout router (langgraph)
│   └── lifecycle.edn        end-of-life routing datalog
├── lex/                  lexicon EDN (product / need / basket / provision)
├── py/                   langgraph python actor (WASM cell) — 6-node graph
└── kotoba/               schema.edn + seed.edn + deploy.sh
```

## Status

🟢 **R0 + R1 + R2 + R3** — design + data-model + **verified Ring 1 / Ring 2 / assisted-checkout logic**.

**R3 (assisted secure checkout, member-principal — landed 2026-06-01):** corrects the
scope-3 framing — okaimono does NOT proxy-buy; the **member is the purchasing principal**
and pays the retailer with their own instrument while the agent provides a secure rail
(safe card / encrypted comms / procedure / delivery). Because value never flows INTO
etzhayyim, **§1.3 holds and no Lv7+ amendment is needed** (only G14/G15/G9/G11). `agent.py`
adds `build_payment_intent` (unsigned, member-principal, `serverHeldKey=false`, warifu-
external trips its own Lv7+ gate), `authorize_payment` (member signature only; server sig
refused — G15), `seal_encrypted` (envelope ref + field names, never plaintext — G9),
`assist_checkout` (awaiting→authorized→submitted; nothing submits without member sig +
operator), `arrange_delivery` (no-gig preferred). +`com.etzhayyim.okaimono.assist` lexicon.
**40/40 tests green.**

**R2 (Ring 2 external catalog, landed 2026-06-01):** the constitutional crux is G3
(no ads/affiliate) + G2 (§1.3). `agent.py` adds `strip_affiliate` (Amazon `tag`/`/ref=`,
`utm_*`, click-ids, `aff_*` … stripped; functional params kept; idempotent),
`normalize_external` (data-only: price/availability/spec only; affiliate/commission/
sponsored/tracking fields dropped by construction), `build_external_handoff` (affiliate-
stripped self-checkout deep-link, no tithe), `scrape_gate` (robots.txt + public-only +
rate budget; live fetch operator-gated G11), `landed_cost_external` (cross-border tariff).
+`:product/retailer-url :availability :tariff-bps` schema; `kotoba/external-catalog.edn`
(4 products, one per source). **30/30 tests green**.

**R1 (Ring 1 internal economy, landed 2026-06-01):** each producing actor owns its goods
catalog in `20-actors/<actor>/products.edn` (SSoT; makura/mitsuho/yakushi/tsutae/futawa/
hikari = 14 SKUs as of 2026-07-07, grew from 11 at R1 landing); `kotoba/ingest_internal.py`
validates + merges → `internal-catalog.edn`.
`py/agent.py` adds: SBT↔SBT eligibility (§3/G2), USDC settlement-intent with **10%
TitheRouter split** (exact `gross=tithe+payout`, intent-only until operator-gated, G7/G11),
order state machine capped at `:in-use` (G13), and no-gig fulfillment routing
(sarutahiko/wadachi/haraedo, G8). `py/test_agent.py` **19/19 green**.

**Honest limits:** R1: no live USDC/TitheRouter broadcast (intent-only); SBT registry is
an attestation map not the on-chain roster; finished-SKU prices `:representative`. R2: no
live retailer API/scrape ingest (all G11-gated; `external-catalog.edn` hand-authored
`:representative`, not fetched); per-provider API ToS for data-only use unverified;
affiliate denylist comprehensive but not exhaustive; no GDSN hierarchy resolution; **Ring 2
代理-purchase (scope 3) does NOT ship — R3-gated**.

## Boundaries

- **vs UNSPSC organism**: the organism classifies + routes internal commodity flows;
  okaimono is the member-facing *discovery/provisioning surface* over it (incl its surplus).
- **vs warifu**: warifu is the settlement card; okaimono is the storefront that invokes it.
- **vs kurashimori**: kurashimori handles post-purchase consumer protection / cooling-off;
  okaimono routes returns/complaints to it (lifecycle), does not adjudicate them.
- **vs toritsugi**: toritsugi is 行政手続き concierge; okaimono is goods-provisioning. Both
  are member-facing concierges and share the "guide + member self-submits" external pattern.
- **vs etzhayyim.ai vendor arm**: a pure commercial marketplace may live vendor-side under
  ADR-2605301036; Ring 2 R3 代理-purchase routes through it. The religious-corp surface
  here stays a provisioning commons.
