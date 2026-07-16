# nyusatsu — Worldwide Public-Procurement Mirror

**DID** `did:web:etzhayyim.com:actor:nyusatsu` · **Tier** B · **Status** R0+R1-offline · **G8 UNLOCKED** (founder Lv7+ 2026-06-27, ADR §8.1) · **ADR** 2606271700 (worldwide), 0035 (JP origin)

> **G8 解禁済み**だが outbound はまだ動かない: live 投稿には **R2 常駐**（autorun + cell + launchd +
> kotoba_bridge）+ アクター封印 did:key + member CACAO leash（G7）が必要。G8 はゲートを外しただけで
> パイプラインは未実装。次の一歩 = R2 実装。

## Quick start

```bash
# from the etzhayyim root (20-actors on the classpath):
bb --classpath 20-actors 20-actors/nyusatsu/run_tests.clj      # 24 tests / 67 assertions green
# offline OCDS ingest (fast path, no LLM); --live is REFUSED (G8):
bb --classpath 20-actors -e "(require 'nyusatsu.methods.ingest) \
  (apply nyusatsu.methods.ingest/-main [\"<ocds-release-package.json>\" \"GB\" \"<issuer-did>\" \"<source-url>\"])"
```

clj methods (cljc): `methods/edn.cljc` (seed reader) · `methods/normalize.cljc` (G1..G10 gates +
OCDS→procurementBid + dedup-by-ocid) · `methods/ingest.cljc` (offline OCDS JSON ingest, `--live`
refused) · `methods/social.cljc` (dry-run multilingual member-signed posts). Seed:
`data/seed-procurement-graph.kotoba.edn` (`:representative`, UA/GB/MX/JP-13).


Worldwide mirror of PUBLIC government procurement (tenders, 入札公告/開札) into the kotoba
Datom log: append-only, **attributed** to each issuer agency, **primary-sourced**, **OCDS-normalized**,
keyed by `ocid`. JP (the original self-hosted NJSS replacement) is now **one jurisdiction adapter
among many**, not the whole actor. Charter spine adopted from kosatsu (高札). See
`90-docs/adr/2606271700-nyusatsu-worldwide-procurement-social-actor.md`.

> Manifest is now `manifest.edn` (EDN). `actor-manifest.jsonld` is frozen legacy (jsonld-retirement
> wave) — kept read-only until the JP HTML pipeline ports to the EDN cells.

## 責務

全世界の政府調達の **一次ソース** を crawl → 正規化 → `com.etzhayyim.apps.govFiscal.procurementBid`
（OCDS 準拠・`ocid` キー・ISO-3166 jurisdiction・ISO-4217 value）として emit → 落札時に
`govFiscal.contract` へ link。OCDS/REST を優先し、構造化フィードが無い source のみ Murakumo で
HTML/PDF 抽出。**有料アグリゲータ（NJSS/官公需ウォッチャー/商用 tender 端末）は引用禁止**（G3）。

source は `registry/sources.edn`（tier = `:supranational`/`:national`/`:subnational`/`:standard-feed`）。
`:standard-feed`（OCDS data registry）の crawl で新規 publisher を発見し shinka ループへ。

## 非責務 (DO NOT)

- 落札予測・入札者ランキング/スコア・談合/汚職の verdict を出さない（**non-adjudicating**, G2）。
- ロビー/影響工作の who-to-target リストにしない（**map-not-target**, G9）。
- `govFiscal.contract` は **issuer agency DID** が owner。nyusatsu は award↔contract の link
  （`resolveAward`）のみ。
- 入札参加者の個人情報（個人事業主住所等）は **PII Tier-3**（ADR-0018）。redaction hook 必須・off-graph 暗号化（G5）。

## Extraction pipeline（cells）

1. `nyusatsu_ocds_ingest` — OCDS/REST source → releases → `procurementBid` datoms（LLM 不要）
2. `nyusatsu_html_extract` — HTML/PDF source → Murakumo structured-extract（fallback）
3. `nyusatsu_normalize_dedup` — `ocid` で MERGE / 通貨・日付・method 正規化 / CPV↔UNSPSC cross-walk
4. `nyusatsu_resolve_award` — XRPC で award↔contract link（award は ≥2 一次引用, G3）
5. `nyusatsu_social_post` — **dry-run** member-signed 多言語 networkPost（sourceLang + en）。
   live 投稿は **Council Lv6+ + operator + member signature**（G8）。

clj methods（kosatsu 規約）: `methods/ingest.clj`（`--live` は R0 で REFUSED）, `methods/normalize.clj`,
`methods/social.clj`, `methods/edn.clj`。cell `.solve()` は Council 批准まで R0 で raise。

## Charter gates

G1 mirror-not-author · G2 non-adjudicating · G3 primary-source-only（≥1 / award ≥2）·
G4 event-log/as-of · G5 PII Tier-3 · G6 robots/legality（≥1500ms） · **G7 no-server-key
（非カストディであって自動化禁止ではない）** · **G8 outward-gated** · G9 map-not-target ·
G10 sourcing-honesty（`:representative` vs `:authoritative`）。

### G7 と G8 は別レバー（混同しない）

- **G7 no-server-key** = *鍵を誰が持つか*（非カストディ／中央集権回避）。プラットフォーム常駐の
  親権的・単独署名鍵を禁じるだけ。**read-only ingest は exempt**（鍵も operator も不要で自律取得、
  ADR-2606072802）。**自律的な書込/投稿も可**（アクター自身の封印 did:key〔Keychain/1Password、
  present-only〕+ member CACAO leash。kaname/ibuki/tsubasa パターン）。**自動化・push の禁止ではない。**
- **G8 outward-gated** = *アウトバウンド公開をいつ許すか*（ガバナンス）。live 投稿・award/contract
  書込は Council Lv6+（= PR-review attestation）+ member 署名で解禁。**read-only ingest は G8 対象外**
  （inbound ≠ outbound）。
- 帰結: **R2 で常駐 + 全世界調達の自律 ingest は可能**（G7 clean・G8 は inbound に無関係）。
  待つのは *outbound* の投稿/書込だけ。常駐アクターが投稿/push すること自体に憲章上の禁止はなく、
  禁じられるのは *親権的中央鍵* が単独でそれを行うこと。

## Source registry（抜粋）

- supranational: EU TED · UN UNGM · World Bank · WTO GPA
- standard-feed: OCDS data registry（publisher 発見）
- national OCDS-native: UA ProZorro · UK FTS/Contracts Finder · MX · AU AusTender · CA CanadaBuys · BR PNCP
- national REST/HTML: US SAM.gov · JP GEPS+省庁 · KR KONEPS · IN CPPP/GeM · SG GeBIZ
- subnational: JP 東京都/横浜市/IPA（47 都道府県 + 市町村は shinka で拡張）

## Related

- ADR-2606271700（本設計） · ADR-2606072000 kosatsu（再利用パターン） · ADR-2606212200 hirameki（worldwide mirror 先例）
- `00-contracts/lexicons/com/etzhayyim/apps/govFiscal/procurementBid.json`（新規・ADR §2）
- `20-actors/danjo/`（JP 政府調達 ingest — 重複は danjo を `govFiscal` consumer 化して reconcile, follow-up）
- `manifest.edn` / `registry/sources.edn`
