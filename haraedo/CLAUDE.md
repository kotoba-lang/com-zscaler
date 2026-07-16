# haraedo 祓戸 — CLAUDE.md

Global **bulky-waste (粗大ゴミ) disposal** actor — two-sided: **citizen intake**
(classify / quote / schedule / sticker) + **operator logistics** (受付 / 配車 /
ルート / 担当者) + a **worldwide processing-facility registry**. ADR-2606010200.

Named for the **Haraedo-no-Ōkami** (祓戸大神) — the Shinto purification deities who
carry impurities away to be dissolved: the mythic image of collecting refuse and
routing it to be processed/purified.

## Role

Two graphs over one kotoba EAVT graph:

1. **intake** (citizen side, langgraph/WASM): classify item → quote fee → match an
   accepting facility with capacity → offer a collection slot → issue a sticker id →
   emit `:application/*`. Member self-applies under DID-signed consent (G1).
2. **dispatch** (operator side, langgraph/WASM): for a jurisdiction + date — gather
   applications → cluster by area → assign vehicle (capacity-checked) → assign crew
   (shift + cert checked) → optimize route (NN + 2-opt over collection points) →
   select destination facility (capacity + accepted-category) → emit `:route/*` plan.

Backed by two datalog registries: **facility_registry** (worldwide ゴミ処理場:
位置 / 種別 / 処理能力 / 受入品目) and **fleet_registry** (車両 + 担当者 per自治体).

## Gates (per ADR-2606010200 §G)

| Gate | Name | Rule |
|---|---|---|
| G1 | consent-bound | member DID-signed consent before any application/collection action |
| G2 | no-illegal-dumping | only verified facilities + licensed routes |
| G3 | hazardous-boundary | 家電リサイクル法/PCB/asbestos/医療廃棄物/batteries → licensed handler |
| G5 | labor-dignity | 担当者 per Labor Liberation ladder; no extractive gig dispatch |
| G6 | pii-encrypted | member address/PII → com.etzhayyim.encrypted.* envelope, DID-bound |
| G7 | fee-non-fiat | 手数料 via USDC/warifu or XRPC backend (領収書用途のみ); never fiat processor |
| G11 | outward-gated | real receipt/dispatch/collection requires Council + operator; R0 design-only |
| G14 | verified-facility | destinations + fees only from verified registry |
| G15 | capacity-honest | facility/vehicle/crew capacity are hard constraints; no silent truncation |

## Layout

```
20-actors/haraedo/
├── manifest.edn          actor manifest (gates, cells, lexicons)
├── cells/                Pregel/langgraph + datalog cell definitions
│   ├── intake.edn            citizen-intake langgraph cell
│   ├── dispatch.edn          operator dispatch/route langgraph cell
│   ├── facility_registry.edn worldwide ゴミ処理場 datalog registry
│   └── fleet_registry.edn    vehicle + crew datalog registry
├── lex/                  lexicon EDN (application + facility)
├── py/                   langgraph python actor (WASM cell) — intake + dispatch graphs
├── kotoba/               schema.edn + seed.edn + deploy.sh
└── *.md                  docs
```

## Boundary

- **toritsugi**: general-procedure concierge; haraedo owns bulky-waste logistics
  and MAY be surfaced through toritsugi/LINE.
- **hodoki 解き / kanayama 金山**: post-collection dismantling + circular recovery;
  haraedo hands material *to* facilities, it does not reprocess.
- **danjo / kanae**: oversight/visualization, not service delivery.
- **chigiri**: any licensing/permit legal procedure (UPL-bounded).
- **kotoba-native**: facility/fleet/crew/route/application are kotoba EAVT facts.
- **Murakumo-only LLM**: inference via 127.0.0.1:4000 (gemma3:4b).

## Status

R0 + **R1** — design + scaffold + verified logic (`py/test_agent.py`, 22 tests).
R1 adds: per-jurisdiction **fee models** (free/per-item/per-sticker/per-weight/flat),
capacity-honest **slot calendar** (`:slot/*`, booked≤capacity), and **capacitated
multi-vehicle VRP** (Clarke-Wright savings + per-route 2-opt; over-capacity routes
surfaced as `unassigned`, G15). All outward action (real receipt / dispatch /
collection) gated by **G11** (Council ratification + community operator) and by
kotoba **operator auth** on writes. Facility/fee/slot seed is representative
(`:sourcing :representative`), not authoritative coverage.
