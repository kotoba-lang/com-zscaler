# 繙き (himotoki) — Active Disclosure-Request Filer

**Tier-B actor · DID `did:web:himotoki.etzhayyim.com` · ADR-2605302130 · R0 scaffold**

himotoki is the **active-outbound** counterpart to the passive-only
danjo (ADR-2605301600) and tadori (ADR-2605301400). Where they only *read*
already-available records, himotoki **exercises a right of access** to
*obtain* records, then lands them into the substrate.

It files:

- **DSAR — 個人情報開示請求** (APPI §33 / GDPR Art.15 / CCPA §1798.110) to
  **private controllers** (Discord, Google, LINE, Meta, Amazon, …) on
  behalf of a **consenting member**, for that **member's own** personal
  data; and
- **FOIA — 行政文書開示請求** (行政機関情報公開法 / FOIA / EU Reg.1049/2001)
  to **public organs** for administrative documents any citizen may obtain.

繙き = *to unbind and peruse a scroll* — to consult records one is entitled
to read. The name frames the actor as the exercise of a **right to read**
(one's own data; public documents), never surveillance of others.

> Name is provisional (alternatives considered: 開き / 求め); Council may
> rename at ratification.

## The coded target registry (the point)

Per the 2026-05-30 requirement — *"公開企業の窓口、住所、連絡先、メール
アドレス、手続きなどをすべて コードに, actor が手続きできるように"* —
himotoki carries each organization's **窓口 / 住所 / 連絡先 / メールアドレス /
portal / 手続き / 手数料 / 法定期限** as coded
`com.etzhayyim.himotoki.disclosureTarget` records so a cell can route and
file procedurally.

- Seed: [`registry/targets.seed.json`](registry/targets.seed.json) — 6
  entries (Discord / Google / LINE / Meta / Amazon / JP 行政機関 template),
  **all `unverified-seed`**.
- **Honesty gate (G14):** no live dispatch against an `unverified-seed` or
  stale entry. Seeds are routing scaffolds; live filing requires
  `maintainer-verified` (FOIA) / `council-verified` (named controller /
  DSAR) within the freshness window. R0 ships the schema + the *mechanism*;
  the verified contacts are a living, curated dataset.

## Architecture (7 Pregel cells, R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `himotoki_target_registry` | reuben | continuous | maintain + resolve the coded `disclosureTarget` catalog; enforce G14 |
| `himotoki_request_intake` | reuben | event | consent + DID/SBT binding + scope → `disclosureRequest` |
| `himotoki_compose` | gad | event | chigiri template + resolved target → filled request artifact |
| `himotoki_dispatch` | gad | event | **only active-outbound cell** — transparent, identified send → `requestDispatch` (G14-gated) |
| `himotoki_deadline_tracker` | gad | continuous | statutory deadline + follow-up + non-response escalation |
| `himotoki_response_intake` | naphtali | event | response → `disclosureResponse` + **encrypted DID-bound PII envelope** (ADR-2605181100) |
| `himotoki_appeal_route` | naphtali | event | refusal/partial → lawful appeal via chigiri → `appealRecord` |

All cells raise `RuntimeError("himotoki R0 scaffold: …")` until Council
ratification.

## Constitutional gates (G1–G14, immutable; Council Lv6+ + ADR to amend)

G3 consent + **own-data-only** · G4 transparent + **non-pretextual** ·
G5 **UPL-equivalent** (legal advice → chigiri + counsel) · G6 disclosed
**PII only in encrypted DID-bound envelopes** · G7 Murakumo-only · G8
**rate-limited / non-vexatious** · G9 no data-broker · G10
**lawful-channel-only** · G11 Transparent Religious Force · G12
data-minimization · G13 stateAlignedFlag · G14 **verified-target-only
dispatch**.

## Non-goals

NOT a law firm · NOT a private-investigator (can't request others' PII) ·
NOT surveillance · NOT a data-broker · NOT a pretext/impersonation tool ·
NOT an unauthorized-access tool · NOT a mass-filing/DoS tool · NOT a
plaintext-PII store · NOT a leak channel.

## Cross-actor boundaries

- **chigiri** (ADR-2605262700): chigiri = procedure templates + UPL +
  appeal procedure; himotoki = active filer + tracker + custodian. himotoki
  *pulls* templates from chigiri.
- **`com.etzhayyim.encrypted.*`** (ADR-2605181100): the only home for
  disclosed PII.
- **manimani** (ADR-2605291100): a member's own disclosed data → that
  member's personal KG (with consent).
- **danjo** (ADR-2605301600) / **ossekai** (ADR-2605264000): FOIA-obtained
  *public* records (non-PII) may flow there; PII never does.

## Roadmap

R0 scaffold (now, no dispatch) → R1 registry + intake + compose (artifacts
only) → R2 FOIA live dispatch (public records) → R3 DSAR live dispatch
(consent + encrypted PII custody) + appeals + multi-jurisdiction.

## References

- ADR: [`/90-docs/adr/2605302130-himotoki-disclosure-request-tier-b-actor-r0.md`](/90-docs/adr/2605302130-himotoki-disclosure-request-tier-b-actor-r0.md)
- Lexicons: [`/00-contracts/lexicons/com/etzhayyim/himotoki/`](/00-contracts/lexicons/com/etzhayyim/himotoki/)
- Charter Rider: [`/CHARTER-RIDER.md`](/CHARTER-RIDER.md)
