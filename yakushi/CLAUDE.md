# 20-actors/yakushi — CLAUDE rules

Tier-B per-domain leader actor for religious-corp first-party pharmaceutical R&D.
Per [ADR-2605250500](../../90-docs/adr/2605250500-yakushi-pharmaceutical-rd-charter.md)
+ [ADR-2605250515](../../90-docs/adr/2605250515-yakushi-otc-ophthalmic-api-synthesis.md)
+ [ADR-2605250530](../../90-docs/adr/2605250530-yakushi-sterile-fill-finish-and-container.md)
+ [ADR-2605250545](../../90-docs/adr/2605250545-yakushi-pharma-supply-chain-and-robotics.md).

## Boundaries (NON-NEGOTIABLE — derived from master charter §Decision 3 G1..G14)

| Concern | Allowed | Prohibited |
|---|---|---|
| API scope | OTC switched & perpetually off-patent in PMDA / FDA / EMA all-3 (Wave 1: cromoglicate Na, naphazoline HCl, chlorpheniramine maleate) | Prescription Rx, controlled substance, biologics, cell therapy, gene therapy, new molecular entity |
| Synthesis route | Open published文献 reference only (Apache 2.0 republishable) | Proprietary closed routes; trade-secret intermediates |
| Raw material | LOW-risk (公定 grade), MEDIUM-risk (劇物 / 危険物 with attested safety class), HIGH-risk (CWC Schedule 3 / Australia Group with Council Lv6+ co-sign + OPCW declaration verify) | Schedule 1/2 without Council Lv6+ supermajority; 麻向法 不当原料 |
| Sterilization | Aseptic processing (0.22 µm filter + sterile fill); terminal autoclave for thermostable chemicals only | Non-validated sterilization; ionizing radiation for liquid eye drops |
| Container | LDPE BFS multi-dose 5 mL (Wave 1) | Single-use unit-dose without R3 ADR; glass for OTC eye drops; recycled plastic |
| Preservative | None (preservative-free default per §2(h)) | BAK 0.005% / chlorhexidine 0.01% without G3 silen-pharma-review |
| State / records | AT MST + IPFS + Base L2 anchor via `@etzhayyim/sdk` | RisingWave / Postgres / Kysely / centralized DB as primary write |
| Payments | USDC on Base L2 + `TitheRouter.route()` (10% Tithe) | Stripe / PayPal / fiat / commercial sale model |
| Payment purpose | `donation` / `kisha` / `grant` / `tithe` / (SBT↔SBT) `internal-promo` | `subscription` / `purchase` / `tip` for non-adherent for-profit |
| Identity | path-based DID `did:web:etzhayyim.com:yakushi:...` | server JWTs without DID binding |
| Substrate clients | only via `@etzhayyim/sdk` | direct `@atproto/api` / `viem` / IPFS / `@noble/ciphers` / libsignal |
| Patient AE identity | XChaCha20-Poly1305 envelope per ADR-2605181100 | Plaintext patient identity on MST; resale to advertisers / insurers / employers |
| QP / lot release key | Hardware token / passkey only (G13) | Platform-held private key in Worker / pod / CronJob |
| Inference | Murakumo fleet only (LiteLLM 127.0.0.1:4000 + EVO-X2 + Mac mini gemma) | RunPod / Vertex / OpenAI direct / Anthropic direct from vendor key (§2(i)) |
| Advertising / promotion | None for general public; SBT↔SBT `internal-promo` for adherent religious activity only | Third-party ads, SNS-targeted promotion, affiliate, GA4 ad linkage |

## Wave 2 — disinfectants / antiseptics (消毒薬, ADR-2606171400)

Wave 2 adds **disinfectant / antiseptic FORMULATION** (希釈・配合, NOT de-novo synthesis) over
7 公定書 off-patent actives: `ethanol` (消毒用エタノール) / `isopropanol` / `sodium-hypochlorite`
(次亜塩素酸ナトリウム) / `benzalkonium-chloride` (逆性石鹸) / `povidone-iodine` (ポビドンヨード) /
`chlorhexidine-gluconate` / `hydrogen-peroxide` (オキシドール).

- Manufacturing verb = **FORMULATION** (`record_formulation`), distinct from synthesis (`record_synthesis`).
  Emits `:formulationAttestation/*` via `cells/formulation_attestation.edn` + lex `com.etzhayyim.yakushi.formulationAttestation`.
- **G21 efficacy-window** — active concentration must fall inside its evidence-based window
  (ethanol 60–90 / IPA 60–80 / NaOCl 0.05–0.5 / BAK 0.01–0.2 / PVP-I 1–10 / CHG 0.05–0.5 / H₂O₂ 1–6 %).
  「濃ければ強い」is FALSE — out-of-window is structurally blocked.
