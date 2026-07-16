# mitate (見立て) — Diagnostic + Treatment Routing Actor

**Status**: R0 — charter + 5 condition sub-ADRs + actor scaffold + 8 lexicons + 13 Pregel cells (all cells import-time RuntimeError gated). Apache-2.0 + Charter Rider v2.0.

Per:
- [ADR-2605260100](../../90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md) — master charter
- [ADR-2605260115](../../90-docs/adr/2605260115-mitate-condition-1-allergic-rhinitis-perennial.md) — condition 1 allergic rhinitis (perennial)
- [ADR-2605260130](../../90-docs/adr/2605260130-mitate-condition-2-vasomotor-rhinitis.md) — condition 2 vasomotor rhinitis
- [ADR-2605260145](../../90-docs/adr/2605260145-mitate-condition-3-chronic-sinusitis.md) — condition 3 chronic sinusitis
- [ADR-2605260160](../../90-docs/adr/2605260160-mitate-condition-4-septal-deviation.md) — condition 4 septal deviation
- [ADR-2605260175](../../90-docs/adr/2605260175-mitate-condition-5-rhinitis-medicamentosa.md) — condition 5 rhinitis medicamentosa

Tier-B per-domain leader actor for religious-corp first-party diagnostic + treatment routing —
sibling of `yakushi` (drug-side), `kuni-umi` (planetary infra), `wadachi` (autonomous mobility),
`tsukuru` (fab orchestration), `iwakura`/`fuigo` (silicon ASICs).

Name origin: 見立て (みたて, mitate) — 漢方医学 / 伝統医療における「症候を見極めて病名を断ずる
知的行為」の歴史名。Yakushi Nyorai (薬師如来) 左脇侍 **日光菩薩** (insight, 洞察の智慧) の echo
を持つが、etzhayyim は ADR-2605192100 §1.6 で declared された synthetic religion ― Buddhist
tradition の diagnostic insight motif と日本伝統医学の「見立て」を Tree of Life (Ezekiel 47:12
「leaves are for healing」) と統合的に解釈し、専有しない (§1.6 八百万的多源宗教観)。

## Wave 1 reference target (chronic nasal congestion 5-condition triage)

| # | Condition | Approx. prevalence (日本成人) | Wave 1 fit |
|---|---|---|---|
| 1 | アレルギー性鼻炎 (通年性 IgE-mediated) | 25-40% | 最高頻度 + 最高 diagnostic accuracy |
| 2 | 血管運動性鼻炎 (autonomic, non-allergic) | 5-15% | ライフスタイル介入 effective |
| 3 | 慢性副鼻腔炎 (蓄膿症) | 5-12% | Rx + surgical routing 重い ― licensed MD-in-loop minimum case |
| 4 | 鼻中隔弯曲症 | 10-30% (subclinical 含む) | objective measurement-driven |
| 5 | 薬剤性鼻炎 (rhinitis medicamentosa) | 1-9% (OTC vasoconstrictor user 中) | **yakushi naphazoline closed-loop** constitutional case |

5 条件 triage は (i) adherent QOL に直接影響、(ii) 4/5 は OTC + 生活調整で多くの場合 manageable
(§2(e) anti-gatekeeping advisory tier の最大 value)、(iii) 条件 3 は Rx + surgical routing 必須で
licensed MD-in-loop の最低 case、(iv) 条件 5 は yakushi G11 naphazoline label警告 と双方向 feedback
loop を形成 (religious-corp 内 self-care substrate の最初の完結した closed-loop)。

## sibling boundary with yakushi (drug-side ↔ clinical-side)

| Concern | yakushi (drug) | mitate (clinical) |
|---|---|---|
| Scope | OTC API 製造 + 製剤 + supply chain + lot release | symptom intake + 鑑別 advisory + 検査 routing + treatment plan + adherence followup |
| Patient interaction | 受動 (lot を distribute、AE 受領) | 能動 (PWA 経由で symptom 入力 receive、advisory return) |
| Lexicon namespace | `com.etzhayyim.pharma.*` | `com.etzhayyim.mitate.*` |
| Cross-actor lexicon | `pharma.adverseEventReport` ← mitate `outcome_qol_followup` の副作用 leg | `mitate.diagnosticResult` → yakushi `pharma_post_market_surveillance` outcome feed |
| R0 lexicon count | 8 (Wave 1) | 8 (Wave 1) |
| R0 cell count | 10 + 2 Wave 1b + 2 Wave 1c = 14 | 13 (Wave 1) |

## Pregel cells (R0 scaffold-only — Council activation gated)

