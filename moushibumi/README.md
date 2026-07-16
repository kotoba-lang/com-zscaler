# 申文 (moushibumi) — Citizen Democratic-Participation Concierge

**Tier-B actor · DID `did:web:moushibumi.etzhayyim.com` · ADR-2605312400 · R0 scaffold**

moushibumi is the citizen's **voice into** the state — the participation sibling
of toritsugi (取次, government procedures) and the counterpart of danjo (弾正,
which watches the state's output). Where toritsugi *transacts* with government
and danjo *watches* it, moushibumi helps a consenting member **be heard by** and
**participate in** the organs of governance.

申文 = a Heian-era formal written submission of one's case to authority. The
name deliberately parallels danjo (弾正台, the Heian censorate): danjo is the
state-watching eye, moushibumi is the citizen's upward voice.

> Name is provisional; Council may rename at ratification.

## What it does (3 channels, member-initiated + consent-bound)

- **選挙情報 (INFO-ONLY)** — when/where to vote, 期日前/不在者投票 mechanics,
  neutral pointers to official 選挙公報. **Never** campaigning, endorsement,
  ranking, GOTV targeting, or vote solicitation (公職選挙法 + neutrality, G3).
- **請願 / 陳情** — assist drafting a 請願書 (請願法) / 陳情 to a 議会, with a
  紹介議員 pointer where required; the **member submits** (or gated 代行).
- **パブリックコメント** (行政手続法 §39 意見公募手続) — assist drafting +
  submitting an opinion on a 命令等 proposal during its comment window.

Default mode = **案内 + 起草補助 + 本人提出**; **代行 (本人同意ベース)** is the
gated R3 exception (parallels toritsugi G15).

## The coded participation-target registry

Each target is an `com.etzhayyim.moushibumi.participationTarget` record holding
the **organ (議会 / 行政機関 / 選管) / channel / 根拠法令 / 提出様式 / 期限 /
紹介議員-required flag** so a cell can route + (eventually) file procedurally.

- Seed: [`registry/targets.seed.json`](registry/targets.seed.json) — 5 entries
  (国会請願 衆/参 · 地方議会陳情 · 国パブコメ e-Gov · 自治体パブコメ · 選挙情報
  総務省/選管), **all `unverified-seed`**.
- **Honesty gate (G14):** no live submission against an `unverified-seed` /
  stale entry; seeds are routing scaffolds.

## Architecture (7 Pregel cells, R0 path-reserved)

| Cell | Node | Phase | I/O |
|---|---|---|---|
| `moushibumi_target_registry` | reuben | continuous | coded `participationTarget` catalog; G14 |
| `moushibumi_voter_info` | reuben | continuous | NEUTRAL election-mechanics info (G3) |
| `moushibumi_opportunity_match` | reuben | event | member interest/locale (OWN) → `participationMatch` (neutral) |
| `moushibumi_intake` | gad | event | consent + DID/SBT → `participationSession` |
| `moushibumi_compose` | gad | event | chigiri template + target → `voiceDraft` (drafting-assist, G5) |
| `moushibumi_submit` | naphtali | event | **only outbound** — self-submit default; 代行 gated R3 → `submissionRecord` |
| `moushibumi_status_track` | naphtali | continuous | receipt + 採択/考え方 outcome |

All cells raise `RuntimeError("moushibumi R0 scaffold: …")` until Council ratification.

## Constitutional gates (G1–G15, immutable)

G3 **公職選挙法 + political-neutrality** (INFO+procedure only; no campaigning/
endorsement/GOTV — the critical gate) · G4 consent + **own-voice-only** · G5
行政書士法/UPL (drafting-assist, no advice → chigiri+licensed) · G6 PII +
**political-opinion** only in encrypted DID-bound envelopes (APPI special-care) ·
G7 Murakumo-only · G8 non-fabrication · G9 **non-partisan + non-commercial** (no
party/PAC/paid-lobbying) · G10 lawful-channel-only · G11 Transparent Religious
Force · G12 data-minimization (no opinion-profiling) · G13 stateAlignedFlag ·
G14 verified-target-only · G15 member-self-submission default.

## Non-goals

NOT a party/PAC/campaign org · NOT lobbying-for-hire · NOT a GOTV/vote-direction
machine · NOT a partisan endorser/ranker · NOT a 行政書士/弁護士 firm · NOT a
replacement for the member's own civic right · NOT a political-profiling system ·
NOT a data-broker · NOT an impersonation tool · NOT a plaintext-PII store · NOT a
mass-filing tool · NOT an oversight actor (that's danjo).

## Cross-actor boundaries

- **chigiri** (ADR-2605262700): templates + UPL; moushibumi pulls, renders no advice.
- **danjo** (ADR-2605301600): opposite posture (watches output vs conveys input).
- **himotoki** (ADR-2605302130): sibling — 開示請求 (data out) vs 請願/意見 (voice in).
- **toritsugi** (ADR-2605312030): sibling — procedures vs participation.
- **`com.etzhayyim.encrypted.*`** (ADR-2605181100): only home for member PII + opinion.

## Roadmap

R0 scaffold (now) → R1 案内+起草補助 → R2 member self-submit + status-track → R3
gated 代行.

## References

- ADR: [`/90-docs/adr/2605312400-moushibumi-democratic-participation-concierge-tier-b-actor-r0.md`](/90-docs/adr/2605312400-moushibumi-democratic-participation-concierge-tier-b-actor-r0.md)
- Lexicons: [`/00-contracts/lexicons/com/etzhayyim/moushibumi/`](/00-contracts/lexicons/com/etzhayyim/moushibumi/)
- Charter Rider: [`/CHARTER-RIDER.md`](/CHARTER-RIDER.md)
