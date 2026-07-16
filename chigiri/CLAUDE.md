# 20-actors/chigiri — CLAUDE.md

## Identity

- **Name**: chigiri (契 — covenant; Hebrew בְּרִית / brit; foundational legal primitive of religious-corp Sola Scriptura tradition)
- **DID**: `did:web:chigiri.etzhayyim.com`
- **ADR**: ADR-2605262700 (R0 scaffold, 2026-05-26)
- **Companion ADR**: ADR-2605262800 (global legal corpus ingestion)
- **Parent ADRs**: ADR-2605192100 (Mission Charter), ADR-2605192200 (Charter Rider), ADR-2605192300 (Council 5-of-7), ADR-2605261000 (Liberation Ladder L0..L6)
- **Status**: R0 scaffold — 12 cells path-reserved (created in W1); 9 Lexicon skeletons under com.etzhayyim.chigiri.*
- **Form**: 任意団体 internal procedure substrate (NOT 一般社団 / NPO / 公益財団 / 宗教法人 法人格 — Preamble §0.4 Lv7+ unanimity lock)

## Constitutional Discipline (CRITICAL — IMMUTABLE)

chigiri is **procedural / templating / attestation substrate**, NOT a
law firm and NOT an unauthorized practice of law. Three discipline
boundaries are structural:

1. **UPL prohibition (G14)** — chigiri MUST NOT render legal advice.
   Templates document procedure; advice happens via human counsel
   contracted through Public Fund (Council Lv6+ approval per
   ADR-2605192145). Lint hook
   `70-tools/scripts/lint/no-chigiri-legal-advice.mjs` (W1) scans
   chigiri code for advice-issuing language; CI-blocks on hit.
2. **No state-granted legal personality (N2)** — Preamble §0.4 Lv7+
   unanimity lock. chigiri MUST NOT introduce a code path that depends
   on state-granted legal personality (一般社団 / NPO / 公益財団 /
   宗教法人法 登記). External-interface paths document state
   recognition as EXTERNAL, never as internal dependency.
3. **Murakumo-only inference (G11)** — chigiri MUST NOT make outbound
   vendor LLM API calls. All LLM-assisted template completion /
   precedent search flows through judah LiteLLM (127.0.0.1:4000) →
   gemma4:e4b on the fleet per ADR-2605215000.

> **Premise correction (ADR-2605302200)**: 非営利 ≠ UPL exemption. None of
> the 9 surveyed jurisdictions (JP/DE/FR/UK/US/KR/AU/CA-ON/AT-CH) gates the
> law-practice monopoly on for-profit/non-profit status. They split into
> **compensation-gated** (JP/FR/KR/AU — free advice lawful alone),
> **licensure-gated** (DE/US/CA-ON/AT — free insufficient), and
> **activity-gated** (UK advice unreserved; CH out-of-court). The operative
> axes are **compensation** and **lawyer involvement**. Two lawful lanes:
> **(A) advice lane** (`chigiri_legal_aid_clinic`, R0 path-reserved) binds on
> **G15 (zero compensation, incl. indirect benefit)** + **G16
> (jurisdiction-licensed-lawyer supervision)** — the strictest common
> denominator across all 9 jurisdictions; **(B) Japan certified-mediation
> lane** (ADR-2605302330) activates the `dispute_mediation` cell as a
> candidate 認証紛争解決事業者 (ADR法), giving 和解仲介 an express §72 carve-out
> + 時効 tolling; still free (G15), still no advice (G14), §6-(5) 弁護士助言措置
> reusing the same G16 counsel rail; gated on 法務大臣 認証 (G17) until which it
> degrades to non-binding facilitation. G14 is unchanged — chigiri renders no
> advice; lane A is a Public-Fund-funded human-counsel delivery channel.
> Court representation / litigation stays a lawyer monopoly everywhere and
> is out of scope. See ADR-2605302200 (table) + ADR-2605302330 (mediation).

## Architecture

12 Pregel cells, each path-reserved at R0 (`40-engine/kotoba/crates/kotoba-kotodama/cells/chigiri_*/`):

```
charters_attestation ──────┐
council_procedure ─────────┤
member_onboarding ─────────┤── reuben + simeon (governance + lifecycle)
member_offboarding ────────┤
inheritance ───────────────┘

covenant_ceremony ─────────── simeon + levi (musubi pair; future)

dispute_mediation ─────────── levi (cooperative-first, ≤3 mediation rounds)

ip_licensing ──────────────┐
tax_receipt ───────────────┤
employment_compliance ─────┤── gad (external interface routing)
data_privacy ──────────────┘

transparent_force_authorization ── naphtali (witness pair; ADR-2605192315 front-end)
```

Each cell = 1 Pregel graph. Cells communicate via lexicon records on
MST (`com.etzhayyim.chigiri.*`). All cell modules are R0 path-reserved
and will be import-time `RuntimeError("chigiri R0 scaffold: activate
via Council ADR + R1 ratification")` at W1 creation.

## Mediation-First Rule (G10) — Structural

`disputeMediation` schema enforces:

- `currentRound ≥ 1` before any arbitration channel may be invoked;
- `mediationOutcomes[]` MUST be populated with at least one completed
  round entry before `escalateToArbitration=true`.

This is structural; Pregel cell logic verifies these fields prior to
forwarding to any arbitration channel.

## Excommunication (G12) — Structural

`excommunicationProcedure` schema enforces:

- `cureWindowStartsAt + 30 days ≤ finalizedAt`;
- `councilAttestations[]` MUST include ≥4 Council seat DIDs (Lv6+);
- `automaticSbtRevoke=true` MUST emit alongside finalization;
- `cureAttempts[]` may be empty (member chose not to cure) but MUST be
  initialized;