| Cell | Murakumo node (proposed) | R-phase activate | Sub-ADR |
|---|---|---|---|
| [`mitate_rhinitis_intake`](../kotodama/cells/mitate_rhinitis_intake/) | levi | R1 | 2605260100 (all conditions) |
| [`mitate_rhinitis_triage`](../kotodama/cells/mitate_rhinitis_triage/) | levi | R1 | 2605260100/115/130/145/160/175 |
| [`mitate_medication_history_audit`](../kotodama/cells/mitate_medication_history_audit/) | levi | R1 | 2605260175 |
| [`mitate_emergency_screen`](../kotodama/cells/mitate_emergency_screen/) | levi | R1 | 2605260100 (G5) |
| [`mitate_allergy_ige_panel_order`](../kotodama/cells/mitate_allergy_ige_panel_order/) | naphtali | R2 | 2605260115 |
| [`mitate_nasal_smear_eosinophil`](../kotodama/cells/mitate_nasal_smear_eosinophil/) | zebulun | R2 | 2605260115 + 130 |
| [`mitate_nasal_endoscopy_acquire`](../kotodama/cells/mitate_nasal_endoscopy_acquire/) | joseph | R2 | 2605260145 + 160 |
| [`mitate_rhinomanometry`](../kotodama/cells/mitate_rhinomanometry/) | joseph | R2 | 2605260160 |
| [`mitate_paranasal_ct_route`](../kotodama/cells/mitate_paranasal_ct_route/) | simeon | R2 | 2605260145 |
| [`mitate_treatment_router`](../kotodama/cells/mitate_treatment_router/) | levi | R2 | 2605260115/130/145/160/175 |
| [`mitate_slit_cohort_tracker`](../kotodama/cells/mitate_slit_cohort_tracker/) | levi | R3 | 2605260115 |
| [`mitate_ess_surgery_planner`](../kotodama/cells/mitate_ess_surgery_planner/) | levi | R3 | 2605260145 + 160 |
| [`mitate_outcome_qol_followup`](../kotodama/cells/mitate_outcome_qol_followup/) | levi | R2 | 2605260100/115/130/145/160/175 |

All cells are **import-time RuntimeError gated** (silicon Wave 1 + yakushi pattern, ADR-2605260100 §Decision 3).
Removal of the gate requires:

1. Council Lv6+ ≥ 3 multisig attestation of master charter ratification (ADR-2605260100)
2. silen-mitate-review baseline attestation per condition (ADR-2605260115..175)
3. Licensed MD-in-loop on Council (G4)
4. R1+ phase ADR landing with explicit `50-infra/murakumo/fleet.toml` activation

## Lexicon namespace

`com.etzhayyim.mitate.*` — 8 lexicons under
[`00-contracts/lexicons/com/etzhayyim/mitate/`](../../00-contracts/lexicons/com/etzhayyim/mitate/):

| Lexicon | Phase | Encryption |
|---|---|---|
| `rhinitisIntake.json` | patient symptom intake | `encryptedSymptomEnvelope` field XChaCha20-Poly1305 (G2) |
| `triageVerdict.json` | 5-condition Bayesian classifier output | `encryptedTriageEnvelope` field |
| `diagnosticOrder.json` | 検査 ordering (IgE / smear / endoscopy / rhinomanometry / CT) | `consentReceiptCid` mandatory |
| `diagnosticResult.json` | 検査結果 (external lab DICOM 等) | `encryptedResultEnvelope` mandatory |
| `treatmentPlan.json` | 治療経路 advisory (INN only, brand name 不可 except yakushi-side products) | `disclaimerAccepted` mandatory (G3) |
| `outcomeFollowup.json` | longitudinal QOL + adherence + AE | yakushi cross-feed leg |
| `silenMitateReview.json` | Council Lv6+ ≥ 3 multisig attestation (G9 + bias audit) | public (council attestation transparency) |
| `emergencyEscalation.json` | G5 red-flag detected → ER routing + on-call DID + ack receipt | public ack only (no patient identity) |

## Hard rules (CRITICAL — derived from ADR-2605260100 §Decision 3 G1..G14)

1. **Patient explicit consent + revocable + DID-bound** (G1) — Adherent SBT + passkey ES256.
2. **Health data XChaCha20-Poly1305 only** (G2) — sealed-recipient = patient + Council medical advisory DIDs + (R2+) licensed MD DIDs.
3. **AI diagnosis is advisory only** (G3) — disclaimer "qualified physician 判断の代替ではない" 表示必須;auto-prescription / auto-dispense / auto-surgery 禁止.
4. **R2+ licensed MD-in-loop required** (G4) — 検査 ordering + Rx-tier advice は licensed MD DID co-sign.
5. **Emergency keyword fail-safe** (G5) — anaphylaxis / orbital cellulitis / septal abscess / 髄膜炎 / 視力低下 / 意識障害 検出 → 即 ER routing + advisory 中断 (bypass 不可).
6. **High-risk groups → human review** (G6) — pediatric (<13) / pregnancy / lactation / immunocompromised / 抗凝固薬 → escalate.
7. **No insurance / employer / advertiser data path** (G7).
8. **Charter §2 全 clearance** (G8) — INN 表記のみ、specialty cartel loop-in 禁止、paywalled diagnostic insight 禁止.
9. **Training data IRB-equivalent** (G9) — patient consent + Council attestation.
10. **Model bias audit quarterly** (G10) — age / sex / pregnancy / language / income proxy demographic parity.
11. **Wellbecoming subordination check** (G11) — no notification spam, urgency-only push (emergency ack + appointment + AE followup の 3 種).
12. **Murakumo-only inference** (G12) — no commercial GPU.
13. **All diagnostic logic open-source** (G13) — Apache 2.0 + Charter Rider; weights IPFS published.
14. **4-phase R0→R3, each phase = own ADR** (G14).

