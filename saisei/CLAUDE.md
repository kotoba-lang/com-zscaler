# saisei 再生 — citizen self-filing debt-relief concierge

**DID**: `did:web:etzhayyim.com:actor:saisei` (registration in the did-web SSoT
deferred — see ADR-2607061800 Consequences) · **Tier**: B · **Status**: 🟡 R0 (seed —
4 jurisdictions, no cells run, no live self-publication) · **ADR**: 2607061800 ·
**depends**: 2606112301 + 2606112400 (tate — UPL-safe concierge pattern this actor
ports) · 2605262700 (chigiri UPL prior art) · 2605231525 (no-server-key) · 2605215000
(Murakumo-only) · 2605312345 (Datom = canonical state)

**Jurisdictions (R0)**: `:jp :us :uk :de` — 4 of ~193 (`coverage_report.cljc`
measures + names the gap, G10; the worklist drops entries off automatically once
covered). Uncovered jurisdictions degrade to `:unknown-jurisdiction` — saisei
**never guesses foreign insolvency law**.

## What this is

The **debtor-initiated** counterpart to [`tate`](../tate/)'s creditor-side
`:insolvency` track (tate discloses how to respond when a THIRD PARTY's insolvency
notice arrives — proof-of-claim, 債権届出; saisei discloses how a member files
**their own** formal insolvency petition). One leg over the member's OWN declared
situation:

**申立て支援** (`methods/filing_plan.cljc`) — a member's self-declared jurisdiction
classified against the coded procedure registry (`data/procedure-registry.edn`) →
**ALL** registered procedures for that jurisdiction disclosed as distinct tracks
(saisei never picks Chapter 7 vs Chapter 13, or 自己破産 vs 個人再生, on the
member's behalf — that choice is exactly the individualized judgment G2 forbids),
each carrying: eligibility SIGNALS (not a verdict), any statutorily MANDATORY
pre-filing step surfaced as a **blocking** step (not skippable), required-documents
checklist, fee + fee-waiver route, DISCLOSED discharge-timeline rule, and the
jurisdiction's free/public insolvency-counseling referral directory (always
present, unconditional on status).

## Hard gates (constitutional — read before any change)

- **G1 member-principal, own situation only.** R0 seeds are fully `:synthetic`;
  live member data is consent-bound + encrypted (`com.etzhayyim.encrypted.*`).
- **G2 non-adjudicating.** A track is a pointer to disclosed eligibility signals +
  statute (`:verify-current-law true` everywhere), never "you qualify for this."
  Report language stays 可能性/専門家確認 (test-enforced). Choosing between
  procedures within a jurisdiction is the member's decision — saisei discloses
  every registered track, never ranks or filters them.
- **G3 UPL (弁護士法72条 / state UPL / RDG / LSA 2007).** No representation, no
  drafting/filing on the member's behalf — `make-option` **raises** on
  `:representation`; every option is `:self-submit`, `filed_by: member`.
- **G4 timeline honesty.** saisei **never computes a calendar date** — it emits
  the disclosed rule text + anchor for discharge/plan timelines.
