# ossekai 御節介 — Maturity

**Stage: R2** — ADR-2605264000. Information-arbitrage + Wellbecoming-nudge substrate over AT
Proto; the charter-clean inverse of an engagement/growth-hacking system. Passive observation,
aggregate-first publication, consent + mute/block honored, no re-engagement after opt-out.

| Dimension | State |
|---|---|
| Lexicons | ✅ 9 under `com.etzhayyim.ossekai.*` (arbitrageGapReport / wellbecomingAdvisory / feedPostAttestation / externalMentionConsent / mentionDispatchAttestation / memberDigest{Record,Subscription} / unsubscribeRecord / silenOssekaiReview) — rich const ledger |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G15) machine-readable |
| Tests | 🟡 **charter-gate suite green; agent suite has 7 pre-existing failures** — see below |
| Methods | 🟡 agent (`py/`) + cells present |

## Tests

- ✅ `methods/test_charter_gates.cljc` — **8 tests, green** (added 2026-06-16). Pins the
  anti-manipulation const ledger:
  - **no re-engagement after opt-out** (`silenOssekaiReview.reEngagementAfterOptOutCount` const 0)
    + **no commercial CRM/intel software** (`commercialIntelCrmSoftwarePenetrationPct` const 0).
  - **opt-out immediate** (`unsubscribeRecord.effectiveImmediatelyAttested` const true).
  - **G15 every post** passes mute/block check + framing audit + signed AS ossekai
    (`feedPostAttestation` const `muteBlockCheckPass`/`framingAuditPass`/`senderDidConst`).
  - **passive-only** observation (`arbitrageGapReport.passiveOnlyAttested` const true).
  - **G13 non-member mention** consented + Council-gated + rate-limited.
  - **advisory boundary routing** (`wellbecomingAdvisory` requires `crossActorDid` + `boundaryKind`).
  - **member digest** opt-in + encrypted.
- ⚠️ `py/test_agent.py` — **34 passed / 7 FAILED** (pre-existing, NOT introduced 2026-06-16;
  `py/` is git-clean). Run via `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest`.

### ⚠️ Pre-existing agent-test failures — FOLLOW-UP NEEDED

7 agent tests fail (dispatcher / publisher / member-digest / emergency). The most
charter-relevant: `test_member_digest_encrypts_no_plaintext_g8` expects
`digest.state == "draft"` (operator-gated, no auto-send) but the agent returns `"sent"`.
**Investigated 2026-06-16** (tick 17): all 7 failures share ONE root cause — `agent.py`
hardcodes `state = "posted"/"sent"` + `broadcast = True` (`# R2 Autonomous: operator gate
removed`), while the tests + `agent.py`'s own docstring (line 266) + no-server-key (G7)
require operator/member-gated drafts. CLAUDE.md documents this as an intentional "R2
Autonomous … without manual operator gating" upgrade — which conflicts with G7 unless posts
are member-signed-capability-backed (ibuki/mimamori CACAO precedent, ADR-2606111400), which
`agent.py` does NOT implement.

→ **Full analysis + 3 resolution options: [`FINDING-G7-autonomy-conflict.md`](FINDING-G7-autonomy-conflict.md)**.
NOT auto-resolved: editing the code would revert a documented R2 upgrade; editing the tests
would ratify a possible G7 weakening (forbidden by the loop mandate). Needs Council/ADR.

## R0/R2 → R3 gate

~~Resolve the 7 agent-test failures~~ ✅ **RESOLVED 2026-06-17** (see below) + Council review;
the charter-gate suite is the schema-level floor (must stay green).

### 2026-06-17 — G7 conflict RESOLVED via member-signed-capability autonomy (the 7 failures fixed)

The G7/no-server-key conflict above is **closed**. Implemented Option 1 (member-signed-capability
autonomy, the ibuki/mimamori precedent ADR-2606111400) in `py/agent.py`:

- a shared **`_outward_authorized(state)`** gate now backs all 4 outward handlers
  (`handle_aggregate_publisher` / `handle_mention_dispatcher` / `handle_member_digest` /
  `handle_emergency_advisory`). A live broadcast is authorized ONLY by an operator attestation
  (`operatorRef`) OR a presented member-signed, scoped capability (`memberCapability` with a
  non-server `memberSignature` — `_server_or_synthetic_signer` rejects blank/`anon`/`server`/
  `autonomous_system_signature`). Absent both → posts stay `:draft`, `broadcast=False`.
- `_attestation_ok` (G13) restored to a real Council Lv6+ ≥3 (≥4 if >50) check.
- all hardcoded `state="posted"/"sent"` + `broadcast=True` + the `# R2 Autonomous: operator gate
  removed` comments are gone; stale docstrings + `CLAUDE.md` Status corrected;
  `FINDING-G7-autonomy-conflict.md` marked RESOLVED.
- **`py/test_agent.py` now 41/41 green** (the 7 guards pass via the restored gate — no test was
  weakened to expect a bare `"posted"`). No server key is implied at any point.

Ratified pattern-wide by **ADR-2606181200** (R2-autonomous live-gate-removal reconciliation, 6
actors) + recorded in `90-docs/260617-r2-autonomous-live-gate-removal-charter-audit.md`. R3 now
gates only on Council review + cell activation (cells stay import-time `RuntimeError` at R0/R2).

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `ossekai.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