- Reversal requires `freshAdherentCeremonyCid` referencing a new
  Adherent SBT issuance (no fast-track restoration).

## Steward Labor (G13) — Structural

`stewardLaborAttestation` schema enforces:

- `lLevel ∈ {L0, L1, L2, L3, L4, L5, L6}` (Liberation Ladder per ADR-2605261000);
- `classification ∈ {witness, adherent, sustenance, shelter, care, vocation, liberation}`;
- `employmentRelation ∈ {volunteer, subsistence, vocation, none}` —
  the value `employee` is NOT a valid enum value (constructive-employment
  drift prevented at schema layer);
- `externalEmployerDid` MAY be present (parallel external employment
  is permitted; the religious-corp relation is separate).

## Force Authorization (G7) — Structural

`forceAuthorizationRecord` schema enforces:

- `posture ∈ {defensive, deterrent}` only;
- `posture=offensive` is NOT a valid enum value (offensive force
  attestation impossible at schema layer);
- `justWarChecklist` (jus ad bellum + jus in bello) MUST be fully
  populated with all 9 checkpoints;
- `ihlCompliance` MUST cite Geneva Conventions + Additional Protocols
  applicability;
- `oneSbtOneVote` chain hash references ADR-2605192315 attestation
  contract.

## R1 Activation Triggers

1. ADR-2605262700 Council Lv6+ ≥3 ratify;
2. Bootstrap Council Seat 2-5 RFP closure (2026-06-19) + at least one
   filled Council seat beyond Founder Seat 1;
3. Lint hook `70-tools/scripts/lint/no-chigiri-legal-advice.mjs`
   deployed to lefthook config;
4. Charter Rider scanner false-positive rate ≤5% over 7-day trial on
   chigiri-bound document samples (R8 / R11 KaizenObserver health);
5. `com.etzhayyim.chigiri.covenantAttestation` + `.inheritanceChain` +
   `.stewardLaborAttestation` schemas Council-attestation-reviewed
   (R1 minimum cell trio).

## R1 Cell Activation Order

1. `chigiri_charters_attestation` (lowest-risk; integrates with existing
   ChartersComplianceRegistry; read-only at R1);
2. `chigiri_council_procedure` (gates own future R2+ activations);
3. `chigiri_member_onboarding` (most-requested; Adherent SBT issuance
   procedure for new members).

R2 adds covenant_ceremony / member_offboarding / inheritance /
dispute_mediation (full cycle) / ip_licensing.

R3 adds the remaining cells including transparent_force_authorization.

## Cross-actor Relationships

### Read-only consumers (hanrei + bunken)

chigiri cross-actor invokes `did:web:hanrei.etzhayyim.com` for case-law
lookup at mediation / IP-licensing time. hanrei is a graph-projection
actor; chigiri reads through hanrei's existing XRPC commands
(`searchDecisions`, `getCase`, `listCases`). hanrei's
`lawfirm.etzhayyim.com` reference (line 73 of hanrei/CLAUDE.md) is
this actor.

`did:web:bunken.etzhayyim.com` provides bibliography lookup for legal
literature (treatises, commentary) at template-drafting time.

### Procedure peers (musubi + shidemori, future)

`musubi.etzhayyim.com` will perform covenant ceremonies (marriage /
naming); chigiri's `covenant_ceremony` cell attests on-chain via
`covenantAttestation`. `shidemori.etzhayyim.com` will issue memorial
NFT + cemetery records; chigiri's `inheritance` cell attests the
SBT + wallet succession.

### Charter / Council on-chain peers

chigiri integrates with:

- `ChartersComplianceRegistry` (single SoT for §2 attestation);
- `Council 5-of-7 Safe` (proposal & vote flow);
- `Public Fund Safe` (external-counsel contract proposals);
- `Land Registry` (inalienable-donation invariant cross-check);
- `Force Authorization` contract (1 SBT = 1 vote chain integration);
- `TitheRouter` (10% Tithe transparency accounting).

## Build & Deploy

**R0 status**: Scaffold only. No cells, no smoke test (cells don't yet
exist). Lexicon schema validation (R1) will run via lefthook
`validate-lexicons` on the 9 chigiri Lexicons.

R1 smoke test (when cells are created):

```bash
cd 40-engine/kotoba/crates/kotoba-kotodama/py
python -c "from kotodama.cells.chigiri_charters_attestation import _r0_marker" 2>&1 | grep "R0 scaffold"
# ... similar for all 12 chigiri_* cells
```

## Related Files

- `/20-actors/chigiri/manifest.jsonld`
- `/20-actors/chigiri/README.md`
- `/00-contracts/lexicons/com/etzhayyim/chigiri/` (9 Lexicon JSONs + README)
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — Master ADR
- `/90-docs/adr/2605262800-public-data-legal-corpus-ipfs-ingestion.md` — Data substrate ADR
- `/90-docs/adr/2605192100-etzhayyim-mission-charter.md` — Mission charter
- `/90-docs/adr/2605192200-etzhayyim-ip-free-release-charter-rider.md` — Charter Rider
- `/90-docs/adr/2605192300-etzhayyim-council-5-of-7-safe.md` — Council
- `/90-docs/adr/2605192315-etzhayyim-transparent-force-authorization.md` — Transparent Force
- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — Liberation Ladder
- `/90-docs/adr/2605262130-kotoba-storage-substrate-unification.md` — Storage substrate
- `/90-docs/adr/2605262400-public-data-organism-ipfs-ingestion.md` — Public-data ingestion parent
- `/CHARTER-RIDER.md` — License + Rider canonical text
- `/COUNCIL.md` — Bootstrap Council roster + RFP
- `/MEMBERS.md` — 信者 roster
- `/CLAUDE.md` — Religious-corp status table
