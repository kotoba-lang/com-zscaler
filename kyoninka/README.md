# kyoninka 許認可 — Robotaxi Legal-Deployment Permitting

**ADR-2606272337 · Tier-B · Status: R0 · did:web:etzhayyim.com:actor:kyoninka**

Answers, per jurisdiction and with an immutable paper trail, the one question a
driverless-taxi service must answer before it carries a passenger on a public
road, **in Japan and worldwide**:

> **「ここでロボタクシーを合法的に走らせられるか。誰がサインオフするのか。」**

A contained **reg-LLM** (regulatory advisor) proposes a legal-readiness
recommendation from a deployment's permitting facts; an independent
**PermitGovernor** censors it against the jurisdiction's mandatory-permit /
minimum-insurance / required-filing / SAE-level invariants; a public-road launch
**always** routes to a human regulatory authority. The single invariant — the
許認可 version of robotaxi-actor's safety contract:

> **observe → recommend ONLY. The actor never grants a permit and never
> activates a vehicle.**

## 手続きの可視化 (web)

The permitting procedure is rendered as a crawlable static site —
**<https://etzhayyim.com/kyoninka>** (deploy is an operator step):

- **手続きフロー** — the DeploymentActor StateGraph as a readable procedure
  (受付 → 助言 reg-LLM → 検閲 PermitGovernor → 判定 → 当局サインオフ → 記録).
- **法域別 要件マトリクス** — per-jurisdiction max SAE level, mandatory permits,
  minimum liability cover, filings, remote-operator requirement.
- **デプロイ準備状況** — per-deployment legal checklist (✓/✗ each permit /
  insurance / filing / level / remote operator) + verdict + which authority signs.

Regenerate:

```bash
bb --classpath 20-actors -e \
  "(require 'kyoninka.methods.site-gen)(kyoninka.methods.site-gen/-main)"
```

## Jurisdictions modelled (illustrative — curate with counsel)

| id | regime | max | mandatory permits (sample) | min liability |
|----|--------|-----|----------------------------|---------------|
| `JP` | 改正道路交通法 特定自動運行 / 道路運送車両法 型式指定 / 道路運送法 | L4 | 型式指定・特定自動運行許可・旅客運送事業許可・個情法 | 2億円 |
| `US-CA` | CA Veh.Code §38750 + DMV AV regs / CPUC / FMVSS | L4 | type approval・DMV deployment・CPUC passenger・data | ~5億 |
| `DE` | StVG §1d–1l (AFGBV 2022) / UNECE / GDPR | L4 | type approval・operating-area・technical supervisor・data | ~15億 |
| `SG` | Road Traffic Act (AV) / LTA AV rules / TR68 | L4 | type approval・LTA trial/deployment・data | ~1億 |
| `ZZ` | (架空) AV 法未整備 | L2 | — | 0 |

The rulebook is **data** (`methods/procedure.cljc`): adding a jurisdiction is a
reviewed edit, not a code change (G5).

## Layout

| Path | Role |
|---|---|
| `manifest.edn` | actor profile SSoT (registered into INFRA_ACTORS via `bb gen:tier-b-actors`) |
| `methods/procedure.cljc` | the jurisdiction rulebook + PermitGovernor legal invariants (bb-runnable) |
| `methods/site_gen.cljc` | the 手続き web-viz generator (crawlable static HTML) |
| `methods/test_procedure.cljc` | the permitting contract as executable tests |
| (impl) `orgs/etzhayyim/com-etzhayyim-kyoninka` | the runnable langgraph-clj StateGraph actor (reg-LLM ⊣ PermitGovernor) |

## Hard prohibitions (structural, not policy)

- **No permit grant / no vehicle activation** (G1) — proposal `:effect` is
  `:assessment` only; permit-grant / vehicle-activation unrepresentable.
- **No auto-launch** (G3) — a public-road go-live always interrupts for a human
  authority, even when the checklist is fully clean.
- **Unoverridable holds** (G4) — a missing/expired mandatory permit, below-floor
  insurance, over-level SAE, or missing filing is a HARD HOLD no human can
  approve past.
- **Not the practice of law** (G8) — general legal information, non-adjudicating
  (shared UPL boundary with chigiri 契).
- **No server-held key** (G10); reg-LLM inference Murakumo-default (G9).

See `manifest.edn` for the canonical gate list and ADR-2606272337 for rationale.
