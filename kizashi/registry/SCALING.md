# kizashi — Scaling Strategy (the bottleneck is NOT compute)

Per ADR-2605312700 §3 (cells) + §7 (roadmap). This file is the honest scaling
design: what actually limits kizashi's growth from R0 (no hardware) to
community-scale, and what does *not*. It is design-only — nothing here is built.

> **Honest headline**: the compute layer (fusion + attribution on Murakumo)
> scales trivially and is never the constraint. The real ceilings are **external,
> sequential, and mostly non-technical** — regulatory clearance, licensed
> operators, and physical hardware. Stating this prevents the failure mode of
> "the model scales, so the actor scales" — it does not.

## The four real scaling constraints (in dependency order)

| # | Constraint | Why it gates | Cannot be bought with compute |
|---|---|---|---|
| 1 | **Encrypted-envelope framework** (G2, ADR-2605181100) live in CI | No biometric (要配慮) data may flow before it; blocks R1 emission | ✓ |
| 2 | **≥1 licensed-MD on Council medical advisory** (G3 boundary review) | The non-diagnostic boundary needs a clinician's review; shared with mitate/iyashi | ✓ — a person, not a node |
| 3 | **薬機法 / medical-device regulatory clearance** per regulated modality **per jurisdiction** (G4/G9) | Gates every R3 modality (elastography / rhinometry / IgE / CRP); multi-year, jurisdiction-specific | ✓ — the dominant ceiling |
| 4 | **Qualified operators as vocation-flow L5 stewards** (G13, no payroll) | A staffed pod needs trained operators recruited under the Liberation Ladder, not gig hire | ✓ — values-gated recruitment |
| — | **Physical pod hardware** | Does not exist; R3 only | ✓ |

Compute (Murakumo node placement) is a *follower* of these, never a leader.

## What scales trivially (and why that's a trap to over-credit)

- **Fusion + attribution inference** — gemma4:e4b via LiteLLM; horizontally
  trivial across the Murakumo fleet (R1 `naphtali` → R2 `+gad` → R3 full 10-node,
  per §7). Adding throughput here is a config change.
- **The lexicon + EAVT data model** — content-addressed; per-member, append-only.
- Because items 1–4 above gate *real* scans, the compute layer at R1 runs only on
  **simulated + consented research data** (∞ cheap, zero member risk). Easy
  scaling here must not be mistaken for actor maturity.

## Data model scales per-member, NOT per-population (G8 is also a scaling property)

`wellbecomingTrajectory` is **self-referenced** (G8): a member's trajectory is
computed only against their OWN prior scans. Consequence for scale:

- There is **no population datastore** to grow, shard, or index across members.
- No cross-member joins, no cohort table, no leaderboard — so the storage model
  is N independent per-member encrypted streams, not one growing shared corpus.
- This is a privacy guarantee (no aggregate to breach) that *also* bounds the
  data architecture: scaling members is linear and embarrassingly partitionable,
  with no central index to become a bottleneck or a target.

## Modality coverage scales by evidence + Council, NOT by demand or breadth

Adding a modality is gated by the G10 ledger (`registry/modalities.seed.json` +
`VERIFICATION.md`), never by "look more comprehensive":

| Principle | Rule |
|---|---|
| **Evidence-first** | A modality enters service only at `council-verified` with a defensible `evidenceGrade` (A/B/C). Grade-X is terminal-excluded. |
| **Regulated ⇒ R3** | `regulatory-class ∈ {regulated, samd, ionizing}` ⇒ `phaseGate R3` + per-jurisdiction clearance (constraint #3). No shortcut. |
| **No breadth theater** | Never add an unvalidated modality to widen apparent coverage (the pseudoscience failure mode G10 exists to block). Fewer, defensible modalities > many vague ones. |
| **Demand-bounded capture** | Even validated modalities run only under per-scan consent (G6); no speculative bulk scanning (mirrors N9 no-surveillance). |

## Throughput ceilings by phase (honest caps)

| Phase | Real-scan capacity | Limited by |
|---|---|---|
| R0 | 0 (no hardware) | by design |
| R1 | 0 real / unlimited simulated | constraints #1–#2 + simulation-only |
| R2 | **≤20 consented research participants** (hard cap) | research protocol + non-regulated modalities only (#3 not yet cleared) |
| R3 | community-scale, **per-regulated-modality gated** | #3 clearance + #4 operators + hardware; scales modality-by-modality, jurisdiction-by-jurisdiction |

R3 is not a single switch: each regulated modality clears independently, so
"community-scale" arrives piecewise (e.g. thermography/posture broadly, IgE/CRP
only where the IVD pathway + phlebotomy capacity — shared with iyashi — exist).

**Last verified**: 2026-06-01 (R0 design-only).
