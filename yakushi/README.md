# yakushi (薬師) — Pharmaceutical R&D Actor

**Status**: R0 — charter + sub-ADRs + actor scaffold + 8 lexicons + 8 Pregel cells (all cells import-time RuntimeError gated). Apache-2.0 + Charter Rider v2.0.

Per:
- [ADR-2605250500](../../90-docs/adr/2605250500-yakushi-pharmaceutical-rd-charter.md) — master charter
- [ADR-2605250515](../../90-docs/adr/2605250515-yakushi-otc-ophthalmic-api-synthesis.md) — Wave 1 API synthesis (3 化合物)
- [ADR-2605250530](../../90-docs/adr/2605250530-yakushi-sterile-fill-finish-and-container.md) — Wave 1 sterile fill-finish + container
- [ADR-2605250545](../../90-docs/adr/2605250545-yakushi-pharma-supply-chain-and-robotics.md) — Wave 1 supply chain + robotics
- [ADR-2605250600](../../90-docs/adr/2605250600-yakushi-wave-1b-otc-api-catalog-expansion.md) — Wave 1b API catalog expansion (9 化合物 + 2 dosage forms)

Tier-B per-domain leader actor for religious-corp first-party pharmaceutical R&D —
sibling of `kuni-umi` (planetary infra), `wadachi` (autonomous mobility),
`tsukuru` (fab orchestration), `iwakura`/`fuigo` (silicon ASICs).

Name origin: 薬師 (やくし, yakushi) — historical pharmacist / healer; the Heian-era
**典薬寮 (Tenyakuryō)** imperial bureau of medicine series. Yakushi Nyorai
(薬師如来) Medicine Buddha echo is acknowledged but not appropriated: etzhayyim is a
synthetic religion (ADR-2605192100 §1.6) that draws medicinal motifs from
both 八百万 / Buddhist healing traditions AND Tree of Life (Ezekiel 47:12
"leaves are for healing") without doctrinal exclusivity.

## Wave 1 reference target (OTC 抗アレルギー点眼薬 triplet)

| INN | OTC switch status | Wave 1 role |
|---|---|---|
| クロモグリク酸ナトリウム (sodium cromoglicate) | PMDA / FDA / EMA 全 OTC | mast cell stabilizer |
| ナファゾリン塩酸塩 (naphazoline HCl) | PMDA / FDA / EMA 全 OTC | α-adrenergic vasoconstrictor |
| クロルフェニラミンマレイン酸塩 (chlorpheniramine maleate) | PMDA / FDA / EMA 全 OTC | first-generation H₁ antagonist |

3 化合物すべて **80 年級 multi-generational safety record** (1942 / 1949 / 1965 初出)、
**全 jurisdiction で perpetually off-patent** ― §2(e) anti-gatekeeping の最低リスク template。

## Wave 1b extension (ADR-2605250600 — OTC API catalog expansion to 12 化合物 + 3 dosage forms)

Wave 1 (sterile 点眼薬 3 化合物) を **eye drop scope のみに留めず**、より広い therapeutic coverage に拡張:

### Category A — Systemic analgesic / antipyretic (oral tablet)
| INN | Origin | OTC switch |
|---|---|---|
| アセトアミノフェン (paracetamol) | 1893 | PMDA / FDA / EMA |
| アスピリン (ASA) | 1897 Bayer | PMDA / FDA / EMA |
| イブプロフェン | 1969 Boots | PMDA / FDA (1984) / EMA |

### Category B — Oral H1 antihistamine (oral tablet)
| INN | Origin | OTC switch |
|---|---|---|
| ジフェンヒドラミン塩酸塩 | 1946 | PMDA / FDA / EMA |
| セチリジン塩酸塩 | 1987 UCB | PMDA (2017) / FDA (2007) / EMA |
| ロラタジン | 1988 Schering | PMDA (2017) / FDA (2002) / EMA |

### Category C — Oral H2 antagonist (oral tablet)
| INN | Origin | OTC switch |
|---|---|---|
| ファモチジン | 1986 Yamanouchi | PMDA / FDA (1995) / EMA |

