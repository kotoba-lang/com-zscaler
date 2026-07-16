# torifune 鳥船 — R3 live-legs operator runbook (Council-attestation-gated)

**ADR**: 2606162355 · **Status**: 🔴 R3 — gated, **no live action performed by this document**.

This runbook is the **Council-attestation request** for torifune's two gated legs. Per the
Bootstrap premise (root `CLAUDE.md`: *Council attestation = Pull Request review*), **merging the
PR that carries this runbook = Council attestation of the PROCEDURE below** — it does NOT
authorize any specific launch. Each launch mission is its own on-chain-attested event (G4).

No step here is executed by an agent or a server. Every leg is a **no-server-key operator-DID**
action (G6). An agent that performed launch operation would violate the no-server-key invariant
(root `CLAUDE.md` § Server-side signing capability) — so the gate holding is intentional.

## Leg A — componentize-py WASM build + CID advertisement (operator/deploy step)

Pre-req: PR #1857 (R0–R2) merged. Then, on an operator workstation (not a Worker/pod/CI):

```bash
cd 20-actors/torifune
componentize-py -w torifune-actor componentize actor -o dist/torifune.wasm
ipfs add --cid-version=1 --raw-leaves dist/torifune.wasm > dist/torifune.cid
node ../tsumugi/wasm/loader/verify.mjs dist/torifune.wasm   # headless CID re-verify
```

Advertise the CID in the actor's `did.json` as an `EtzhayyimWasmComponent` service via the apex
Worker `:actor/wasm-cid` (ADR-2606013800). The component is read-only + dry-run — it cannot fly
hardware (G1/G6). This leg is **safe to do on attestation of this runbook**.

## Leg B — live launch operation (operator-DID + per-mission Council, physical)

**This leg is NOT unblocked by merging this PR.** It additionally requires, per mission:

1. **G4 Transparent space access** — the mission (vehicle, payload class, trajectory class,
   disposal plan) published open-source + logged on-chain, ratified **1 SBT = 1 vote**.
2. **G1 civilian-only** — payload ∈ civilian classes; trajectory ∈
   {ascent, orbit-insertion, rendezvous, deorbit}; the `check_g1`/`check-g1` gate must pass on
   the mission graph (strike/munition unrepresentable — verified in CI).
3. **G2 zero-net-carbon** — `carbon_balance` net ≤ 0 for the fueled propellant (measured).
4. **G5 debris-responsibility** — `disposal_plan` present (no plan ⇒ refused); deorbit-debt
   handed to `hoshimori` stewardship; torifune may not add to the congestion hoshimori routes
   around.
5. **G6 no-server-key** — flight commanded by the operator's own key/ground segment, never an
   etzhayyim-operated Worker/pod/CI/bot; the actor component issues no guidance and commands no
   hardware.
6. **G8 sourcing honesty** — pre-campaign numbers are representative estimates; only post-flight
   telemetry under the operator's authority is `:authoritative`.

## What this PR attests / does NOT attest

- **Attests** (on merge, = Council PR review): the *procedure* above is charter-conformant and
  Leg A may proceed.
- **Does NOT attest**: any specific launch, any physical capability, any operator-DID action.
  Those are separate, per-mission, on-chain-attested events outside this repo's authorship.
