# akuma.etzhayyim.com — Authorized Red Team Probing

スコープ契約で縛った authorized red team / vulnerability probing actor。
ADR-2605151400 が SSoT。

## Architecture

| 項目 | 値 |
|---|---|
| **Runtime** | K8s LangServer pod (`ak0m4r3d`) |
| **Edge** | SvelteKit CF Worker proxy (no business logic; ADR-2605111200) |
| **Probe execution** | K8s namespace `akuma-probe` with egress NetworkPolicy reconciled from active scopes |
| **External surface** | kotodama MCP facade only (ADR-2605091400 cytoplasmic demotion) |
| **Persistence** | `vertex_akuma_scope`, `vertex_akuma_probe`, `vertex_akuma_finding`, `vertex_akuma_audit` (append-only; no soft delete) |
| **Vault** | finding raw payloads ciphertext-stored in `vault.etzhayyim.com` (zero-knowledge invariant) |
| **Operating entity** | etzhayyim (etzhayyim Japan = vendor only) |
| **Domain** | `akuma.etzhayyim.com` / `ak0m4r3d.etzhayyim.com` |

## XRPC surface (`com.etzhayyim.apps.akuma.*`)

| NSID | Type | Purpose |
|---|---|---|
| `registerScope` | procedure | Append draft scope contract |
| `approveScope` | procedure | Owner + authority dual signature → status `active` |
| `revokeScope` | procedure | One-way revoke (either party) → NetworkPolicy reconcile |
| `runProbe` | procedure | Execute single probe (policy-gated) |
| `recordFinding` | procedure | Append finding metadata; raw payload to vault |
| `closeFinding` | procedure | Owner attests remediation; akuma re-probes to verify |
| `getScope` | query | Read scope contract |
| `listFindings` | query | List findings for a scope |

## Intrusiveness tiers

| tier | tools | requires |
|---|---|---|
| `passive` | `dns`, `whois`, `tls` | scope only |
| `safe-active` | `http-head` | + dual signature |
| `intrusive` | `nmap`, `nuclei`, `zap`, `sqlmap` | + DMN per-engagement approval row |

**Rejected (out of scope)**: exploit weaponization, RCE proof, lateral movement,
DoS. Such work goes to a separate human-driven engagement, not akuma.

## Authorization gate

Rego package `etzhayyim.akuma.scope` (`00-contracts/policies/etzhayyim/akuma/scope/policy.rego`).
Deny conditions:

1. `scope-not-active`
2. `outside-window`
3. `target-not-in-scope`
4. `target-excluded`
5. `intrusiveness-exceeds-scope`
6. `tool-exceeds-probe-tier`
7. `port-not-allowed`
8. `rate-limit-exceeded`

Unauthorized probe attempts (`target-not-in-scope` / `scope-not-active`)
emit `prune_actor_seed_tier` obligation → Bonsai seed-tier prune
(ADR-2605091800: full actor freeze + human review).

Run: `opa test 00-contracts/policies/etzhayyim/akuma/scope/ -v` (11/11 PASS).

## Closed loop with yabai / malak / threat ledger

- `yabai.risk` ≥85 (Challenge) on a scope-listed target → queue passive recon
- `_working/malak/THREAT-LEDGER.md` named target inside an active scope → queue
  probe at scope.intrusivenessTier ceiling
- `akuma.finding` → `yabai` evidence row (category `VulnFinding`, weight TBD
  in follow-up ADR)

## Key Files

| File | Purpose |
|---|---|
| `actor-manifest.jsonld` | Actor identity + governance + pipelines |
| `00-contracts/lexicons/com/etzhayyim/apps/akuma/*.json` | XRPC schemas |
| `00-contracts/policies/etzhayyim/akuma/scope/policy.rego` | Authorization Rego |
| `00-contracts/policies/etzhayyim/akuma/scope/test.rego` | 11 policy unit tests |
| `90-docs/adr/2605151400-akuma-authorized-redteam-actor.md` | ADR (SSoT) |

## Status

`status: active` (ADR active, scaffolded 2026-05-15). `production_live_pending: true`
until the human-driven steps below are executed:

1. **Authority key**: `70-tools/scripts/akuma/provision-authority-key.sh` (writes
   Ed25519 keypair to macOS Keychain `etzhayyim.akuma`; mirror to 1Password
   `etzhayyim Japan株式会社`); publish `AUTHORITY_SIGNING_KEY_PUBLIC` at
   `https://akuma.etzhayyim.com/.well-known/did.json` `verificationMethod`.
2. **K8s apply**: `rw-health-gate.sh` then `kubectl apply -k 50-infra/k8s/akuma-langserver/`.
   Implement `kotodama.akuma.scope_egress_reconciler` per pseudo-code in
   `50-infra/k8s/akuma-langserver/README.md` before reconciler image is built.
3. **Migration**: `pnpm db:migrate` (or psycopg2 phased apply per CLAUDE.md
   "Multi-Head Alembic Workaround"). Migration file:
   `30-graph/graph-schema/alembic/current_versions/r_20260515150000_vertex_akuma_redteam_scope.py`.
4. **PDS deploy**: `cd 50-infra/cloudflare/workers/atproto && npx wrangler deploy`
   (Lexicon bundle already regenerated 2026-05-15; Worker deploy is the missing step).
5. **End-to-end smoke test**: register a draft scope on a benign owned target,
   approveScope with both signatures, runProbe (passive tier), recordFinding
   (vault ciphertext), closeFinding, verify re-probe. Only after this passes
   should any production third-party target be approved.

`registered: false` flips to `true` after step 5 passes.
