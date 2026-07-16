# 暮らし守 (kurashimori) — Citizen Consumer-Protection Concierge

**Tier-B actor · DID `did:web:kurashimori.etzhayyim.com` · ADR-2605312500 · R0 scaffold**

kurashimori is the 国民生活センター / 消費生活センター-equivalent self-help
concierge — the citizen↔**merchant** sibling of toritsugi (citizen↔government,
ADR-2605312030) and moushibumi (citizen→state, ADR-2605312400). 暮らし守 =
"guardian of everyday life" (parallels shidemori 死出守).

## What it does (3 self-help channels, member-initiated + consent-bound)

- **クーリングオフ (cooling-off)** — detect whether a contract is within a
  statutory cooling-off window (特定商取引法 — 訪問販売 8日 / 連鎖販売 20日 等),
  assist drafting the 書面/電子 通知; the **member sends** (or gated 代行). The
  cleanest, most deterministic channel.
- **返金 / 苦情 (refund / complaint)** — assist drafting a refund demand /
  complaint to the merchant; track the response.
- **エスカレーション** — stalled self-help → route to 消費生活センター /
  消費者ホットライン 188 / ADR (指定紛争解決機関) / chigiri + licensed counsel.

Default = **診断 + 起草補助 + 本人送付**; **代行 (本人同意ベース)** is the gated
R3 exception. kurashimori never represents the member before a tribunal.

## The coded remedy registry

Each remedy/route is an `com.etzhayyim.kurashimori.remedyTarget` record holding
the **remedy kind / 根拠法令 / statutory window (日数) / 書面 form / delivery
channel (内容証明 / 電子 / portal) / escalation forum**.

- Seed: [`registry/targets.seed.json`](registry/targets.seed.json) — 5 entries
  (訪問販売 クーリングオフ · 通信販売 返品特約 · 連鎖販売 クーリングオフ ·
  消費生活センター/188 · 適格消費者団体/ADR), **all `unverified-seed`**.
- **Honesty gate (G14):** no live send against an `unverified-seed` / stale
  entry — statutory windows drift and a wrong 日数 is harmful.

## Architecture (7 Pregel cells, R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `kurashimori_remedy_registry` | reuben | continuous | coded `remedyTarget` catalog; G14 |
| `kurashimori_intake` | reuben | event | consent + DID/SBT + matter (OWN) → `complaintSession` |
| `kurashimori_cooloff_check` | reuben | event | contract date + type → `coolingOffAssessment` (informational, NOT legal advice — G5) |
| `kurashimori_compose` | gad | event | chigiri template + remedy → `remedyDraft` (drafting-assist, G5) |
| `kurashimori_send` | naphtali | event | **only outbound** — self-send default; 代行 gated R3 → `dispatchRecord` |
| `kurashimori_status_track` | naphtali | continuous | merchant response / refund / window clock |
| `kurashimori_escalation` | gad | event | stalled → 消費生活センター / ADR / chigiri+counsel → `escalationReferral` |

All cells raise `RuntimeError("kurashimori R0 scaffold: …")` until Council ratification.

## Constitutional gates (G1–G15, immutable)

G3 consent + **own-matter-only** · G4 transparent + non-pretextual (not an
official センター) · **G5 UPL / 司法書士法 / 弁護士法** (no advice / no
representation / no rights-determination → chigiri + licensed; cooling-off output
is date-computation, not a legal opinion) · G6 PII only in encrypted DID-bound
envelopes · G7 Murakumo-only · G8 non-fabrication (cite 根拠法令 + provenance) ·
**G9 no claims-buying / no contingency / no 取立** · **G10 lawful + non-
harassment** · G11 Transparent Religious Force · G12 data-minimization · G13
stateAlignedFlag · **G14 verified-remedy-only send** · **G15 member-self-action
default**.

## Non-goals

NOT a 弁護士/司法書士 firm or advocate · NOT a claims/debt-collection (取立)
business · NOT contingency-fee / claims-buying · NOT a 適格消費者団体 substitute ·
NOT a harassment/威迫 tool · NOT a replacement for the member's own right · NOT a
merchant-blacklist / review-bombing system · NOT a data-broker · NOT an
impersonation tool · NOT an official 消費生活センター · NOT a plaintext-PII store ·
NOT a legal-opinion / rights-determination engine.

## Cross-actor boundaries

- **chigiri** (ADR-2605262700): legal characterization + 作成代理 + representation
  + ADR; kurashimori pulls templates + escalates, no advice.
- **himotoki** (ADR-2605302130): sibling — 開示請求 (data out) vs 苦情/通知.
- **toritsugi** (ADR-2605312030) / **moushibumi** (ADR-2605312400): sibling
  citizen-facing concierges; same registry + G15 self-action pattern.
- **wakai** (ADR-2605263500): mutual-aid relief for irrecoverable loss (kurashimori routes).
- **warifu** (ADR-2605302000): card-side chargeback (kurashimori does consumer-side demand).
- **`com.etzhayyim.encrypted.*`** (ADR-2605181100): only home for member PII + contract content.

## Roadmap

R0 scaffold (now) → R1 診断+起草補助 → R2 member self-send + status-track +
escalation → R3 gated 代行.

## References

- ADR: [`/90-docs/adr/2605312500-kurashimori-consumer-protection-concierge-tier-b-actor-r0.md`](/90-docs/adr/2605312500-kurashimori-consumer-protection-concierge-tier-b-actor-r0.md)
- Lexicons: [`/00-contracts/lexicons/com/etzhayyim/kurashimori/`](/00-contracts/lexicons/com/etzhayyim/kurashimori/)
- Charter Rider: [`/CHARTER-RIDER.md`](/CHARTER-RIDER.md)
