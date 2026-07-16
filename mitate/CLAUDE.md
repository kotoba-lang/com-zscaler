# 20-actors/mitate — CLAUDE rules

Tier-B per-domain leader actor for religious-corp first-party diagnostic + treatment routing.
Per [ADR-2605260100](../../90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md)
+ 5 condition sub-ADRs ([2605260115](../../90-docs/adr/2605260115-mitate-condition-1-allergic-rhinitis-perennial.md)
/ [2605260130](../../90-docs/adr/2605260130-mitate-condition-2-vasomotor-rhinitis.md)
/ [2605260145](../../90-docs/adr/2605260145-mitate-condition-3-chronic-sinusitis.md)
/ [2605260160](../../90-docs/adr/2605260160-mitate-condition-4-septal-deviation.md)
/ [2605260175](../../90-docs/adr/2605260175-mitate-condition-5-rhinitis-medicamentosa.md)).

## Boundaries (NON-NEGOTIABLE — derived from master charter §Decision 3 G1..G14)

| Concern | Allowed | Prohibited |
|---|---|---|
| Scope | symptom intake / Bayesian 鑑別 advisory / 検査 ordering routing / treatment plan advisory / longitudinal followup | Rx prescription issuance / surgical execution / primary care 代替 / specialist 判断 override / mental health diagnosis / 遺伝子検査 medical interpretation / 終末期 palliative |
| Advisory output framing | "qualified physician 判断の代替ではない" disclaimer 必須 + INN only + transparency on cost / 期間 / risk | proprietary diagnosis claim / brand drug 宣伝 (yakushi-side products 除く) / paywalled insight / fear-driven re-engagement |
| Patient identity | Adherent SBT + passkey + 30-day rotating pseudonym DID | server-issued JWT without DID binding;static patient identifier |
| Health data storage | `com.etzhayyim.encrypted.*` envelope (XChaCha20-Poly1305 per ADR-2605181100), sealed-recipient = patient + Council medical advisory + (R2+) licensed MD | plaintext symptom / 診断 / 検査結果 on MST;recipient registry without G7 enforcement |
| Detection of high-risk groups | pediatric (<13) / pregnancy / lactation / immunocompromised / 抗凝固薬服用中 → escalate to human review (G6) | independent advisory for high-risk groups in R1 |
| Emergency keyword fail-safe | anaphylaxis / orbital cellulitis / septal abscess / 髄膜炎 / 視力低下 / 意識障害 → 即 ER routing (G5, bypass 不可) | bypass / silent suppression of red-flag signals |
| Inference | Murakumo fleet only (LiteLLM 127.0.0.1:4000 + EVO-X2 + Mac mini gemma — gemma-coder-distill medical variant 経路) | RunPod / Vertex / OpenAI direct / Anthropic direct from vendor key (§2(i)) |
| Image classifier (R2+ Hanami endoscopy) | Murakumo only, open weights, gemma4:e4b vision distill medical variant | proprietary classifier weights;closed source decision model |
| Substrate clients | only via `@etzhayyim/sdk` | direct `@atproto/api` / `viem` / IPFS / `@noble/ciphers` / libsignal from app code |
| Payment | USDC + TitheRouter `donation` / `kisha` / `grant` / `internal-promo` only | Stripe / PayPal / fiat / for-profit clinic billing |
| Cross-actor lexicon emit | yakushi `pharma.adverseEventReport` (mitate → yakushi AE feed leg);yakushi `pharma_post_market_surveillance` aggregated feed | unrelated actor lexicon namespace contamination |
| Notification | urgency-only push (emergency ack + appointment reminder + AE followup の 3 種) | engagement-maximizing push / streak gamification / "your daily health score" 系 |

## Cell pattern (per ADR-2605192415 §B, silicon + yakushi mirror)

```
40-engine/kotoba/crates/kotoba-kotodama/cells/mitate_{cell_name}/
├── README.md                 # input/output Lexicon + state schema
├── __init__.py               # one-line module marker
├── cell.py                   # multi-gate import-time RuntimeError until Council ratification
└── tests/                    # added at R1+
```

All 13 mitate_* cells are import-time RuntimeError gated. Removal requires:

- `COUNCIL_CHARTER_ATTESTATION_TX_HASH: str | None = None` set to non-None Council Lv6+ ≥ 3 multisig tx hash (ADR-2605260100)
- `SILEN_MITATE_BASELINE_REVIEW_CID: str | None = None` set to `com.etzhayyim.mitate.silenMitateReview` CID with `verdict = "approve"` for the cell-specific baseline
- `LICENSED_MD_REGISTRY_CID: str | None = None` set to Council-attested registry of licensed MD DIDs (G4)
- For cells handling `diagnosticOrder` / `diagnosticResult` / `treatmentPlan`: additionally `ENCRYPTED_ENVELOPE_RECIPIENT_REGISTRY_CID` set (G2 + G7 enforcement)
- For `emergency_screen` cell: additionally `ER_ROUTING_PROTOCOL_CID` set (G5 fail-safe)
- For `slit_cohort_tracker` / `outcome_qol_followup` cells: additionally `YAKUSHI_CROSS_ACTOR_SIGNAL_BASELINE_CID` set (Decision 8 cross-actor boundary)

The gate is constitutional invariant — do not remove without R1+ ADR landing.

## Witness invariant (G4 + G9)