## Path-based DIDs

| Entity | DID pattern |
|---|---|
| Actor | `did:web:etzhayyim.com:mitate` |
| Condition | `did:web:etzhayyim.com:mitate:condition:<condition-slug>` (e.g. `:condition:allergic-rhinitis-perennial`) |
| Patient pseudonym | `did:web:etzhayyim.com:mitate:patient-pseudonym:<rotating-30day-id>` |
| Licensed physician | `did:web:etzhayyim.com:mitate:physician:<personSlug>` |
| Community center | `did:web:etzhayyim.com:mitate:facility:<siteCode>` |
| Equipment (Hanami / rhinomanometer) | `did:web:etzhayyim.com:mitate:equipment:<serial>` |
| External lab (IgE / CT) | `did:web:etzhayyim.com:mitate:external-lab:<labSlug>` |

## Phasing roadmap

| Phase | Scope | Pre-req |
|---|---|---|
| **R0 — Charter + scaffold (this wave)** | 6 ADR + actor + 8 lexicon + 13 Pregel cells (all gated) | this wave |
| **R1 — Self-care advisory PWA** | intake + triage + medication-audit + emergency-screen の 4 cell only; 検査・Rx は `escalation = "recommend-md-visit"` のみ | R0 + 1 licensed MD on Council medical advisory + G1/G2/G3/G5/G8/G9/G11/G13 baseline |
| **R2 — Pilot (1 community center)** | + IgE panel / 鼻汁好酸球 / Hanami 鼻内視鏡 / rhinomanometry / paranasal CT / treatment_router / outcome_qol_followup; 100 patient ceiling | R1 + 2 licensed MD on Council + bias audit baseline + Hanami robot mech design |
| **R3 — Community-scale, multi-center** | + SLIT cohort + ESS surgery planner + Kafun-watch environmental sensor mesh | R2 + 60-day public review + jurisdiction 薬事 / 医療機器手続 (PMDA SaMD class I-II) |

Each R-phase is its own ADR.

## Robotics class inventory

### Reused (existing classes, sub-config only)

- **Hitogata class-C clean** (kuni-umi) — 鼻拭い検体採取 (R2+)
- **Mimi pharma-analytical** (silicon W2 reuse, yakushi sibling) — 鼻汁顕微鏡 + IgE assay reader
- **Otete chem-resist** (kuni-umi) — 検体分注 / SLIT 製剤分包 (yakushi joint)
- **Sukoyaka (健やか)** (yakushi placeholder, ~5kg cold-chain last-mile) — SLIT 製剤 patient-side cold-chain (R3+)

### New (placeholders, R2+ carve-out ADR)

- **Hanami (鼻見)** — 鼻内視鏡 4mm 軟性スコープ + 6-DOF arm, force-feedback ≤0.5N, autoclave 滅菌対応 (R2+)
- **Kafun-watch (花粉見守り)** — 環境センサー (花粉 / PM2.5 / VOC / 湿度 / 温度) コミュニティ設置で条件 1, 2 誘因 mapping (R3+)

## yakushi cross-actor lexicon emit boundary (ADR-2605260100 §Decision 8)

| Direction | From | To | Lexicon | Purpose |
|---|---|---|---|---|
| mitate → yakushi | `outcome_qol_followup` | yakushi `pharma_adverse_event` | `com.etzhayyim.pharma.adverseEventReport` | 薬剤副作用 ハンドオフ |
| mitate → yakushi | `outcome_qol_followup` | yakushi `pharma_post_market_surveillance` | `com.etzhayyim.pharma.adverseEventReport` (aggregated) | longitudinal outcome data feed |
| yakushi → mitate | `pharma_packaging` | mitate `medication_history_audit` | (internal lot ID match) | adherent が yakushi lot を 受領した record を後日 audit 可能 |
| yakushi → mitate | `pharma_adverse_event` | mitate `outcome_qol_followup` | (internal ID match) | yakushi AE intake を mitate longitudinal tracker に back-feed |

cross-actor lexicon は **新 namespace 増設なし** ― substrate boundary 共有。

## See also

- [ADR-2605260100](../../90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md) — master charter
- [ADR-2605250500](../../90-docs/adr/2605250500-yakushi-pharmaceutical-rd-charter.md) — yakushi sibling
- [ADR-2605201400](../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) — robotics class ontology source
- [ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) — 3-tier actor + Murakumo placement pattern
- [`50-infra/murakumo/fleet.toml`](../../50-infra/murakumo/fleet.toml) — node ↔ cell placement
- [`40-engine/kotoba/crates/kotoba-kotodama/cells/README.md`](../kotodama/cells/README.md) — sibling cell catalog