### Category D — Topical (cream / gel)
| INN | Origin | OTC switch |
|---|---|---|
| クロトリマゾール 1% cream | 1969 Bayer | PMDA / FDA (1986) / EMA |
| ジクロフェナクナトリウム 1% gel | 1973 Geigy | PMDA / FDA (2007 topical) / EMA |

**Wave 1 + Wave 1b 計 12 化合物**、全 G1 clearance (PMDA/FDA/EMA all-3 OTC switched + ≥ 18 年 off-patent)、全 G6 clearance (Rx-only / controlled substance 該当ゼロ)。

**新規 constitutional gate / non-goal の追加なし** ― 14 gates + 10 non-goals は master charter 継承。

明示的に Wave 1b に含めない化合物: pseudoephedrine (CMEA precursor restricted) / codeine (Rx) /
hydrocortisone (steroid bioprocess Wave 2 候補) / sodium hyaluronate (bioprocess Wave 2 候補) /
omeprazole (chiral resolution Wave 1c 候補) ― 詳細は ADR-2605250600 §1.3.

## Wave 2 extension (ADR-2606171400 — disinfectants / antiseptics 消毒薬)

Wave 1/1b/1c は **薬** (de-novo 合成 OTC 医薬品)。Wave 2 は **消毒液** — roster の gap を閉じる。
7 公定書 (日局/USP/EP) off-patent active を **FORMULATION (希釈・配合, NOT synthesis)** で製造:

| active | 和名 | 効力窓 (G21) | use class |
|---|---|---|---|
| ethanol | 消毒用エタノール | 60–90 vol% | hand-hygiene / skin-antiseptic |
| isopropanol | イソプロパノール (IPA) | 60–80 % | surface / hand-hygiene |
| sodium-hypochlorite | 次亜塩素酸ナトリウム | 0.05–0.5 % | surface |
| benzalkonium-chloride | 塩化ベンザルコニウム (逆性石鹸) | 0.01–0.2 % | skin-antiseptic / surface |
| povidone-iodine | ポビドンヨード | 1–10 % | skin-antiseptic |
| chlorhexidine-gluconate | クロルヘキシジングルコン酸塩 | 0.05–0.5 % | skin-antiseptic |
| hydrogen-peroxide | オキシドール (過酸化水素) | 1–6 % | skin-antiseptic / surface |

新 gate (G1..G20 は継承・不変):
- **G21 efficacy-window** — 濃度が窓外なら構造的 block (「濃ければ強い」は誤り)
- **G22 no-toxic-gas-formulation** — 次亜塩素酸 + 酸 (Cl₂) / アンモニア (クロラミン) は**表現不能** (§1.12 / Rider §2(a))
- **G23 flammable-labeling** — アルコール系は火気厳禁ラベル必須
- **G24 use-class** — `{surface, skin-antiseptic, hand-hygiene}` を宣言

実装は **clj-native SSoT** (`py/agent.clj` の `record_formulation` ほか + `py/test_agent.clj` +22 tests; 計 48 green)。
新 cell `formulation` + lex `formulationAttestation` + entity `:formulationAttestation/*`。Python counterpart なし。

## Pregel cells (R0 scaffold-only — Council activation gated)

