# 20-actors/suimin — CLAUDE rules

Tier-B per-domain leader actor for religious-corp first-party **sleep-disorder treatment-EVIDENCE
research + synthesis** (NOT diagnosis, NOT treatment).
Per [ADR-2606072800](../../90-docs/adr/2606072800-suimin-sleep-disorder-evidence-research-charter.md).

Sibling of [mitate](../mitate/) (ADR-2605260100): mitate = symptom-intake diagnostic routing;
suimin = corpus-level treatment-evidence synthesis + referral. Same patient-wellbeing context,
**no diagnostic overlap**. Wave 1 reference = 睡眠時無呼吸症候群 (obstructive + central sleep apnea)
treatment-method evidence landscape.

## One-line identity

suimin ≝ source-whitelist(PubMed/Cochrane/ICSD-3/ICD-11/AASM) × evidence-grade(GRADE)
          × treatment-landscape(集団レベル) × disclaimer(非診断) × referral(地元医療機関)

## Boundaries (NON-NEGOTIABLE — derived from ADR-2606072800 §Decision 3 G1..G13)

| Concern | Allowed | Prohibited |
|---|---|---|
| Scope | corpus-level treatment-evidence synthesis / evidence-grading / referral routing to local sleep clinics | individual diagnosis / severity (AHI) judgment / CPAP pressure or oral-appliance setting / surgical indication / prescription / PSG·OCST interpretation (G4 / N1-N5) |
| Source (G1) | whitelisted `sourceClass` ONLY (cochrane-systematic-review / pubmed-rct / pubmed-systematic-review / aasm-practice-guideline / icsd-3 / icd-11 / pubmed-cohort / pubmed-preprint / jp-sleep-society-guideline) + verifiable provenance (PMID / DOI / Cochrane CD-ID / guideline-ID) | any claim without whitelisted source + provenance; general web / blogs / vendor material / testimonials as evidence (N9) |
| Evidence grade (G2) | explicit GRADE (high / moderate / low / very-low) + studyType on every `treatmentSynthesis` item | ungraded assertion |
| Output framing (G3) | "医師の診断・治療の代替ではない / 睡眠専門医・地元医療機関へ相談を" disclaimer 必須 (every patient-facing output); brand-neutral (device class / INN only) | proprietary diagnosis claim / CPAP brand marketing / paywalled insight / fear-driven re-engagement |
| Referral (G4) | "どこに相談すべきか" の提示まで (睡眠専門外来 / 認定睡眠検査施設 routing) | 予約代行 / 遠隔診療予約 / 機器販売・斡旋 (N6 / N7) |
| Red-flag (G5) | 目撃された無呼吸 + 重度日中傾眠 (運転従事) / 心不全合併 / 小児重度 SAS → mitate emergency 経路 + 速やかな受診推奨 (bypass 不可) | bypass / silent suppression of red-flag signals |
| High-risk groups (G6) | 小児 (<18) / 妊娠期 / 心血管疾患合併 → human review escalate | independent advisory for high-risk groups in R1 |
| PHI (G7, R2+) | `com.etzhayyim.encrypted.*` envelope (XChaCha20-Poly1305, ADR-2605181100); sealed-recipient = patient + Council medical advisory + (R2+) licensed sleep MD | plaintext symptom / 睡眠データ on MST; R0/R1 handling any PHI at all |
| Inference (G10) | Murakumo fleet only (LiteLLM 127.0.0.1:4000 + EVO-X2 + Mac mini gemma) | RunPod / Vertex / OpenAI direct / Anthropic direct from vendor key (§2(i)) |
| Source-integrity (G12) | peer-reviewed sources; preprint labeled as preprint + grade=low; conflict-of-interest sources excluded | predatory journals; preprints presented as established evidence; device-vendor marketing / COI sources |
| Substrate clients | only via `@etzhayyim/sdk` | direct `@atproto/api` / `viem` / IPFS / `@noble/ciphers` / libsignal from app code |
| Payment | USDC + TitheRouter `donation` / `kisha` / `grant` only | Stripe / PayPal / fiat / device-sale revenue |
| Notification | urgency-only (red-flag receipt ack + referral reminder の 2 種) | engagement-maximizing push / "your sleep score" gamification (G11) |
| Server key (G13) | read-only RPC / member-signed | any platform-held private key (ADR-2605231525) |

## Source-whitelist invariant (G1 — most important)

Every `evidenceRecord` / `treatmentSynthesis` claim MUST carry a `sourceClass` ∈ Council-ratified
`sourceWhitelist` AND a verifiable provenance id. A claim without whitelisted source + provenance is
**not emittable** (architectural invariant). Whitelist additions require `silenSuiminReview` scope
`source-whitelist-baseline` with Council Lv6+ ≥3 attestation. See
`00-contracts/lexicons/com/etzhayyim/suimin/sourceWhitelist.json`.

