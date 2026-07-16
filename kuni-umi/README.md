# kuni-umi (国生み) — Planetary Infrastructure Robotics Fleet Actor

**Status**: S0 — spec + lexicon + actor scaffold (no robots dispatched). Apache-2.0 + Charter Rider v2.0.

Per [ADR-2605201400](../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md).
Tier-B per-domain leader actor for the 4-phase deployment workflow
(Survey → Plan → Construct → Commission) that ties together:

- `60-apps/etzhayyim-project-open-{denki,gas,water,network,power,rail,airplane,ports}` — utility CIM lexicons (target topology)
- `60-apps/etzhayyim-project-open-robo` — Giemon hardware (Otete arm + crawler, future Hitogata humanoid)
- `60-apps/etzhayyim-project-open-ot` — IEC 61499 WASM PLC (commissioning hand-off)
- UNSPSC 18,345 LangGraph agent fleet (procurement specialists, [ADR-2605171300](../../90-docs/adr/2605171300-open-unispsc-generative-agent-fleet.md))

Name origin: Izanagi / Izanami の国生み神話. Re-read in religious-corp context as
"chain-of-stewards co-creating the physical substrate that lets people live their
daily lives" — NOT "creating a state". See ADR Alternatives §D.

## Phase cells

| Cell | Phase | Murakumo leader | Trigger | Solidity contracts |
|---|---|---|---|---|
| [`cells/site_survey/`](cells/site_survey/) | 1 | naphtali | `defineDeploymentSite` MST listener | (none) |
| [`cells/deployment_planning/`](cells/deployment_planning/) | 2 | zebulun | `submitSiteSurvey` MST listener | `TitheRouter`, `EtzhayyimPaymaster`, `ChartersComplianceRegistry` |
| [`cells/construction_orchestration/`](cells/construction_orchestration/) | 3 | joseph | `proposeDeploymentPlan` accept | (none) |
| [`cells/commissioning/`](cells/commissioning/) | 4 | simeon | `recordConstructionProgress` complete | (none — hands off to open-ot) |
| [`cells/audit_witness/`](cells/audit_witness/) | continuous | levi | sensor stream + every super-step | `Phenotype` (feedback only) |
| [`cells/decommission/`](cells/decommission/) | end-of-life | dan | `lifespanYears` expiry or governance vote | `LandRegistry` (release back to commons) |

Tier A (per-site `KuniUmiSiteAgent`) is code-generated per the
[ADR-2605171300](../../90-docs/adr/2605171300-open-unispsc-generative-agent-fleet.md) pattern;
not catalogued in this directory.

Tier C escalation uses the generic
[`kotodama/cells/council_deliberation/`](../kotodama/cells/council_deliberation/) per
[ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md).

## Lexicon namespace

`com.etzhayyim.apps.etzhayyim.kuniUmi.*` — 6 procedure lexicons under
[`00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/`](../../00-contracts/lexicons/com/etzhayyim/apps/etzhayyim/kuniUmi/):

| Lexicon | Phase | Encryption |
|---|---|---|
| `defineDeploymentSite.json` | 0 (gate) | public |
| `submitSiteSurvey.json` | 1 | public (ecology baseline is open data) |
| `proposeDeploymentPlan.json` | 2 | XChaCha20-Poly1305 (BoM / cost — ADR-2605181100) |
| `recordConstructionProgress.json` | 3 | public |
| `commissionDeployment.json` | 4 | public |
| `recordPhysicalAuditEvent.json` | continuous | XChaCha20-Poly1305 for `anomaly` / `injury` subtypes; public for `community-event` / `compliance-check` |

## Hard rules (CRITICAL — derived from ADR §5 + §10)

1. **Land claim required** — `defineDeploymentSite` rejects any site not covered by
   `LandRegistry` (terrestrial) / `OceanStewardship` / `RiverStewardship` /
   `AtmosphereStewardship` / `OrbitalSlot`. Stewardship-only is enough; operational
   sovereignty is not required.
2. **Counterparty filter** — `ChartersComplianceRegistry` (ADR-2605192230) Non-Aligned
   flag on any of `stewardDid` / `landOwnerDid` / `intendedBeneficiaryDids` → reject.
3. **Charter Rider §2 gate** — `intendedUse` matching weapons / speculative finance /
   surveillance capitalism / fossil fuel extraction / specialist gatekeeping /
   multi-generational harm / strict individualist ontology / wellbecoming subordination
   → reject (ADR-2605192200 §2(a-h)).
4. **Witness N ≥ 2** — every `recordConstructionProgress` and
   `recordPhysicalAuditEvent` carries ≥ 2 independent robot-DID signatures. Mismatch
   triggers automatic Council escalation. Constitutional invariant.
5. **No military / proprietary** — `intendedUse=military` or proprietary closed-design
   BoM auto-reject at Phase 2.
6. **No hard-RT in cells** — motion / servo loops stay in Giemon firmware (open-robo +
   open-ot field tier). kuni-umi cells coordinate at 1–10 Hz checkpointer cadence.
7. **No SIL** — IEC 61508 / 61511 safety-instrumented functions remain on certified
   parallel safety PLCs.
8. **Substrate boundary** — substrate clients (`@atproto/api`, `viem`, IPFS client,
   noble-ciphers, libsignal) only via `@etzhayyim/sdk` per ADR-2605172000.

## Path-based DIDs

| Entity | DID pattern |
|---|---|
| Deployment site | `did:web:etzhayyim.com:kuniumi:site:<siteCode>` |
| Robot (Giemon unit) | `did:web:etzhayyim.com:kuniumi:robot:<serial>` |
| Fleet allocation | `did:web:etzhayyim.com:kuniumi:fleet:<id>` |
| Deployment plan | `did:web:etzhayyim.com:kuniumi:plan:<planCode>` |
| Audit event | `did:web:etzhayyim.com:kuniumi:audit:<eventId>` |

## Phasing roadmap

| Stage | Scope | Pre-req |
|---|---|---|
| **S0 — Spec + scaffold** (this ADR) | 6 lexicons + actor + BPMN + DMN; no robots | — |
| **S1 — Solo survey** | one Giemon Otete robot surveys an etzhayyim-owned plot | Giemon Otete operational + `LandRegistry` plot |
| **S2 — Single-utility prototype** | community microgrid (open-ot prototype scope, ADR-2605151200 §R3) end-to-end | S1 + open-ot MVP runtime |
| **S3 — Multi-utility integrated** | electric + water + network simultaneously on one site | S2 + Giemon Hitogata + open-water / open-network MVP runtimes |
| **S4 — Multi-site fleet** | ≥ 5 concurrent sites; cross-site BoM consolidation | S3 + ≥ 20 robots + edge orchestration |
| **S5 — Extended sovereignty** | ocean / river / atmosphere / orbital sites | S4 + Phase 1 of ADR-2605192330 live |

S1–S5 are separate ADRs.

## See also

- [ADR-2605201400](../../90-docs/adr/2605201400-etzhayyim-kuni-umi-planetary-infra-fleet.md) — master design (this actor)
- [ADR-2605192415](../../90-docs/adr/2605192415-etzhayyim-religious-corp-daemon-architecture.md) — 3-tier actor hierarchy + Murakumo placement pattern
- [ADR-2605171800](../../90-docs/adr/2605171800-langgraph-mst-ipfs-l2-anchor-pipeline.md) — checkpoint pipeline foundation
- [`50-infra/murakumo/fleet.toml`](../../50-infra/murakumo/fleet.toml) — node ↔ cell placement
