# 20-actors/tsukuroi — CLAUDE.md

## Identity

- **Name**: tsukuroi (繕い — *to mend / to patch*; the constructive sibling of `akuma` 悪魔)
- **DID**: `did:web:tsukuroi.etzhayyim.com`
- **nanoid**: `t5kur0i9`
- **ADR**: ADR-2605291500 (R0 scaffold, 2026-05-29) — SSoT
- **Diagnosis sibling**: ADR-2605151400 (akuma — authorized red team / vulnerability probing)
- **Parent ADRs**: ADR-2605192100 (Mission Charter), ADR-2605192200 (Charter Rider), ADR-2605215000 (Murakumo-only), ADR-2605231525 (no platform-held key), ADR-2605262130 (kotoba)
- **Status**: R0 scaffold — 7 cells path-reserved (created in W1); 5 Lexicon skeletons under `com.etzhayyim.tsukuroi.*`
- **Form**: 任意団体 internal remediation substrate (NOT a managed patch-as-a-service; non-profit only)

## One-line purpose

Take an **owner-attested vulnerability finding** (from akuma) → synthesize a
**defensive fix patch** → validate in an egress-restricted sandbox →
**propose** it to the authorized target (fork-and-PR / signed patch bundle).
**Propose-only**: a human owner merges. tsukuroi performs **no probing** and
holds **no merge/deploy authority and no platform master key**.

## Capability ceiling (CRITICAL — IMMUTABLE)

1. **PROPOSE-ONLY (G4)** — open PR / emit patch only. NO merge, self-approve,
   force-push to protected branch, deploy, or release. `mergeAuthorityHeld`
   and `autonomousMerge` are schema const `false`.
2. **DEFENSIVE-ONLY (G5)** — fixes only. No PoC / exploit / offensive payload,
   even as a test fixture (Charter Rider §2(a)). `defensiveOnly` const `true`.
3. **NO PROBING (G3)** — vulnerability input only via an akuma `finding_cid`
   or owner-signed finding report. Acquiring probe capability is a critical
   violation (negative-space) → cell halt + `chigiri.disputeMediation`.
4. **SCOPED WRITE (G6)** — `pathsTouched ⊆ mandate.allowedPaths`.
5. **NO PLATFORM-HELD KEY (G8)** — submission uses an owner-issued,
   least-privilege, expiring, fork-PR-only delegated credential (vault
   ciphertext) per ADR-2605231525. `platformHeldKeyCount=0`.

## RemediationMandate contract

`com.etzhayyim.tsukuroi.remediationMandate` — `target_repo`, `finding_cid`
(upstream akuma finding), `allowed_paths[]`, `submission_mode`
(`fork-pr` | `patch-file` | `config-diff`), `owner_did`+`owner_signature`,
`authority_did`+`authority_signature`, `valid_from`/`valid_until`,
`legal_basis`, `delegation_credential_ref` (ref, not secret),
`mergeAuthorityHeld` const false, `max_pr_per_window`, `revoked` (one-way).

Valid iff both signatures verify, in window, not revoked, and `finding_cid`
resolves to an active akuma finding on the same owner+target.

## Cells (7; R0 path-reserved under `40-engine/kotoba/crates/kotoba-kotodama/cells/tsukuroi_*/`)

| Cell | Purpose | Key gate |
|---|---|---|
| `finding_intake` | consume akuma.finding within an active mandate | G3, G7 |
| `patch_synthesis` | Murakumo-only LLM drafts candidate defensive diff | G10 |
| `charter_rider_scan` | §2(a)..(h) scan + offensive/PoC rejection | G1, G5 |
| `patch_validation` | sandbox build/test (never the live target) | G9 |
| `pr_submission` | fork-and-PR via owner-delegated expiring credential | G4, G8 |
| `closure_verification` | request akuma re-probe; close on owner-merge + pass | G11 |
| `silen_tsukuroi_review` | quarterly Council audit; structural zero-counters | G13 |

Each cell is import-time `RuntimeError("tsukuroi R0 scaffold: activate via
Council ADR + R1 ratification")` until R1.

## Lexicons (`com.etzhayyim.tsukuroi.*`)

`remediationMandate` · `patchProposal` (`defensiveOnly` const true /
`autonomousMerge` const false / `pathsTouched ⊆ allowedPaths`) ·
`patchValidationResult` (`ranAgainstLiveTarget` const false) ·
`closureAttestation` (`ownerMerged` + `akumaReprobePass` ⇒ `remediated`) ·
`silenTsukuroiReview` (zero-counters: `autonomousMergeCount`,
`exploitArtifactCount`, `outOfScopeWriteCount`, `platformHeldKeyCount` — any
nonzero ⇒ cell halt + `chigiri.disputeMediation`).

## Closed loop with akuma

```
akuma.finding (VulnFinding)
   → tsukuroi.finding_intake   (active mandate, same owner+target+finding_cid)
   → tsukuroi.patch_synthesis → charter_rider_scan → patch_validation
   → tsukuroi.pr_submission    (propose-only)
   → [HUMAN OWNER MERGES]
   → tsukuroi.closure_verification → akuma re-probe (closeFinding path)
   → closureAttestation.remediated = true
```

## Topology (kotoba-native)

- **Edge**: SvelteKit CF Worker proxy `tsukuroi.etzhayyim.com` (edge only)
- **Runtime**: K8s `tsukuroi-langserver`; external surface = kotodama MCP facade only
- **Synthesis+validation**: egress-restricted `tsukuroi-validate` namespace;
  egress only to the owner-attested git submission endpoint (fork remote),
  never to the live target
- **Persistence**: kotoba datom (EAVT) + MST `com.etzhayyim.tsukuroi.*`; raw
  payloads + delegated credentials ciphertext in `vault.etzhayyim.com`

## R0 → R3

- **R0** (this commit): charter + scaffold; 7 cell paths + 5 Lexicon skeletons; zero runtime code.
- **R1** (Council Lv6+ ≥3 ratify + ≥1 filled seat): 3 core cells + datom schema; `patch-file` mode only; benign owned-target / internal lab repo; findings internal.
- **R2** (+30-day public objection): + `patch_validation` + `pr_submission` (`fork-pr`) + `closure_verification`; first `silenTsukuroiReview`; first external mandate after a benign dry run.
- **R3** (+Council Lv7+ for submission_mode expansion; autonomous merge NEVER): `config-diff` mode; multi-target mandates; federation with toritate + chigiri + kataribe.

## Related Files

- `/90-docs/adr/2605291500-tsukuroi-authorized-remediation-tier-b-actor-r0.md` — Master ADR (SSoT)
- `/90-docs/adr/2605151400-akuma-authorized-redteam-actor.md` — diagnosis sibling
- `/90-docs/adr/2605231525-server-side-signing-capability.md` — no platform-held key
- `/90-docs/adr/2605215000-etzhayyim-inference-murakumo-only-no-runpod.md` — Murakumo-only
- `/90-docs/adr/2605262130-kotoba-storage-substrate-unification.md` — storage substrate
- `/90-docs/adr/2605262700-chigiri-legal-procedure-tier-b-actor-r0.md` — dispute mediation sink
- `/CHARTER-RIDER.md` · `/COUNCIL.md` · `/CLAUDE.md`
