# 取次 (toritsugi) — Citizen-Facing Government-Procedure Concierge

**Tier-B actor · DID `did:web:toritsugi.etzhayyim.com` · ADR-2605312030 · R0 scaffold**

toritsugi is the **service-delivery** counterpart to the passive danjo
(ADR-2605301600, watches the state) and to himotoki (ADR-2605302130, exercises a
right of access). Where danjo *watches* government and himotoki *pulls* data out
of organizations, toritsugi stands at the 窓口 **on the citizen's side** and
relays a consenting member through a government / municipal procedure — the role
Japanese municipalities fill with a LINE 公式アカウント.

取次 = *to relay / to broker at the counter* (窓口取次). The name frames the
actor as the member's side of the 窓口: it relays the person to the right
procedure and walks them through it.

> Name is provisional (alternatives considered: 導き / 手引き); Council may
> rename at ratification.

## What it does

- **案内 + 伴走 + 本人提出支援** (default mode, R0→R2): proactively surface
  制度/給付 the member may be eligible for, explain the 手続き, assemble the
  必要書類 checklist, and assist filling the 様式/フォーム — the **member
  themselves submits and signs**. No 代理権 required; 行政書士法-safe.
- **本人同意ベース提出代行** (gated, R3): with explicit per-submission consent +
  DID/SBT binding, file the member's **own** procedure via the official channel.
  Constitutionally gated (G14 + G15 + 行政書士法 clearance + Council Lv7+); **not
  enabled at R0**.

## The coded procedure registry (the point)

Mirroring himotoki's coded target registry, toritsugi carries each government /
municipal procedure as a coded `com.etzhayyim.toritsugi.procedure` record
holding the **窓口 / 所管 (省庁・自治体) / オンライン申請URL / 必要書類 / 様式 /
手数料 / 法定処理期間 / 根拠法令 / channel** so a cell can guide (and eventually
file) procedurally.

- Seed: [`registry/procedures.seed.json`](registry/procedures.seed.json) — 6
  entries (住民票の写し / 転入届 / 出生届 / マイナンバーカード交付申請 /
  児童手当認定請求 / 確定申告 e-Tax), **all `unverified-seed`**.
- **Honesty gate (G14):** no live submission against an `unverified-seed` or
  stale entry. Seeds are routing/guide scaffolds; live filing requires
  `maintainer-verified` (public procedure) / `council-verified` (代行 path).

## Architecture (7 Pregel cells, R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `toritsugi_procedure_registry` | reuben | continuous | maintain + resolve the coded `procedure` catalog; enforce G14 |
| `toritsugi_eligibility_match` | reuben | event | member life-event/profile (OWN) → `benefitMatch` (proactive notify) |
| `toritsugi_intake` | reuben | event | consent + DID/SBT + need → `procedureGuide` session |
| `toritsugi_guide` | gad | event | chigiri template + resolved procedure → step-by-step 案内 + 必要書類 checklist |
| `toritsugi_draft` | gad | event | assist filling the 様式 → `applicationDraft` (member-owned; assist, not 作成代理) |
| `toritsugi_submit` | naphtali | event | **only / gated active-outbound** — default hands back for self-submit; 代行 only at R3 → `submissionRecord` |
| `toritsugi_status_track` | naphtali | continuous | 処理状況 + 法定処理期間 clock + 結果 intake (encrypted) + appeal → chigiri |

All cells raise `RuntimeError("toritsugi R0 scaffold: …")` until Council
ratification.

## Constitutional gates (G1–G15, immutable; Council Lv6+ + ADR to amend)

G3 consent + **own-procedure-only** · G4 transparent + **non-pretextual**
(member is 申請者本人; not an official channel) · **G5 行政書士法 / UPL boundary**
(no advice, no 作成代理 → chigiri + licensed) · G6 PII **only in encrypted
DID-bound envelopes** · G7 Murakumo-only · **G8 non-fabrication** (cite 根拠法令
+ provenance; member confirms) · G9 non-profit / no data-broker · G10
**lawful-channel-only** · G11 Transparent Religious Force · G12 data-minimization
· G13 stateAlignedFlag · **G14 verified-procedure-only submission** · **G15
member-self-submission default** (代行 is the gated R3 exception).

## Non-goals

NOT a 行政書士/司法書士/税理士/弁護士 firm · NOT a replacement for the member's
own right · NOT surveillance/profiling · NOT a data-broker · NOT an impersonation
tool · NOT an unauthorized-access tool · NOT a mass-filing/DoS tool · NOT an
official 自治体 channel · NOT a plaintext-PII store · NOT legal/tax advice · NOT
an oversight actor (that's danjo).

## Cross-actor boundaries

- **chigiri** (ADR-2605262700): templates + UPL + 作成代理 + appeal. toritsugi
  pulls templates; renders no advice.
- **himotoki** (ADR-2605302130): files 開示請求 (data out); toritsugi files
  申請/届出 (member into a procedure). Sibling pattern.
- **toritate** (ADR-2605262900): tax/accounting characterization (確定申告).
- **warifu** (ADR-2605302000): 申請手数料 / 証紙 settlement.
- **musubi** (ADR-2605263400): life-event ceremonies whose government 届出
  toritsugi handles.
- **産土 ubusuna / §1.16** (ADR-2605302357/2605302358): toritsugi is the
  government-procedure execution arm complementing etzhayyim's own social
  security.
- **`com.etzhayyim.encrypted.*`** (ADR-2605181100): the only home for member PII.

## Roadmap

R0 scaffold (now, no submission) → R1 registry + match + intake + guide (案内
only) → R2 draft + status-track (member self-submits) → R3 gated 本人同意ベース
提出代行 + encrypted result custody + appeals + multi-jurisdiction.

## References

- ADR: [`/90-docs/adr/2605312030-toritsugi-government-procedure-concierge-tier-b-actor-r0.md`](/90-docs/adr/2605312030-toritsugi-government-procedure-concierge-tier-b-actor-r0.md)
- Lexicons: [`/00-contracts/lexicons/com/etzhayyim/toritsugi/`](/00-contracts/lexicons/com/etzhayyim/toritsugi/)
- Charter Rider: [`/CHARTER-RIDER.md`](/CHARTER-RIDER.md)
