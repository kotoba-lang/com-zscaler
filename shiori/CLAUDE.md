# shiori 栞 — human-Wellbecoming detractor observatory + transparent intervention

**ADR**: 2606082100 · **depends**: 2605192100 (Mission Charter — §1.13 Wellbecoming + anti-addictive,
§1.4 anti-individualism, §1.7 multi-generational, §1.12.B Transparent Religious Force /
covert-operations prohibited, §2(c) v3.1 no monetized-or-asymmetric surveillance — ADR-2606082400) +
2605264000 (ossekai — the intervention carrier) + 2605181100 (PII envelope) +
2605301600 (danjo) + 2606011800 (tsumugi) + 2606066000 (keizu) + 2605302300 (kanae) + 2605312345
(Datom = canonical state) + 2605215000 (Murakumo-only). **Status**: 🟡 R0 design-only.

shiori ("栞" = 枝折り — the branch-marker a traveller breaks to mark a path through difficult
terrain; later 道標 / a guide) is the **human-Wellbecoming sibling** of the KG-mirror lineage
(inochi living-world / asobi freed-time / hokorobi finance / tsugite peoples). It is the
**取-concentration HUMAN side**: where the other mirrors weave power, ecology, finance and peoples,
shiori weaves **what structurally diminishes human Wellbecoming** — the factors that make people
unhappy (precarity / overwork / isolation / addictive-design / debt / housing-insecurity /
chronic-pain / information-pollution / discrimination / care-deprivation / meaning-deficit /
sleep-deprivation), the **aggregate cohorts** that bear them, the **structural drivers** (patterns,
never named entities) that impose them, and the **mitigators** (havens) that relieve them — into
the kotoba Datom log, and surfaces that detraction routed to **RELIEF (救い)**: a **transparent**
Wellbecoming intervention carried by **ossekai**, plus the **relief gap** that names who is most
under-served.

This closes the gap the founder named directly: the prior roster could **analyse / identify /
warn** about detractors (the accountability mirrors) and **care** for bodies/minds (yakushi /
iyashi / kokoro / suimin), but had no actor that fuses *what burdens people* with *a transparent
route to relieve it*. shiori is that fusion — observatory + intervention — built **inside** the
Charter's hard limits on influence, not around them.

> **Why this is not a "happiness engine."** The Charter forbids per-person scoring, covert
> persuasion, and dark patterns. shiori therefore works at **cohort scale**, names **structural**
> causes (not individuals to blame), routes relief through **ossekai** (which logs every nudge
> on-chain and is consent-bound), and is itself **anti-addictive** — it may never deploy the very
> engagement-maximising technique it catalogues as a detractor.

## Hard gates (constitutional — read before any change)

- **G1 — WELLBECOMING-RELIEF map, NEVER a per-person affect/manipulation engine.** The defining,
  load-bearing inversion. shiori works at **AGGREGATE / cohort scale only**: **no individual
  records, no per-person happiness/mood/affect score, no biometric**; every `:cohort` node carries
  `:cohort/scope :aggregate`. **Drivers are structural PATTERNS** (work-culture, design-pattern,
  credit-market), **never named orgs/persons** (map-not-target). It routes detraction to relief,
  **never to coercion / dark patterns** — and shiori may **not itself** use engagement-maximising
  technique (anti-addictive, §1.13). Tests
  (`test_g1_aggregate_only_no_person_scoring`, `test_g1_anti_addictive_mitigators_are_not_engagement`,
  `test_imposed_driver_is_structural_pattern_not_entity`) assert all three.
- **G2 — edge-primary (N1).** Detraction lives ONLY on edges (`:en/load`). A cohort's
  wellbecoming-burden = the **integral of its incident inbound `:diminishes` 縁** (load × disclosed
  severity weight), computed **on read** — never a stored per-cohort/per-person score. There is no
  `:shiori/score-of-cohort`.