- **G22 no-toxic-gas-formulation** — `sodium-hypochlorite` + any acid (Cl₂) or ammonia (chloramine) is
  **constitutionally unrepresentable** (Charter §1.12 / Rider §2(a)); `record_formulation` REFUSES it.
  This is the actor-level implementation of the weaponizable-unrepresentable invariant.
- **G23 flammable-labeling** — alcohol actives require 火気厳禁 / flammable on the label (extends G11 lint).
- **G24 use-class** — each product declares `{surface, skin-antiseptic, hand-hygiene}`.
- clj-native SSoT: Wave 2 logic lives only in `py/agent.clj` + `py/test_agent.clj` (no Python counterpart —
  the clj-as-SSoT direction). G1..G20 inherited unchanged.

## Cell pattern (per ADR-2605192415 §B, silicon Wave 1 silicon_* mirror)

```
40-engine/kotoba/crates/kotoba-kotodama/cells/pharma_{phase_name}/
├── README.md                 # input/output Lexicon + state schema
├── __init__.py               # one-line module marker
├── cell.py                   # COUNCIL_ATTESTATION_TX_HASH + SILEN_PHARMA_BASELINE_REVIEW_CID gate
└── tests/                    # added at R1+
```

All 8 pharma_* cells are import-time RuntimeError gated. Removal requires:

- `COUNCIL_ATTESTATION_TX_HASH: str | None = None` set to a non-None Council Lv6+ ≥ 3 multisig tx hash
- `SILEN_PHARMA_BASELINE_REVIEW_CID: str | None = None` set to an `com.etzhayyim.pharma.silenPharmaReview` record CID with `verdict = "approve"`
- For G4 enforcement, additionally `QP_EQUIVALENT_REGISTRY_CID` set to a Council-attested registry of QP DIDs

The gate is constitutional invariant — do not remove without R1+ ADR landing.

## Witness invariant

`apiSynthesisAttestation` / `purificationAttestation` / `qcAttestation` / `fillFinishAttestation` /
`lotAttestation` are all witness N ≥ 2 (G9) — typically (process operator DID) + (QP-equivalent DID)
or (operator DID) + (independent witness automated sensor DID).
N = 1 auto-escalates to Council Lv6+ via MstListener — constitutional invariant.

## Phasing gate

R0 (this wave) is scaffold-only. R1+ requires:

- master charter §Decision 3 G3 silen-pharma-review baseline (Council Lv6+ ≥ 3)
- QP-equivalent (薬機法 製造管理者 / EU GMP Qualified Person 相当) on Council
- For R2: + 製造管理者 + Annex 1 sterile facility integrity attestation
- For R3: + 60-day public review + jurisdiction 薬事手続 (PMDA 製造業許可 + GMP 適合性調査 等)

Do NOT skip phases. Each R transition is its own ADR.

## Substrate-port + non-violation rules

- The 8 pharma lexicons use `com.etzhayyim.pharma.*` namespace (NOT `com.etzhayyim.yakushi.*`) — this mirrors silicon (`iwakura`/`fuigo`/`tsukuru` all under `com.etzhayyim.silicon.*`)
- The 3 化合物 API DIDs use **stable INN slug**: `:api:sodium-cromoglicate`, `:api:naphazoline-hydrochloride`, `:api:chlorpheniramine-maleate` — never CAS or local code
- Adverse event submission **MUST NOT** be aggregated by patient DID — only by lot + severity + outcome; aggregate keys exclude patient identity (G10)
- Wellbecoming label content (G11) is enforceable lint — `pharma_packaging` cell rejects label drafts missing `naphazoline 連用警告` for products containing naphazoline ≥ 0.05%
- DSCG 室温安定性は短期のみ — long-term storage 2-8°C; cold chain default = 2-8°C (single SOP for all 3 化合物)

## See also

- [ADR-2605250500](../../90-docs/adr/2605250500-yakushi-pharmaceutical-rd-charter.md) (master)
- [ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) (3-tier actor + Murakumo placement)
- [ADR-2605181100](../../90-docs/adr/2605181100-etzhayyim-encrypted-confidentiality-substrate.md) (XChaCha20 envelope for patient AE)
- [ADR-2605231525](../../90-docs/adr/2605231525-no-server-key-invariant.md) (G13 enforcement)
- [`20-actors/kuni-umi/CLAUDE.md`](../kuni-umi/CLAUDE.md) (sibling actor — robotics class ontology source)
- [`40-engine/kotoba/crates/kotoba-kotodama/cells/README.md`](../kotodama/cells/README.md) (sibling cell catalog)