## Disclaimer-gate invariant (G3)

`suimin_disclaimer_gate` is an architectural invariant cell — **all patient-facing output paths MUST
pass through it** (mirror of mitate `emergency_screen`). It cannot be bypassed or silently suppressed.
Disclaimer canonical text lives in `com.etzhayyim.suimin.disclaimerText` (tamper-resistant).

## referral-not-treatment invariant (G4)

suimin NEVER issues, for an individual: a diagnosis, a severity (AHI) judgment, a device setting
(CPAP pressure), a surgical indication, or a prescription. Output is population-level evidence
landscape + referral routing only. This is the constitutional identity (N1-N5) — do not weaken.

## Cell pattern (per ADR-2605192415 §B, mitate mirror)

```
20-actors/magatama/cells/suimin_{cell_name}/
├── README.md     # input/output Lexicon + state schema
├── __init__.py   # one-line module marker
└── cell.py       # multi-gate import-time RuntimeError until Council ratification
```

All 5 suimin_* cells are import-time RuntimeError gated. Removal requires:

- `COUNCIL_CHARTER_ATTESTATION_TX_HASH: str | None = None` → non-None Council Lv6+ ≥3 multisig tx hash (ADR-2606072800)
- `SILEN_SUIMIN_BASELINE_REVIEW_CID: str | None = None` → `com.etzhayyim.suimin.silenSuiminReview` CID, verdict=approve
- `SOURCE_WHITELIST_REGISTRY_CID: str | None = None` → Council-ratified source whitelist (G1)
- For `suimin_referral_router`: additionally `REFERRAL_DIRECTORY_REGISTRY_CID` (G4 local-clinic directory)
- For any R2+ individual-facing cell: additionally `ENCRYPTED_ENVELOPE_RECIPIENT_REGISTRY_CID` (G7) + `LICENSED_SLEEP_MD_REGISTRY_CID` (G8)

The gate is a constitutional invariant — do not remove without an R1+ ADR landing.

## Witness invariant (G9)

- `silenSuiminReview` = ≥3 Council Lv6+ DIDs (+ optional licensed sleep MD)
- `treatmentSynthesis` baseline = (automated synthesis output DID, deterministic) + (R2+ licensed sleep MD co-sign DID)

## Phasing gate

R0 (this wave) is scaffold-only. R1+ requires:

- ADR-2606072800 §Decision 3 G1/G2/G3/G10/G12 baseline (Council Lv6+ ≥3)
- ≥1 licensed sleep-medicine MD on Council medical advisory (R1 evidence-synthesis review)
- For R2: + licensed sleep MD-in-loop (G8) + PHI encryption (G7) + bias audit baseline
- For R3: + 60-day public review + jurisdiction 薬事 / SaMD class confirmation (if applicable)

Do NOT skip phases. Each R transition is its own ADR.

## Substrate-port + non-violation rules

- 7 suimin lexicons use `com.etzhayyim.suimin.*` namespace (actor 名 = namespace, 1:1, condition-agnostic — future 不眠症 / RLS / ナルコレプシー expansion)
- Condition DIDs use **stable slug**: `:condition:sleep-apnea-obstructive`, `:condition:sleep-apnea-central` — never ICD-11 code in the slug (carry ICSD-3 / ICD-11 codes inside `conditionProfile` as coded references)
- Every emitted claim carries provenance (PMID/DOI/CD-ID/guideline-ID) — G1 enforceable lint
- Treatment synthesis text **MUST** be brand-neutral (device class / INN only) except yakushi-distributed products — G12 enforceable lint
- `suimin_disclaimer_gate` **MUST NOT** be bypassed — all patient-facing output passes through it first (architectural invariant)
- R0/R1 handle **no PHI** — corpus-level evidence only; individual intake is R2+ under G7+G8

## See also

- [ADR-2606072800](../../90-docs/adr/2606072800-suimin-sleep-disorder-evidence-research-charter.md) (master)
- [ADR-2605260100](../../90-docs/adr/2605260100-mitate-diagnostic-routing-charter.md) (mitate sibling — diagnostic routing)
- [ADR-2605181100](../../90-docs/adr/2605181100-mst-encrypted-records-signal-keywrap.md) (XChaCha20 envelope — G7)
- [ADR-2605231525](../../90-docs/adr/2605231525-no-server-key-religious-corp-architecture.md) (no-server-key — G13)
- [ADR-2605215000](../../90-docs/adr/2605215000-etzhayyim-inference-murakumo-only-no-runpod.md) (Murakumo-only — G10)
- [`20-actors/mitate/CLAUDE.md`](../mitate/CLAUDE.md) (mitate sibling rules)
- [`20-actors/magatama/cells/README.md`](../magatama/cells/README.md) (sibling cell catalog)
