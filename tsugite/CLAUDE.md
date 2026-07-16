# tsugite 継ぎ手 — world peoples-continuity (民の存続) mirror

**ADR**: 2606073800 · **depends**: 2606073000 (inochi) + 2606073200 (asobi) + 2606073400
(hokorobi) + 2606073600 (hoshimori — sibling pattern) · 2605181100 (PII envelope) · 2605302130
(himotoki — consent discipline) · 2605301600 (danjo) · 2605312345 (Datom = canonical state) ·
2605215000 (Murakumo-only). **Status**: 🟡 R0 design-only.

tsugite ("継ぎ手" = the one who carries on / inheritor) is the **human-collective sibling** of
the KG-mirror lineage (inochi living-world / asobi freed-time / hokorobi finance / hoshimori
orbit). It mirrors **human collectives** — migration / refugee / stateless populations,
indigenous peoples, and language communities — the **languages they carry**, the
**displacement + erasure pressures** they bear, and the **havens** that protect them, into the
kotoba Datom log, and surfaces that pressure routed to **CONTINUITY (継承)**: safe passage +
protection + revitalization.

It closes coverage-gap **E** of ADR-2606073000 — the final gap — joining the two strands the
catalog named: human movement (migration / refugees / statelessness) and endangered-language /
intangible-culture preservation.

> **Naming note.** An earlier draft used 民 (*tami*); it was rejected because that
> character's oracle-bone etymology depicts a blinded/subjugated person — the opposite of a
> dignity-centered actor's intent. 継ぎ手 names the value itself (継承, carrying-on), as
> inochi (命→restoration) does.

## Hard gates (constitutional — read before any change)

- **G1 — PEOPLES-CONTINUITY map, NEVER person-tracking.** This is the defining, load-bearing
  inversion (this is the most PII-sensitive domain). tsugite works at **AGGREGATE / collective
  scale only**: there are **no individual records, no real-time location, no biometric**; every
  `:people` node is a collective (`:people/scope :aggregate`). It is **never** a border-
  enforcement / deportation / surveillance aid — it inverts that apparatus, routing to refuge
  and revitalization, **never to interdiction**. A dedicated test
  (`test_g1_aggregate_only_no_person_tracking`) asserts the absence of any person/locator attr.
- **G2 — edge-primary (N1).** Pressure lives ONLY on edges (`:en/peril-load`). A bearer's
  continuity-need = the **integral of its incident inbound pressure 縁** (severity × disclosed
  vitality weight), computed **on read** — never a stored per-collective score. There is no
  `:tsugite/score-of-people`.
- **G3 — non-adjudicating (N3).** Displacement figures and language-vitality categories are
  **DISCLOSED facts** (UNHCR / IOM / UNESCO / Ethnologue), never tsugite verdicts.
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. Never a private/covert
  registry of peoples.
- **G5 — sourcing honesty.** Every record `:authoritative | :representative`; peril-load values
  are **representative severities, not individual data**.
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Live aggregate ingest (UNHCR / IOM / UNESCO feeds) requires Council +
  operator DID. R0 = analyzer + schema + seed only.
- **G8 — consent-bound PII.** Any member-linked datum is consent-bound + XChaCha20-Poly1305-
  enveloped (ADR-2605181100); the public seed carries none.

## Layout

```
20-actors/tsugite/
├── CLAUDE.md                           # this file
├── manifest.jsonld                     # actor manifest (3 cells, 8 gates)
├── data/
│   └── seed-peoples-graph.kotoba.edn   # real PUBLIC AGGREGATE collectives/languages + 縁
├── methods/                            # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                      # edge-primary continuity vs protection analyzer
│   ├── datom_emit.py                   # kotoba Datom-log (EAVT) emitter — canonical state
│   └── coverage_report.py              # honest coverage + gap map (G5)
├── tests/                              # 9 tests, pure stdlib (incl. G1 no-person-tracking)
│   ├── test_analyze.py
│   └── test_coverage.py
├── wasm/
│   └── README.md                       # kotoba pywasm actor (componentize-py) design
└── out/                                # GENERATED — do not hand-edit
    ├── continuity-report.md
    ├── peoples-datoms.kotoba.edn
    └── coverage-report.md
```

## Run

```bash
cd 20-actors/tsugite
python3 methods/analyze.py          # → out/continuity-report.md
python3 methods/datom_emit.py       # → out/peoples-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py  # → out/coverage-report.md
python3 tests/test_analyze.py && python3 tests/test_coverage.py   # 9 green
```

## Cross-links

tsugite sits beside **kataribe** (publishing/translation — a revitalization vehicle),
**manabi** (education — mother-tongue education is a haven here), **hagukumi** (care for
displaced families), and **himotoki** (consent-bound disclosure — the PII discipline tsugite
inherits). The `:speaks` 縁 couples a people to its tongue: when displacement imperils the
people, transmission-fragility shows the language at downstream risk — both routed to
continuity, never to tracking. The seed surfaces **Ainu** as the top continuity-need (critically
endangered + assimilation + language-shift) and **Māori/Hawaiian** carrying real protection
buffers (Kōhanga Reo / Pūnana Leo) — the revitalization model routed forward.
