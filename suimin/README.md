# suimin (睡眠)

Religious-corp first-party **sleep-disorder treatment-EVIDENCE research + synthesis** actor.
Per [ADR-2606072800](../../90-docs/adr/2606072800-suimin-sleep-disorder-evidence-research-charter.md).

> suimin は **診断もしないし治療もしない**。信頼できるソースだけから治療法のエビデンスを束ねて
> 平易に提示し、地元の医療機関に繋げる。

## What it does

1. ある睡眠障害 (Wave 1 = **睡眠時無呼吸症候群** OSA/CSA) の治療法を、
2. **信頼性の確立したソースのみ** から拾い —
   PubMed/MeSH 一次文献 (RCT / 系統的レビュー / コホート) + Cochrane 系統的レビュー +
   ICSD-3 (国際睡眠障害分類) + ICD-11 + AASM 臨床診療ガイドライン + 各国睡眠学会ガイドライン、
3. **GRADE 等の明示的エビデンスグレード付き** で治療選択肢の landscape を合成し
   (CPAP / 口腔内装置 / 体位療法 / 減量・生活習慣 / 上気道手術 / 舌下神経刺激 等)、
4. **「医師の診断・治療の代替ではない / 睡眠専門医・地元医療機関へ相談を」** という disclaimer を必ず付し、
5. **地元の睡眠医療機関** (睡眠専門外来 / 認定睡眠検査施設) への **referral routing** に繋げる。

## What it does NOT do (constitutional, ADR-2606072800 N1-N10)

- 個人の SAS 診断・重症度 (AHI) 判定をしない
- CPAP 圧・口腔内装置の個人設定をしない
- 手術適応・術式の個人判断をしない
- 処方・薬剤推奨 (個人向け) をしない
- 終夜睡眠検査 (PSG) / 簡易検査 (OCST) を判読しない
- 医療機器・CPAP の販売・斡旋をしない
- 遠隔診療予約・予約代行をしない
- whitelist 外ソース (一般 web / ブログ / ベンダー資料 / 体験談) をエビデンスとして提示しない

## Sibling

[mitate (見立て)](../mitate/) — symptom-intake 診断 routing。
mitate が個人症状を見立てて受診 routing し、suimin が治療法エビデンスを束ねて提示する補完関係。

## Status

**R0 — scaffold-only.** 5 Pregel cells (all import-time RuntimeError gated) + 7 lexicons + master ADR.
Evidence ingest / synthesis is not live. See
[`CLAUDE.md`](./CLAUDE.md) for boundaries and the phasing gate (R0 → R3).

## Reliable sources (G1 whitelist)

`com.etzhayyim.suimin.sourceWhitelist` — Council-ratified の採用可能ソースクラスのみ。
全主張は whitelisted `sourceClass` + verifiable provenance (PMID / DOI / Cochrane CD-ID / guideline-ID)
を持つ。それ以外はエビデンスとして提示しない。