- **G5 mandatory-precondition honesty.** A jurisdiction's legally-required
  pre-filing step (e.g. DE's `außergerichtlicher Einigungsversuch` per §305 InsO,
  US `§109(h)` credit counseling, UK DRO's approved-intermediary requirement) is
  modeled as a **blocking** step (`"blocked_on_precondition": true`) — it is never
  presented as optional or skippable.
- **G6 no-outcome-promise.** Discharge is always framed as the court's decision;
  saisei discloses the rule, never predicts the outcome.
- **G7 referral-forward.** Every plan carries the jurisdiction's free/public
  insolvency-counseling directory, unconditionally (higher bar than tate's — a
  misfiled bankruptcy petition has worse consequences than a missed civil-notice
  deadline).
- **G8 kotoba-eavt-audit.** Registries (ground) and plans (transient,
  `:bond/is-transient` — computed on read) on the append-only Datom log.
- **G9 Murakumo-only.** R0 ships **zero** LLM calls — pure registry lookup; any
  future LLM step (situation classification) must go through LiteLLM
  `127.0.0.1:4000` only (ADR-2605215000).
- **G10 jurisdiction-honesty.** Procedures never cross jurisdictions; an uncovered
  jurisdiction degrades to `:unknown-jurisdiction` — `coverage_report.cljc`
  reports covered/193 + named gaps, never silently claims coverage it doesn't have.

## Non-goals

N1 not a law firm / no advice · N2 does not draft or file the petition on the
member's behalf (UPL) — self-file only · N3 does not adjudicate eligibility or
promise a discharge outcome (G2/G6) · N4 does not duplicate tate's `:insolvency`
track (creditor-side response to a THIRD PARTY's notice stays with tate) or
toritsugi's administrative filings — saisei owns exactly the debtor-initiated
court/formal-insolvency-forum petition surface · N5 does not duplicate yobel
(voluntary doctrinal release) or amnesty (legal-person institutional
restructuring) — saisei is the formal, legally-binding natural-person procedure
layer those two actors name as their fallback · N6 no evasion of lawful
obligations — non-dischargeable debt categories are disclosed honestly, never
planned around.

## Boundaries (who owns what)

| Concern | Owner |
|---|---|
| Debtor-initiated formal insolvency petition (self-file, own procedure) | **saisei** (this actor) |
| Creditor-side response to a third party's insolvency notice (proof-of-claim, 債権届出) | **tate** |
| Proactive administrative/municipal procedures (passport, registry, tax filing) | **toritsugi** |
| Voluntary doctrinal debt release (shmita/jubilee/tokusei-rei/political amnesty) | **yobel** |
| Legal-person institutional debt restructuring (sovereign/corporate) | **amnesty** |
| legal-procedure substrate (registry 基盤) | **chigiri** |

## Layout

```
20-actors/saisei/
├── CLAUDE.md                      # this file
├── README.md
├── manifest.edn                   # actor manifest (4 cells, 10 gates, 6 non-goals)
├── data/
│   ├── jurisdictions.edn          # jurisdiction registry: UPL anchor + forum + referrals (R0, 4 juris)
│   ├── procedure-registry.edn     # jurisdiction-keyed procedure registry (7 procs: jp 2 / us 2 / uk 2 / de 1)
│   └── seed-member-docs.edn       # SYNTHETIC member situations, incl. one uncovered-jurisdiction probe (G1/G10)
├── methods/                        # clj/bb (.cljc) — kotoba-native
│   ├── edn.cljc                    # minimal EDN reader (string-keyed fidelity convention, tate-pattern)
│   ├── filing_plan.cljc            # classify + build-plan (all tracks disclosed, never ranked — G2)
│   ├── coverage_report.cljc        # honest jurisdiction coverage + named gaps (G10)
│   └── datom_emit.cljc             # kotoba Datom-log (EAVT) emitter
├── tests/                          # clj/bb (.cljc) — bb run_tests.sh (14 tests / 53 assertions)
│   ├── test_filing_plan.cljc
│   └── test_coverage.cljc
└── run_tests.sh
```

Deferred to a follow-up wave (mirrors `amnesty`'s explicit phase deferral):
`coverage_publish.cljc` (public anonymized digest), `cid.cljc` (content-addressing),
`site_gen.cljc` (crawlable static site), `case_actors_gen.cljc` (1 case = 1 actor),
did-web registration + `public/actor/saisei/{did.json,profile.json}`, and the
IVA (UK, insolvency-practitioner-mediated) referral-only track.

## Run

```bash
# clj/bb (babashka), run from the repo root (classpath = 20-actors). NOT python.
bash 20-actors/saisei/run_tests.sh   # full suite: 14 tests / 53 assertions green

# ad-hoc, from repo root:
bb --classpath 20-actors -e '(require (quote [saisei.methods.coverage-report :as c])) (print (c/report (c/coverage)))'
bb --classpath 20-actors -e '(require (quote [saisei.methods.datom-emit :as d])) (println (count (d/emit)))'
```

## Do not

- Do not emit a qualification/discharge-outcome verdict, drop a statutory anchor,
  or rank/filter which registered procedure applies to a jurisdiction — G2/G6
  (tests enforce).
- Do not add a `:representation` option kind or any draft/file-on-behalf leg — G3
  (`make-option` raises; tests enforce).
- Do not compute a calendar date for a discharge/plan timeline — G4 (tests enforce:
  no `\d{4}-\d{2}-\d{2}` pattern in any disclosed rule text).
- Do not present a jurisdiction's statutorily-mandatory pre-filing step as
  optional/skippable — G5 (tests enforce `blocked_on_precondition`).
- Do not ingest real member situations into `data/` — seeds stay `:synthetic`;
  live data is consent-gated + encrypted (ADR-2605181100).
- Statutory rules carry `:verify-current-law true` — when amending them, cite the
  current statute text, never memory.
- Do not answer for an uncovered jurisdiction (no LLM-guessed foreign insolvency
  law), and never let a procedure cross jurisdictions — G10 (tests enforce).
  Adding a jurisdiction = one `jurisdictions.edn` entry + procedures + tests; no
  code change.
