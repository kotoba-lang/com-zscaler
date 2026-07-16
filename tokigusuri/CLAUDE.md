# tokigusuri 時薬 — pharmaceutical patent-cliff / off-patent-access observatory

**ADR**: 2606171300 · **depends**: 2606073400 (hokorobi — sibling pattern) · 2606073000
(inochi — KG-mirror lineage) · 2605250500 (yakushi — off-patent OTC manufacture, the
manufacture-side composer) · 2604271830 (patent-expired → open-seiyaku handoff) · 2606011800
(tsumugi — 取-concentration) · 2605262800 (legal-corpus) · 2605312345 (Datom = canonical
state) · 2605215000 (Murakumo-only). **Status**: 🟡 R0 design-only.

tokigusuri ("時薬" = *time is the medicine* — the proverb that time heals; here the patent
cliff is the clock, and time itself delivers a monopolised drug into the generic/biosimilar
commons) is the **medicine-access sibling** of the KG-mirror lineage (hokorobi / inochi /
tsumugi). It applies the mirror architecture to **pharmaceutical exclusivity** — patents,
secondary patents (evergreening), data exclusivity, SPCs, orphan exclusivity, pay-for-delay,
patent thickets — and surfaces **which essential medicines are gated by remaining exclusivity**
(the access surface) vs where **release** (generic / biosimilar / expiry) has restored access,
routed to **RELEASE** (解放 — the liberation of the medicine to all).

It closes a real roster gap: yakushi 薬師 *manufactures* perpetually-off-patent OTC APIs, and
ADR-2604271830 bridges *individual* patent-expiry → generic handoff, but the world's
**patent-cliff landscape** — the systematic map of what is gated, what has fallen, and what is
about to — had no observatory. tokigusuri is that map; its candidates flow to yakushi
(manufacture) and to the open-seiyaku handoff (ADR-2604271830).

## Hard gates (constitutional — read before any change)

- **G1 — RELEASE map, NEVER a patent-busting / FTO-opinion / trading signal.** This is the
  defining inversion of the commercial drug-patent terminal. tokigusuri is **never a
  freedom-to-operate (FTO) legal opinion, never an infringement determination, never a
  per-company verdict, never an investment / short signal on pharma equities**. Aggregate-first.
  The 取-holder is the **exclusivity-barrier**; the bearer is **patients / the public** (incl.
  LMIC populations, health-systems, payers, the uninsured); the routing is **lawful release**.
- **G2 — edge-primary (N1).** access-barrier lives ONLY on edges (`:en/barrier-load`). A node's
  access-barrier-concentration = the **integral of its incident inbound barrier 縁** (severity ×
  disclosed essentiality weight), computed **on read** — never a stored score. There is no
  `:tokigusuri/monopoly-of-drug`. **A medicine is never tallied as a 取-holder** — only an
  exclusivity-barrier or an originator-holder can be a barrier *source* (the drug is the gated
  object, never the villain; enforced in `analyze`'s `holder-imposed-kinds`).
- **G3 — non-adjudicating (N3).** patent status, expiry, and exclusivity are **DISCLOSED facts**
  (FDA Orange Book / Purple Book, EPO / national patent registers, WHO Model List of Essential
  Medicines, Medicines Patent Pool licence registry) — never tokigusuri verdicts. **No FTO /
  infringement determination, no investment advice** — that is qualified-counsel territory,
  mirroring the yakushi legal boundary.
- **G4 — lawful-routes-only (the access-not-piracy invariant).** Generic / biosimilar entry is
  surfaced ONLY for **off-patent / expiring** drugs. For still-**on-patent** drugs the only
  disclosed routes are the **lawful** ones: Medicines Patent Pool voluntary licensing and
  TRIPS / Doha flexibilities (compulsory licensing for public health). **Circumvention or
  inducement to infringe a live patent is unrepresentable** — there is no edge kind for it.
- **G5 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a paid drug-patent
  terminal (Rider §2(e)).
- **G6 — sourcing honesty.** Every record `:authoritative | :representative`; barrier-load
  values are **representative severities, not measured exclusivity terms**; coverage of all
  marketed drugs is ~0 by design (`coverage_report.cljc` makes it measurable).
- **G7 — Murakumo-only narration** (ADR-2605215000).
- **G8 — outward-gated, observation→handoff only.** Live ingest (Orange Book / WHO EML / MPP /
  patent registers) requires Council + operator DID. tokigusuri **never manufactures** — it
  observes and hands candidates to yakushi / ADR-2604271830. R0 = analyzer + schema + seed only.

## Layout

```
20-actors/tokigusuri/
├── CLAUDE.md                                  # this file
├── manifest.jsonld                            # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-pharma-patent-graph.kotoba.edn    # real PUBLIC drugs (WHO EML / MPP / biosimilar cliff) + exclusivity 縁
├── methods/                                   # pure .cljc (bb/clj) → kotoba pywasm-runnable
│   ├── analyze.cljc                           # edge-primary access-barrier vs release analyzer
│   ├── datom_emit.cljc                        # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── coverage_report.cljc                   # honest coverage + gap map (G6)
│   └── test_datom_emit.cljc                   # 2 datom-emit tests (in methods/)
├── tests/                                     # 6 tests
│   ├── test_analyze.cljc
│   └── test_coverage.cljc
├── wasm/
│   └── README.md                              # kotoba pywasm actor (componentize-py) design
├── run_tests.sh                               # bb test harness (8 green)
└── out/                                        # GENERATED — do not hand-edit / not committed
    ├── patent-cliff-report.md
    ├── patent-cliff-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/tokigusuri
bash run_tests.sh        # 8 green (edge-primary integral identity, top-is-essential sanity,
                         #          source-is-holder, transient-flagging, determinism, cliff-both-ends)
# CLI cells run from repo root via bb (the -main reads *file*; the fleet runner sets it):
#   bb 20-actors/tokigusuri/methods/analyze.cljc        → out/patent-cliff-report.md
#   bb 20-actors/tokigusuri/methods/datom_emit.cljc     → out/patent-cliff-datoms.kotoba.edn
#   bb 20-actors/tokigusuri/methods/coverage_report.cljc → out/coverage-report.md
```

## Cross-links

tokigusuri composes with **yakushi** (off-patent OTC API manufacture — the body to
tokigusuri's eye), **ADR-2604271830** (patent-expiry → open-seiyaku generic handoff — the
bridge tokigusuri's candidates feed), **tsumugi** (power-graph 取-concentration — the
originator/holder cross-link via `:barrier/links`), and the legal-corpus (ADR-2605262800 —
the statutes behind TRIPS / Bolar / SPC). Together they turn the patent cliff from an opaque,
terminal-priced data product into a public, aggregate **release map**: which essential
medicines time has freed, which it is about to, and which still need a lawful release route —
routed to access, never to a trade and never to a patent fight.
