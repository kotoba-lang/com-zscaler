# subaru 昴 — Transparent connectivity-commons satellite constellation (Starlink/OneWeb inversion)

**ADR**: 2606162355 · **depends**: 2605192100 (Mission Charter — §1.12 Transparent Force,
§1.13 Wellbecoming) + 2605302357 (Social Security for Humanity — §1.16 in-kind) + 2606073600
(hoshimori — orbital stewardship) + 2606162355 (torifune — the launch sibling, this wave) +
2605181100 (com.etzhayyim.encrypted — E2E) + 2606051600 (noroshi — photonics links) +
2605312345 (Datom = canonical state) + 2605215000 (Murakumo-only). **Status**: 🟡 R0+R1
(design + offline sim; live legs Council-gated).

subaru ("昴" = the Pleiades star cluster; verb root すばる = "to gather / to unite") is the
charter-clean inversion of Starlink/OneWeb: a **connectivity COMMONS**, not a subscription ISP.
A constellation of satellites that *unites/connects*. Connectivity is delivered as **§1.16
Social Security in-kind** (ADR-2605302357) — to the unconnected, to disaster zones, covenantal-
universal — **cash≡0, no ads, no subscription, no surveillance**. subaru is the **payload
`torifune` launches**, the footprint **`hoshimori` observes** for stewardship, the orbital
sibling of `watatsuna` (submarine cable), `tsutae` (handheld terminal), and `noroshi`
(光電融合 photonics inter-satellite + ground links).

Unlike hoshimori (observation-only), subaru **operates spacecraft** — so its gates *invert*
hoshimori's posture into **operate-but-only-Transparently**.

## Hard gates (constitutional — read before any change)

- **G1 — connectivity COMMONS, NEVER a surveillance / targeting / military-C2 platform.** The
  defining inversion, load-bearing because a global comms constellation is — modulo intent — a
  military C2 + ISR backbone (cf. Starshield). **No deep-packet inspection; no traffic-content
  inspection; no user-geolocation-as-product; no ISR/targeting relay.** Military-exclusive C2
  and jamming-as-weapon are **unrepresentable** (§1.12 + Rider §2(a),(c)). A dedicated test
  (`test_g1_no_surveillance_relay`) asserts no DPI / user-location-product / targeting-relay
  attribute exists in the schema.
- **G2 — no person-tracking; reciprocal-symmetric only (Rider §2(c) v3.1).** Subscriber content
  is end-to-end encrypted via `com.etzhayyim.encrypted.*` (ADR-2605181100); the constellation
  routes **ciphertext it cannot read**; no traffic-metadata retention-as-product. Privacy by
  **encryption**, not by a metadata graph (暗号化≠忘却).
- **G3 — non-profit / no-ads / cash≡0.** Connectivity is §1.16 in-kind social security
  (covenantal-universal, conversion-gated for Level-0 entitlement); external = donation /
  in-kind only; the SBT↔SBT internal carve-out (ADR-2605192115) covers internal use. **NO
  subscription, NO advertising, NO data-as-payment.**
- **G4 — Transparent constellation.** Open-source bus + protocol + on-chain ops log + 1 SBT =
  1 vote (§1.12). Never covert / proprietary.
- **G5 — orbital stewardship (couples hoshimori + torifune G5).** Low-deorbit-debt orbit +
  mandatory disposal plan + **night-sky brightness mitigation** (darksat — the astronomy /
  Wellbecoming §1.13 night-sky commons); no new debris; subaru's footprint is an input to
  hoshimori's congestion integral and must reduce, not add to, it.
- **G6 — spectrum / coordination honesty.** ITU + national spectrum coordination is DISCLOSED
  and respected (non-adjudicating, N3); no harmful interference; no unlicensed/covert band use.
- **G7 — Murakumo-only narration** (ADR-2605215000).
- **G8 — no-server-key; live ops Council-gated.** R0 = link-budget + coverage + constellation-
  design sim + ontology only; live constellation operation is Council + operator-DID gated.

## Layout

```
20-actors/subaru/
├── CLAUDE.md                          # this file
├── manifest.jsonld                    # actor manifest (4 cells, 8 gates)
├── data/                              # R1
│   └── seed-constellation.kotoba.edn  # shells/bus/links/ground-stations/service-areas seed
├── methods/                           # R1 — pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── link_budget.py                 # per-link EIRP/path-loss/margin (incl. noroshi optical)
│   ├── coverage.py                    # coverage as §1.16 entitlement reach
│   ├── stewardship.py                # deorbit-debt + darksat → hoshimori input (G5)
│   └── datom_emit.py                  # kotoba Datom-log (EAVT) emitter — canonical state
├── tests/                             # R1 (incl. G1 no-surveillance-relay assertion)
├── wasm/                              # R1 — componentize-py design
└── out/                               # GENERATED — do not hand-edit
```

## Run (R1)

```bash
cd 20-actors/subaru
bb -cp 20-actors -m subaru.methods.link-budget       # → out/link-budget-report.md
bb -cp 20-actors -m subaru.methods.coverage          # → out/coverage-report.md
bb -cp 20-actors -m subaru.methods.stewardship       # → out/stewardship-report.md
bb -cp 20-actors -m subaru.methods.datom-emit        # → out/constellation-datoms.kotoba.edn (EAVT)
```

## Cross-links

subaru is the **connect** leg of the off-Earth chain: **torifune builds + (gated) launches →
subaru operates the constellation → hoshimori observes → stewardship**. Connectivity-family
siblings: `watatsuna` (submarine cable — the sea-floor sibling), `tsutae` (handheld terminal —
the ground sibling), `noroshi` (photonics links). G1 surveillance-unrepresentability mirrors
`hoshimori` G1 (no-targeting) and the Rider §2(c) reciprocity axis; G3 cash≡0 §1.16 in-kind
mirrors the Social Security delivery pipeline (ADR-2605302358).
