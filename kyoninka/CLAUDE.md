# 20-actors/kyoninka 許認可

**Robotaxi legal-deployment permitting actor. ADR-2606272337. Status: R0.**

Maps a robotaxi deployment's permitting facts (per jurisdiction) to a legal-
readiness verdict + a human-authority sign-off requirement, with an append-only
permitting ledger. A contained **reg-LLM** proposes; an independent
**PermitGovernor** censors against the jurisdiction's mandatory invariants; a
public-road launch always routes to a human regulatory authority.

Single invariant (the 許認可 analog of robotaxi-actor's safety contract):
**observe → recommend ONLY — the actor never grants a permit and never activates
a vehicle.**

## Implementation

- **Runnable engine**: `orgs/etzhayyim/com-etzhayyim-kyoninka` — the langgraph-clj
  StateGraph actor (reg-LLM ⊣ PermitGovernor), the robotaxi-actor / ai-gftd-
  itonami sibling. 10 contract tests green; `clojure -M:dev:run` drives the demo.
- **Platform form (here)**: `methods/procedure.cljc` is the same domain logic
  (rulebook + invariants) in dependency-free `.cljc`, bb-runnable, kotoba-native.
  `methods/site_gen.cljc` renders the 手続き web viz.

## Hard prohibitions (structurally unrepresentable, not policy)

- **No permit grant / vehicle activation** (G1) — `:effect :assessment` only.
- **No auto-launch** (G3) — public-road go-live always interrupts for a human
  authority (`interrupt-before :request-approval`), even on a clean checklist.
- **Unoverridable HARD holds** (G4) — missing/expired permit, below-floor cover,
  over-level SAE, missing filing.
- **Rulebook is data** (G5) — jurisdiction edits are reviewed datoms, not code.
- **Non-adjudicating** (G8) — general legal info, not the practice of law (shared
  UPL boundary with chigiri 契).
- **No server-held key** (G10); reg-LLM Murakumo-default (G9).

## Regenerate / register

```bash
# web viz → 50-infra/etzhayyim-did-web/public/kyoninka/
bb --classpath 20-actors -e "(require 'kyoninka.methods.site-gen)(kyoninka.methods.site-gen/-main)"
# register into INFRA_ACTORS (tier-b-actors.gen.ts) so the DID resolves + /search
bb gen:tier-b-actors
```

See ADR-2606272337 for the full rationale; `manifest.edn` for the gate list.