- **G3 — non-adjudicating (N3).** Detractor-severity bands and cohort burden patterns are
  **DISCLOSED facts** (OECD Better Life / WHO determinants-of-health / public wellbeing surveys),
  never shiori verdicts. shiori diagnoses no person.
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a private/covert profiling
  registry of people.
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; load values are
  representative severities, not individual data; no fabricated coverage.
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Live ingest **and live intervention routing** (to ossekai targeted-path /
  dry-run social) require Council + operator DID. R0 = analyzer + schema + seed only.
- **G8 — TRANSPARENT-INTERVENTION only** (§1.12.B Transparent Religious Force — covert operations
  prohibited; transparency requirement = full on-chain monitoring + open-source + 1 SBT = 1 vote).
  Every nudge is **logged on-chain**, **consent-bound** for any member-targeted intervention, and
  **never covert / manipulative / coercive**. shiori **proposes**; **ossekai (R2) carries**; the
  recipient can **always see why**. Relief is routed to **structural change first**, not individual
  blame (§1.4 / §1.7).
  - **Permitted transparent action channels** (the only ways an intervention may leave the
    boundary): (1) **atproto social post** — a public `app.bsky.feed.post` over the §4 MST membrane
    (ADR-2605231902); public-by-construction, so transparent by construction. (2) **actor-bound
    email** — sent from the actor's own address via the openmail pipeline (`50-infra/openmail-postage/`),
    where the **full email body is disclosed** (the same content is posted/append-logged on-chain),
    never a private back-channel. A nudge that cannot be shown in full, in public, is not a
    permitted action — opacity is the disqualifier, not the medium. (Monetized or one-sided /
    asymmetric per-person data retention stays forbidden under §2(c) v3.1 — monetized-or-asymmetric
    surveillance, ADR-2606082400 — regardless of channel; shiori holds no individual data at all,
    so it cannot be an asymmetric watcher. Reciprocal/symmetric 相互監視 is itself affirmed, but
    shiori takes the strictest cohort-only posture.)

## How it routes (the intervention chain)

```
shiori (observe)                 → finds relief-gap per cohort + 取-holder drivers + design gaps
  ├─ relief-gap  → ossekai       → transparent, consent-bound Wellbecoming-nudge (G8)
  ├─ 取-concentration (drivers) → danjo / tsumugi / keizu  → structural transparency (never target)
  └─ relief routes              → kokoro / iyashi / hagukumi / wakai (the actual havens)
```

shiori never carries an intervention itself — it is the **map + the routing signal**. The act of
influence belongs to ossekai (consent-bound, on-chain-logged); the act of care belongs to the L4
Care actors; the act of transparency belongs to the accountability mirrors.

## Layout

```
20-actors/shiori/
├── CLAUDE.md                              # this file
├── manifest.jsonld                        # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-wellbecoming-graph.kotoba.edn # structural detractors + AGGREGATE cohorts + 縁
├── methods/                               # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                         # edge-primary detraction vs relief analyzer
│   ├── datom_emit.py                      # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.py                 # honest coverage + gap map (G5)
├── tests/                                 # 11 tests, pure stdlib (incl. 3 G1 inversions)
│   ├── test_analyze.py
│   └── test_coverage.py
├── wasm/
│   └── README.md                          # kotoba pywasm actor (componentize-py) design
└── out/                                   # GENERATED — do not hand-edit
    ├── relief-gap-report.md
    ├── wellbecoming-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/shiori
python3 methods/analyze.py          # → out/relief-gap-report.md
python3 methods/datom_emit.py       # → out/wellbecoming-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py  # → out/coverage-report.md
python3 tests/test_analyze.py && python3 tests/test_coverage.py   # 11 green
```

## Cross-links

shiori sits beside **ossekai** (its intervention carrier — the only actor that *acts* on the
relief-gap, consent-bound + on-chain), **kokoro / iyashi / hagukumi / wakai** (the actual relief
havens), and the accountability mirrors **danjo / tsumugi / keizu** (where structural-driver
concentration is routed for transparency). The seed surfaces **low-income households** as the top
relief-gap (precarity + debt + housing-insecurity + discrimination, thin relief) and names
**information-pollution / discrimination / sleep-deprivation** as detractions with **no relief
route yet** — the intervention-design gap for the next wave.