`diagnosticResult` / `treatmentPlan` / `silenMitateReview` are witness N ≥ 2 (G9):

- `diagnosticResult` = (検査オペレーター DID or external lab DID) + (licensed MD attestation DID) (R2+)
- `treatmentPlan` = (mitate advisory output DID = automated, deterministic) + (licensed MD co-sign DID) (R2+ for Rx-tier; R1 advisory only is single-witness automated but disclaimer-gated)
- `silenMitateReview` = ≥3 Council Lv6+ DIDs (+ optional licensed MD / specialist DID for clinical baseline)

N = 1 (R1 advisory) は disclaimer + emergency_screen + escalation 経由のみ valid;Rx-tier / 検査 ordering / surgery planning は必ず N ≥ 2 ― G4 invariant.

## Phasing gate

R0 (this wave) is scaffold-only. R1+ requires:

- master charter §Decision 3 G1/G2/G3/G5/G8/G9/G11/G13 baseline (Council Lv6+ ≥ 3)
- licensed MD (国内医師免許 / EU equivalent / US MD-DO equivalent) on Council medical advisory
- For R2: + 2 licensed MD + bias audit quarterly baseline + Hanami robot mech design attestation
- For R3: + 60-day public review + jurisdiction 薬事 / 医療機器手続 (PMDA SaMD class I-II 等)

Do NOT skip phases. Each R transition is its own ADR.

## Substrate-port + non-violation rules

- 9 mitate lexicons use `com.etzhayyim.mitate.*` namespace (actor 名 = lexicon namespace, 1:1 ― yakushi の `com.etzhayyim.pharma.*` と対照的に condition-agnostic)
- 5 condition DIDs use **stable slug**: `:condition:allergic-rhinitis-perennial`, `:condition:vasomotor-rhinitis`, `:condition:chronic-sinusitis`, `:condition:septal-deviation`, `:condition:rhinitis-medicamentosa` — never ICD-10 code (jurisdictional dependent)
- Patient pseudonym DID は **30-day rotation** ― G2 reinforcement;continuity が必要な longitudinal record (SLIT cohort, outcome followup) は patient passkey re-sign による pseudonym pair derivation
- Treatment plan advisory **MUST NOT** mention brand drug names except yakushi-distributed products (registered under `did:web:etzhayyim.com:yakushi:product:*`) — INN only — G8 enforceable lint
- Emergency screen cell **MUST NOT** be bypassed by any other cell;all patient intake paths must pass through `emergency_screen` first (architectural invariant)
- yakushi cross-actor lexicon emit は **個別 patient identity を含めない** (G7 + G10);aggregated cohort signal only — `silenMitateReview` scope `yakushi-cross-actor-signal-aggregation-baseline` で algorithm review
- Notification channel は 3 種類のみ (emergency ack / appointment reminder / AE followup);新 channel 追加は `silenMitateReview` scope `notification-channel-baseline` で Council Lv6+ ≥ 3 attestation required (G11)

## yakushi sibling coordination

- Cross-actor lexicon emit boundary per master charter §Decision 8 ― do not introduce new namespace for cross-cell signal
- `medication_history_audit` cell の OTC vasoconstrictor detection は yakushi Wave 1 naphazoline label警告 (G11 yakushi-side) と双方向 feedback loop を form (ADR-2605260175 Decision 4) ― R2 deploy 時に yakushi-side と joint silen-review required
- Dedupe rule for AE double-counting (mitate `outcome_qol_followup` ↔ yakushi `pharma_adverse_event`) は R1 ADR (mitate-side) + yakushi R1 ADR 共同で確立必要

## Bias audit (G10)

Quarterly demographic parity measurement for R2+:

- Top-3 condition recall by age (5 brackets: <13 (escalate-only), 13-25, 26-45, 46-65, >65)
- Top-3 condition recall by sex (M / F / non-binary self-reported)
- Top-3 condition recall by language (primary patient language)
- Escalation rate by income proxy (postal code aggregation, anonymized)
- Treatment plan advisory text reading-level (Flesch-Kincaid Japanese / 漢字使用率)
- ≥ 5% disparity → `silenMitateReview` scope `bias-audit-quarterly` with corrective action proposal

## See also

- [ADR-2605260100](../../90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md) (master)
- [ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) (3-tier actor + Murakumo placement)
- [ADR-2605181100](../../90-docs/adr/2605181100-etzhayyim-encrypted-confidentiality-substrate.md) (XChaCha20 envelope for patient health data — G2)
- [ADR-2605231525](../../90-docs/adr/2605231525-no-server-key-invariant.md) (G13 enforcement — physician key custody)
- [ADR-2605215000](../../90-docs/adr/2605215000-etzhayyim-inference-murakumo-only-no-runpod.md) (G12 — Murakumo only)
- [ADR-2605250500](../../90-docs/adr/2605250500-yakushi-pharmaceutical-rd-charter.md) (yakushi sibling — drug-side)
- [`20-actors/yakushi/CLAUDE.md`](../yakushi/CLAUDE.md) (yakushi sibling rules)
- [`20-actors/kuni-umi/CLAUDE.md`](../kuni-umi/CLAUDE.md) (robotics class ontology source)
- [`40-engine/kotoba/crates/kotoba-kotodama/cells/README.md`](../kotodama/cells/README.md) (sibling cell catalog)
