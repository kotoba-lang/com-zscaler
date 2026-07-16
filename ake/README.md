# 朱 (ake) — community-edit membrane

> *"Wikipedia のように皆で更新したい"* — fitted to the charter.

朱 (vermillion editorial ink; **朱を入れる** = to correct a manuscript) is the actor that lets a
**信者 (SBT holder)** propose a correction to a **KG fact** or an **actor profile** — the
charter-clean answer to *"can etzhayyim's actors and information be updated Wikipedia-style?"*.

It is **NOT anonymous open-edit**. The charter requires conversion-gated, **signed** contribution,
so 朱 is a **permissioned wiki**: every edit is a **member-signed proposal appended to the kotoba
Datom log** — never an overwrite (非終末論, the full revision history is immutable and replayable).

## How an edit flows

| step | cell | what happens |
|---|---|---|
| propose | `propose` | a 信者 submits a correction; screened for **G1** (member-signed, no-server-key), **G3** (a fact or a profile — never impersonation), **G4** (provenance present) |
| triage | `edit_triage` | a **Murakumo-only LLM** scores **risk + quality** (the Wikipedia **ORES** analogue) and a **pure function** routes — *the model never accepts/rejects* (**G2**) |
| route | — | `auto-accept` (low risk + sourced) · `vote` (1 SBT = 1 vote) · `council-lv7` (invariant-adjacent) · `refused` (Charter-Rider §2 hit) |
| review | `review_vote` | optimistic fast-path / **1 SBT = 1 vote** with a 48h timelock / Council-pending |
| promote | `promote` | member-signed append (+ optional `:representative→:authoritative`); **no server signature**, `published=false` at R0 |
| history | `revision_log` | the append-only **"view history"** tab; the log only ever grows (**G5**) |

## What makes it charter-clean

- **It cannot impersonate.** You correct the *observation* of an entity, never speak *as* it
  (the `isMirror` invariant of ADR-2606042330 is preserved). `:entity-speech` is unrepresentable.
- **The AI never decides.** Triage produces a risk class + a 0–1 quality score and *routes*;
  acceptance is the optimistic rule, a 1 SBT = 1 vote, or Council — never the model (**G2**).
- **History is immutable.** No `:delete`, no `:overwrite`; a retraction is itself an appended
  datom. You can time-travel to any past value (**G5**, kotoba-canonical-state).
- **Every edit is sourced and signed.** Unsourced → refused; server-signed → refused.
- **It amends no invariant.** It *strengthens* no-server-key, kotoba-canonical-state, 1 SBT = 1
  vote, and the mirror invariant.

## Empirical R0

`methods/analyze.py` runs the `:representative` seed (5 edits, one per route) end-to-end:

| edit | what | route | outcome |
|---|---|---|---|
| e1 | sourced KG-fact (TSMC HQ postcode) | `auto-accept` | ✅ landed |
| e2 | company status → delisted (sensitive) | `vote` 8–1 | ✅ landed |
| e3 | actor-profile description fix | `vote` 5–0 | ✅ landed |
| e4 | license attr (invariant-adjacent) | `council-lv7` | pending (G7) |
| e5 | "now runs advertising" (Rider §2 hit) | `refused` | unpromotable |

**120 tests green** — `./run_tests.sh`, and the suite is **hermetic** (green in any checkout). Incl.
a route_for totality + priority sweep proving the pure router is total over the whole
risk×quality×rider domain and that auto-accept has exactly one door (the G2 guarantee), a G9
contributor suite proving per-DID isolation + a no-ranking/no-score-of-soul API lock, and incl.
a 7-test cell-chain integration that threads one edit through all five cells in sequence, a 6-test
edit-war / challenge→revert suite, an 8-test G9 contributor-trajectory suite, an 8-test genesis
bridge (asserted against a committed fixture, with a soft real-SSoT integration check), and a
10-test SSoT drift-lock.

Edit-wars are settled the Wikipedia way: a `:challenge` of the current value routes to a vote, and
an upheld challenge **reverts** to the predecessor by *appending* a rollback revision — the bad edit
is undone but stays in the immutable history (auditable), never deleted.

`methods/ingest.py` bootstraps the append-only "view history" from the **real** committed
`actor-profile-seed.kotoba.edn` (the SSoT the DID-web Worker publishes from); member edits append on
top. Building it surfaced — and this iteration closed — an `INFRA_ACTORS`↔profile-seed drift
(mitooshi + noroshi were registered but had no profile record).

## Honest R0

Design + offline routing/revision only. No live ingest, no binding vote, no promotion, no publish
(all **G8** — Council Lv6+ + operator; invariant-adjacent Lv7+). The triage scoring is
deterministic at R0 (the Murakumo-only LLM refines the scores at R1; the *routing* stays a pure
function regardless). The G9 contributor trajectory is real code (`methods/contributor.py`) but is
not yet wired to a live rate-limiter in the request path (R1). The seed is `:representative`. See
`CLAUDE.md` for the 9 gates and ADR-2606052100.
