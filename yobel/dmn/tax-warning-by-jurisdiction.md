# DMN — Tax Warning by Jurisdiction

Per [ADR-2605201800](../../../90-docs/adr/2605201800-etzhayyim-yobel-debt-release-actor.md).
Gate for `verifyEligibility` cell — produces per-jurisdiction COD (cancellation-of-debt) income / tithe / Schuldenerlass tax warnings.

**Warnings are informational only.** Tax advice 提供は禁止 — `lawfirm.etzhayyim.com` (vendor) に delegate。

**Hit policy**: COLLECT (multiple jurisdictions can fire; all warnings appended).

## Inputs

| Name | Type | Source |
|---|---|---|
| `debtorJurisdictionIso3` | `string` | derived from debtor DID |
| `creditorJurisdictionIso3` | `string` | derived from creditor DID |
| `riteType` | `enum` | `declareRite.riteType` |
| `releasedMicroUsdc` | `integer` | `recordRelease.releasedMicroUsdc` |
| `releasedUsdc` | `integer` | `releasedMicroUsdc ÷ 10^6` (derived for threshold comparisons) |
| `debtorTaxResident` | `boolean` | self-declared (out-of-band) |

## Outputs

| Name | Type |
|---|---|
| `warnings` | `string[]` |
| `severity` | `enum(info, caution, high)` (max of all fired rules) |
| `consultLegalDelegate` | `boolean` (true if any rule severity ≥ caution) |

## Rules

| # | debtorJurisdictionIso3 | creditorJurisdictionIso3 | riteType | releasedUsdc | → `warning` | `severity` |
|---|---|---|---|---|---|---|
| **R1** | `USA` | `*` | `*` | `≥ 1` | "US IRC §61(a)(11): cancellation-of-debt income is generally taxable. Exclusions: insolvency (§108(a)(1)(B)), qualified principal residence (§108(a)(1)(E)), Title 11 bankruptcy (§108(a)(1)(A)). Report on Form 982 + Form 1099-C from creditor." | `caution` |
| **R2** | `USA` | `*` | `religious_jubilee` | `*` | "US IRC §170(c)(1): gifts from religious organizations may have different treatment than commercial debt forgiveness. Consult tax delegate." | `caution` |
| **R3** | `JPN` | `*` | `*` | `≥ 1` | "日本所得税法 §36(1) + §44-2: 債務免除益は原則として一時所得または雑所得。資力喪失中の免除は §44-2 適用で非課税の余地。確定申告で消費税法 §63 課税仕入れ調整が必要な場合あり。" | `caution` |
| **R4** | `JPN` | `*` | `tokusei_rei` | `*` | "徳政令型 rite は歴史的には公権力の宣言だが、本 actor の declared rite は私的合意。所得税法 §36 適用は通常通り。" | `info` |
| **R5** | `DEU` | `*` | `*` | `≥ 1` | "Deutsches EStG §15 + §17: Schuldenerlass kann Betriebseinnahme darstellen. §3 Nr. 66 Sanierungsklausel applies in restructuring context only — voluntary religious release likely not covered." | `caution` |
| **R6** | `GBR` | `*` | `*` | `≥ 1` | "UK Income Tax (Trading and Other Income) Act 2005 §249: release of debt deemed income if previously deductible. Charity exemption (CTA 2010 §471) does not auto-extend to religious-corp voluntary rite — check ESC C16 / SP D32." | `caution` |
| **R7** | `FRA` | `*` | `*` | `≥ 1` | "Code général des impôts art. 39-1 + abandon de créance doctrine: abandon de créance à caractère commercial = recette imposable; à caractère financier = neutre. Religieux voluntary release: position fiscale incertaine." | `caution` |
| **R8** | `ISR` | `*` | `shmita_7yr ∨ yobel_50yr` | `*` | "Israel: שמיטת כספים (prozbul institution per Hillel) historically routes around shmita debt cancellation; modern Israeli law (Pkudat Mas Hachnasa) does not auto-recognize religious shmita as tax-exempt cancellation." | `caution` |
| **R9** | `*` (any) | `*` | `*` | `≥ 1000000` | "Releases ≥ $1M USDC trigger many jurisdictions' anti-abuse / disguised-gift rules. Coordinate with vendor:lawfirm.etzhayyim.com before settlement." | `high` |
| **R10** | `*` | `*` | `*` | `≥ 100` | "Release amount may exceed gift tax annual exclusion in many jurisdictions. Verify jurisdiction-specific gift tax rules." | `info` |
| **R11** | `USA` | `USA` | `*` | `≥ 600` | "US IRS Form 1099-C threshold (≥ $600). Creditor may have reporting obligation independent of yobel rite." | `info` |
| **R12** | `*` | `*` | `political_amnesty` | `*` | "Political amnesty operates under sovereign decree referenced in declareRite.doctrinalBasis — tax treatment determined by that decree's terms, not by this actor's defaults." | `info` |

## Aggregation

`severity` = max over fired rules (`info < caution < high`).
`consultLegalDelegate` = (severity ≥ `caution`).
`warnings[]` = all fired rule strings, deduplicated, in rule-number order.

## Implementation note

DMN は `cells/release_settlement/nodes.py` の eligibility eval step で実行 + `recordRelease` lexicon response の `jurisdictionNotes` に warnings 配列を文字列化して埋め込む。詳細 advice は vendor:lawfirm.etzhayyim.com の `runConflictCheck` / `searchPrecedent` cross-actor invoke で取得 (yobel 自身は tax advice を出さない)。
