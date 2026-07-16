# 20-actors/rare-earth-coverage — CLAUDE.md

## What this is

An **observation watcher** — a supply-chain / dependency knowledge-graph mirror over the
rare-earth & critical-metal value chain (minerals, subsystems, regulators, processors,
downstream users). Sibling of the observatory lineage (kabuto / tsumugi / inochi / watatsuna).
It **does NOT mine, extract, trade, or target.** `re4c0v26`, `did:web:rare-earth-coverage.etzhayyim.com`.

## Analytical core — multi-generational risk-assessment axis (ADR-2606161700)

The Charter does **not** ban mining/extraction as such. Per **Charter Rider §2(l) v3.2**
(ADR-2606161700), extraction is gated by a **multi-generational (子・孫) × wellbecoming
risk assessment**, not a by-industry-name blanket ban. This watcher carries that axis as
its telos: it maps where rare-metal value chains create

1. **monopoly / chokepoint dependency** (the §1.12 anti-monopoly concern), and
2. **irreversible multi-generational environmental risk** (the §2(d)/§2(l) measured standard),

routed to **RESILIENCE / de-monopolization / restoration**.

The machine-readable form lives in `actor-manifest.jsonld` → `riskAssessmentAxis`.

## Hard rules

- **NEVER a target-list, an extraction recipe, or a "mining is forbidden" verdict.** Judge by
  measured harm to 子孫, never by an industry's name.
- Observation only — capabilities are read/query/derive (see `capabilities` in the manifest);
  no extraction, no trade, no person-tracking.
- License: Apache 2.0 + Charter Compliance Rider v3.2 (see `/CHARTER-RIDER.md`).

## Related files

- `actor-manifest.jsonld` — actor set + pipelines + `riskAssessmentAxis`
- `actor-manifest.test.ts` — manifest structural tests
- `/CHARTER-RIDER.md` §2(l) — the multi-gen extraction risk-gate
- `/90-docs/adr/2606161700-multigenerational-extraction-risk-gate-not-blanket-mining-ban.md`
