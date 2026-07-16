# ossekai — FINDING: R2-Autonomous publication vs no-server-key (G7) conflict

**Status**: RESOLVED 2026-06-17 — Option 1 (member-signed-capability autonomy) implemented.
**Found**: 2026-06-16 (`/loop` coverage iteration, surfaced by `py/test_agent.py`).
**Severity**: HIGH — touches a Tier-1 substrate invariant (no-server-key, root CLAUDE.md).

## Resolution (2026-06-17)

Option 1 (member-signed-capability autonomy, ibuki/mimamori parity) was implemented in
`py/agent.py`. The internal inconsistency is gone: a shared `_outward_authorized(state)` gate
now backs all four outward handlers (`handle_aggregate_publisher` / `handle_mention_dispatcher`
/ `handle_member_digest` / `handle_emergency_advisory`). A live broadcast is authorized ONLY by
an operator attestation (`operatorRef`) OR a presented member-signed, scoped capability
(`memberCapability` with a non-server `memberSignature` — `_server_or_synthetic_signer` rejects
blank/`anon`/`server`/`autonomous_system_signature`); absent both, posts stay `:draft` and
`broadcast=False`. `_attestation_ok` (G13) is restored to actually require Council Lv6+ ≥3
(≥4 if >50). All hardcoded `state="posted"/"sent"` + `broadcast=True` + the "operator gate
removed" comments are gone. `py/test_agent.py` passes **41/41** (the 7 guards are green via the
restored gate — no test was weakened to expect a bare `"posted"`). `CLAUDE.md` Status updated to
match. No server key is implied at any point. Cross-ref: the systemic audit
`90-docs/260617-r2-autonomous-live-gate-removal-charter-audit.md`.

## The conflict

Three sources of truth disagree about whether ossekai may publish to AT Proto **without an
operator/member in the loop**:

| Source | Says |
|---|---|
| `20-actors/ossekai/CLAUDE.md` (Identity + Status) | "**R2 Autonomous** … fully operational … publication **without manual operator gating**" |
| `py/agent.py` (lines 291/302/419/568/603) | hardcodes `state = "posted"`/`"sent"` + `broadcast = True`, commented `# R2 Autonomous: operator gate removed` — **ignores** the `operator_ref` it reads |
| `py/test_agent.py` (7 tests) + `agent.py` docstring line 266 | "without `operatorRef` posts are **:draft, nothing broadcast**" — operator-gated (no-server-key) |
| root `CLAUDE.md` substrate boundary | **no-server-key (G7)**: no platform-held private key in etzhayyim-operated pods/CronJobs/bots |

`agent.py` is **internally inconsistent**: its docstring (line 266) promises operator-gating
while its body unconditionally posts. The 7 `test_agent.py` failures are this inconsistency.

## Why this is a real G7 question (not just a stale test)

An etzhayyim-operated agent that **auto-broadcasts** to AT Proto needs a key to sign the
`app.bsky.feed.post` records. A platform-held signing key is exactly what **no-server-key
(G7)** forbids. So "autonomous publication without operator gating" is only charter-clean if
the post is signed by a **member-held key**, not a server key.

**There is a charter-clean precedent for exactly this**: `ibuki` / `mimamori` achieve
autonomous posting via a **member-signed, scoped, revocable CACAO capability**
(ADR-2606111400): a member Ed25519-signs a delegation in their OWN runtime, the organism
*presents* the opaque capability (never holds a key), and the write is on-record attributed
to the consenting member. `ossekai/py/agent.py` does **not** implement this — it has no
`cacao` / `capability` / `memberPrincipal` path; it simply sets `broadcast = True`.

## Resolution options (Council / ADR decision)

1. **Member-signed-capability autonomy (recommended, ibuki/mimamori parity)** — keep R2
   autonomy, but route every auto-post through a member-signed CACAO capability so the
   member is the write author. Then update `test_agent.py` to assert the capability path
   (not bare `"posted"`). G7 preserved.
2. **Restore operator-gating** — honor the `operator_ref` the code already reads: no
   `operatorRef`/capability → `state = "draft"`, `broadcast = False`; present → `"posted"`.
   This makes the existing 7 tests pass as-is and matches `agent.py`'s own docstring.
3. **Document an explicit G7 exemption** — only via the documented `// no-server-key:`
   marker + an ADR amendment ratified Council Lv7+ (high bar; not obviously available here).

## What this iteration did NOT do (and why)

- Did **not** edit `agent.py` to force drafts — that would unilaterally revert a
  CLAUDE.md-documented R2-Autonomous upgrade without ratification.
- Did **not** edit `test_agent.py` to expect `"posted"` — that would **ratify a possible G7
  weakening**, which the `/loop` mandate forbids ("no-server-key G7 を絶対に弱めない").
- The lexicon-level charter-gate suite (`methods/test_charter_gates.py`, 8 green) is
  unaffected and stands.

**Action owner**: operator + Council. Pick option 1/2/3, then reconcile `agent.py` +
`test_agent.py` + `CLAUDE.md` in one ADR-referenced change.
