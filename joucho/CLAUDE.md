# etzhayyim-project-joucho — 情緒 Well-Becoming Scoring

**joucho.etzhayyim.com** — Well-Becoming 観点でモノ・場所・サービスの質を評価するスコアリングプラットフォーム。

## Core Concept

**場所・食事・商品・建物の「情緒的価値」を Well-Becoming 5 軸で定量化する。** 数値スペックだけでなく、心身が整う体験の質を scoring。

```
対象 (飲食店/スポット/商品/建物/食事)
  → 情緒スコア (5 軸 Well-Becoming 評価)
  → omatsuri 未病クレジットへの貢献度
  → society6 Kyu/Dan への反映
```

## Architecture

- **performerType**: `service`
- **DID**: `did:web:joucho.etzhayyim.com`
- **uiType**: `redirect`
- **LLM**: Murakumo Opus 4.6 (`claude-opus-4-6`)
- **Pattern**: Single Worker + multi-DID + W Protocol Event Stream + joucho 情緒 cadence heartbeat
- **2 次ソース**: Follow-based (maps/omatsuri/okaimono/kaigo から受信)

## CRITICAL: Platform Heartbeat Cadence Provider

→ `etzhayyim dodaf tv1 query --id etzhayyim-project-joucho-platform-heartbeat-cadence-provider` / MCP `etzhayyim.dodaf.tv1.query`

## Scoring 対象 × 軸

| 対象 | path-based DID | scoring 観点 |
|---|---|---|
| 食事・メニュー | `meal` | 栄養バランス、未病貢献度、食材透明性 |
| 飲食店 | `restaurant` | 健康メニュー率、雰囲気、アクセシビリティ |
| スポット・施設 | `spot` | 運動環境、交流機会、自然・緑 |
| 商品 | `product` | Well-Becoming 貢献度、安全性、持続可能性 |
| 建物 | `building` | バリアフリー、健康建築、コミュニティ性 |

## Well-Becoming 5 軸 Mapping

| 軸 | Weight | 情緒 scoring での意味 | data source |
|---|---|---|---|
| engagement | 20% | 利用者の関与度・リピート率 | joucho 自身 (review/visit 集計) |
| competence | 20% | 対象の専門性・品質 (栄養学的正しさ等) | omatsuri (未病基準) + dojo |
| contribution | 20% | 地域・社会への貢献度 | society6 |
| growth | 20% | 改善傾向・進化度 | joucho 自身 (時系列トレンド) |
| resilience | 20% | 災害時・非常時の対応力、持続可能性 | kaigo + maps |

## Score Model

```
joucho_score (0-100) = Σ (axis_weight × axis_score)
```

| Grade | Range | 意味 |
|---|---|---|
| S | 90-100 | 卓越 — Well-Becoming を体現 |
| A | 75-89 | 優秀 — 高い情緒的価値 |
| B | 60-74 | 良好 — 基本的な Well-Becoming 貢献 |
| C | 40-59 | 普通 — 改善余地あり |
| D | 0-39 | 要改善 |

## Lexicon Collections

`com.etzhayyim.joucho.{review,score,meal_score,restaurant_score,spot_score,product_score,building_score,score_history}`

## WIT

- Domain: `etzhayyim:joucho@1.0.0` (`wit/joucho/package.wit`)
- Export: `etzhayyim:joucho/scoring@1.0.0`
- Import: `kotodama:div/health`, `kotodama:div/recreation`, `kotodama:contract/agreement`

## Follow Graph (2 次ソース)

| Upstream | 受信データ | 用途 |
|---|---|---|
| omatsuri | 未病基準、栄養データ | competence 軸 scoring |
| maps | 空間データ、施設情報 | spot/building scoring |
| okaimono | 商品カタログ | product scoring |
| kaigo | ケア適合性、バリアフリー | resilience 軸 scoring |
| society6 | Kyu/Dan 軸定義 | scoring framework |
| dojo | competence/resilience drill data | competence 軸 calibration |