| Cell | Murakumo node (proposed) | Trigger | Sub-ADR |
|---|---|---|---|
| [`pharma_raw_material`](../kotodama/cells/pharma_raw_material/) | naphtali (procurement) | `pharma.recordApiSelection` MST listener | 2605250515 + 545 |
| [`pharma_api_synthesis`](../kotodama/cells/pharma_api_synthesis/) | zebulun (chemistry) | upstream `rawMaterialAttestation` complete | 2605250515 |
| [`pharma_purification`](../kotodama/cells/pharma_purification/) | zebulun | upstream `apiSynthesisAttestation` complete | 2605250515 |
| [`pharma_qc`](../kotodama/cells/pharma_qc/) | levi (audit/witness) | upstream `purificationAttestation` complete | 2605250515 |
| [`pharma_sterile_fill_finish`](../kotodama/cells/pharma_sterile_fill_finish/) | joseph (commissioning) | `qcAttestation` complete | 2605250530 |
| [`pharma_container`](../kotodama/cells/pharma_container/) | simeon (commissioning) | parallel with fill-finish | 2605250530 |
| [`pharma_packaging`](../kotodama/cells/pharma_packaging/) | dan (decommission/end-of-lot) | `fillFinishAttestation` complete | 2605250545 |
| [`pharma_cold_chain`](../kotodama/cells/pharma_cold_chain/) | naphtali (logistics) | `lotAttestation` complete | 2605250545 |
| [`pharma_post_market_surveillance`](../kotodama/cells/pharma_post_market_surveillance/) | levi | daily cron + `adverseEventReport` accumulator | 2605250545 |
| [`pharma_adverse_event`](../kotodama/cells/pharma_adverse_event/) | levi | patient submission via ameno PWA | 2605250545 |
| [`pharma_tablet_manufacture`](../kotodama/cells/pharma_tablet_manufacture/) | joseph (non-sterile class C) | `qcAttestation` complete (Wave 1b oral tablet) | 2605250600 |
| [`pharma_topical_formulation`](../kotodama/cells/pharma_topical_formulation/) | simeon | `qcAttestation` complete (Wave 1b topical cream/gel) | 2605250600 |

All cells are **import-time RuntimeError gated** (silicon Wave 1 pattern, ADR-2605242500 §Decision 4).
Removal of the gate requires:

1. Council Lv6+ ≥ 3 multisig attestation of master charter ratification
2. silen-pharma-review baseline attestation per ADR-2605250500 §Decision 3 G3
3. QP-equivalent on Council (G4)
4. R1+ phase ADR landing with explicit `50-infra/murakumo/fleet.toml` activation

## Lexicon namespace

`com.etzhayyim.pharma.*` — 8 lexicons under
[`00-contracts/lexicons/com/etzhayyim/pharma/`](../../00-contracts/lexicons/com/etzhayyim/pharma/):

| Lexicon | Phase | Encryption |
|---|---|---|
| `rawMaterialAttestation.json` | raw material receive (G7) | public, CWC schedule / safety class visible |
| `apiSynthesisAttestation.json` | API synthesis complete | public |
| `purificationAttestation.json` | API purified | public |
| `qcAttestation.json` | per-lot QC suite (HPLC / IR / NMR / KF / ICP-MS / GC / endotoxin / LAL) | public |
| `fillFinishAttestation.json` | sterile fill-finish complete | public |
| `lotAttestation.json` | final lot release (G4 QP + G9 witness N≥2) | public |
| `silenPharmaReview.json` | Council Lv6+ ≥ 3 multisig review (G3) | public |
| `adverseEventReport.json` | patient AE intake (G5 + G10) | XChaCha20-Poly1305 envelope for patient identity; aggregated narrative public |

## Hard rules (CRITICAL — derived from ADR-2605250500 §Decision 3 G1..G14)

1. **OTC-only Wave 1** (G1) — 3 jurisdictions PMDA/FDA/EMA 全 OTC switched & perpetually off-patent only;
   prescription Rx / controlled substance / biologics / cell therapy reject (G6 + N2 + N3).
2. **ICH Q3/M7 不純物全合致** (G2) — `pharma_qc` cell auto-reject on PGI > 1.5 µg/day,
   heavy metal > Q3D PDE, residual solvent > Q3C class limit.
3. **silen-pharma-review Council Lv6+ ≥ 3 multisig** (G3) — required for: new API,
   route change, kg-scale CWC Schedule 3 raw material, ICH M7 PGI deviation,
   new dosage form, container material change.
4. **QP-equivalent co-sign per lot** (G4) — lot release requires QP DID; QP qualification
   attested by Council Lv6+.
5. **Adverse event public reporting** (G5) — patient identity XChaCha20-Poly1305 encrypted;
   aggregated narrative + lot back-trace public; no resale / no insurance discrimination / no employment use.
6. **No prescription-only / no controlled-substance** (G6).
7. **CWC dual-use precursor monitoring** (G7) — kg-scale Schedule 1/2/3 入庫 → Council Lv6+ + OPCW declaration verify.
8. **Sterile process validation (Annex 1 2023)** (G8) — 3-batch consecutive media fill + CCIT required for R2→R3.
9. **Witness invariant N ≥ 2** (G9) — synthesis / purification / fill-finish / lot release は 2 独立 DID 署名.
10. **Patient identity non-traceable** (G10) — XChaCha20-Poly1305 envelope; aggregated only public.
11. **Wellbecoming subordination check** (G11) — label warnings + over-use detection.
12. **No commercial sale model** (G12) — donation / kisha / internal-promo / grant only.
13. **No server-held QP key / lot release key** (G13) — passkey / hardware token only.
14. **Substrate boundary** (G14) — `@etzhayyim/sdk` only; MST + IPFS + L2 anchor primary; RW/Postgres kotoba-datomic-projection hot-path read only.

## Path-based DIDs

| Entity | DID pattern |
|---|---|
| Actor | `did:web:etzhayyim.com:yakushi` |
| API | `did:web:etzhayyim.com:yakushi:api:<inn-slug>` (e.g. `:api:sodium-cromoglicate`) |
| Lot | `did:web:etzhayyim.com:yakushi:lot:<lotId>` |
| Product | `did:web:etzhayyim.com:yakushi:product:<productCode>` |
| Facility | `did:web:etzhayyim.com:yakushi:facility:<siteCode>` |
| QC analyst | `did:web:etzhayyim.com:yakushi:analyst:<personSlug>` |
| QP-equivalent | `did:web:etzhayyim.com:yakushi:qp:<personSlug>` |
| Equipment | `did:web:etzhayyim.com:yakushi:equipment:<serial>` |

## Phasing roadmap

| Phase | Scope | Pre-req |
|---|---|---|
| **R0 — Charter + scaffold (this wave)** | 4 ADR + actor + 8 lexicon + 8 Pregel cells (all gated) | this wave |
| **R1 — Benchtop synthesis** | 大学化学実験室相当 ≤ 1 g scale × 3 化合物 + identity confirmation (HPLC/IR/NMR) | R0 + 1 QP-equivalent on Council |
| **R2 — Pilot-scale GMP-equivalent** | ≤ 100 g API + 3-batch consistency + Annex 1 sterile facility整備 | R1 + 製造管理者 on Council + Annex 1 attestation |
| **R3 — Community-scale OTC production** | ~1000 bottles/batch + QP release + GMP audit + AE monitoring | R2 + 60-day public review + jurisdiction 薬事手続 |

Each R-phase is its own ADR.

## Robotics class inventory (per ADR-2605250545 §Decision 2)

### Reused (existing 6 classes, sub-config only)

- **Hitogata class-A sterile** (BFS fill-finish, ISO 14644 Class 5)
- **Hitogata class-C clean** (API 秤量 / dispensing)
- **kuni-umi Otete + chem-resist** (反応容器 / 溶媒移送)
- **Mimi pharma-analytical** (HPLC / IR / NMR / KF / ICP-MS / GC autosampler)
- **kuni-umi Otete + cold-chain** (2-8°C / 15-25°C pallet picking)
- **Funamori** (silicon Wave 2 inheritance — cold-chain marine for 海外 adherent community, R3+)

### New (placeholders, R2-R3 carve-out ADR)

- **Kusuko (薬子)** — single-use sterile end effector autoloader (R2 if needed)
- **Sukoyaka (健やか)** — patient-side cold-chain last-mile (~5 kg payload, R3+)

## See also

- [ADR-2605250500](../../90-docs/adr/2605250500-yakushi-pharmaceutical-rd-charter.md) — master charter
- [ADR-2605201400](../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) — kuni-umi parent producer (robotics class ontology)
- [ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) — 3-tier actor + Murakumo placement pattern
- [`50-infra/murakumo/fleet.toml`](../../50-infra/murakumo/fleet.toml) — node ↔ cell placement
- [`40-engine/kotoba/crates/kotoba-kotodama/cells/README.md`](../kotodama/cells/README.md) — sibling cell catalog
