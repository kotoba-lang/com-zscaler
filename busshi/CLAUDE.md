# 20-actors/busshi — CLAUDE.md

## What this is

**busshi 物資** — the world **commodity & raw-materials** KG-mirror observatory.
Mirrors commodities/materials (precious metals 金銀PGM · base metals · rare/critical
metals · energy 石油ガス石炭 · agricultural softs) into the kotoba Datom log and runs
the **§2(l) multi-generational (子・孫) × wellbecoming RISK axis** (ADR-2606161700).
Umbrella sibling of `rare-earth-coverage` (rare-metals specialist), `kabuto`
(supply-chain), `kanjō` (financials), `kasa` (compute), `shionome` (capital flows).

`did:web:etzhayyim.com:busshi` · `com.etzhayyim.busshi.*` · ADR-2606161730 · clj-native R0.

## OBSERVATION ONLY (hard invariants — proven by tests)

- **取引しない (never a trade)** — G1: no buy/sell/position/order; `:busshi/trade` is unrepresentable.
- **採掘しない (never extracts)** — N1: extraction is gated by §2(l) as its OWN actor (ADR-2606161700); busshi only observes.
- **never forecasts** — G3: a producer SHARE + a price LEVEL are DISCLOSED facts, never a verdict and never a forecast point (mitooshi 見通し owns distributions). No `:busshi/signal`.
- **a resilience map, NEVER a target-list** — G2/G5: aggregate-first, no mine/well coordinates; the report says so in words.

## Analytical core (§2(l) risk axis)

Per commodity (pure clj): top-producer-share + named-HHI (concentration, `:other`
residual excluded) → chokepoint-risk; **multigen-risk** = 0.40·monopoly +
0.30·carbon-intensity + 0.30·irreversibility; **route** ∈
`{:resilience, :de-monopolization, :restoration}` by dominant driver:

- `:de-monopolization` → route-around (abaki / kabuto / tsumugi)
- `:restoration` → circular path (kanayama recycling / kamado energy-transition / inochi)
- `:resilience` → diversify supply + build stock/recovery buffers (default)

## Files

```
methods/busshi_edn.cljc   loader + classify (clojure.edn; :clj file I/O)
methods/analyze.cljc      analyze → datoms → render-datoms → coverage → render-report (+ bb CLI)
methods/kotoba.cljc       Wave 2: content-addressed append-only OBSERVATION LEDGER (tamper-evident commit-DAG)
methods/autorun.cljc      Wave 2: deterministic, idempotent-by-content heartbeat — analyze → append ONLY on change (+ bb CLI)
methods/test_*.cljc       loader + analytics + G1/G3/G5 + ledger/heartbeat invariants
kotoba/ontology.busshi.edn  EAVT schema + negative space (unrepresentable attrs)
kotoba/seed.edn           seed (26 commodities, all 5 classes); MIXED provenance —
                          25 :authoritative (15 USGS incl. REE+gallium + WNA uranium +
                          EIA/OPEC crude/gas/coal + FAO wheat/corn/soybean/coffee + USDA
                          sugar); 1 :representative (germanium — USGS data unverifiable)
data/ (gitignored)        generated observation ledger — never committed/hand-edited
manifest.edn              gates G1–G8 + non-goals N1–N5 + method/seed/ledger registry
```

## Datom convention

`[":db/add" entity ":busshi.<resource>/<aspect>" value]` (attrs are `:`-prefixed
strings, kotoba EAVT). Entities: `busshi-commodity:<id>`, `busshi-class:<class>`.
Every DERIVED datom carries `:busshi/derived true` + the input row's `:busshi/sourcing`
(`:representative` by default; `:authoritative` rows additionally carry `:busshi/source`,
the cited primary source folded via an operator-triggered G7 ingest). Provenance
describes the INPUT producer shares, not the computed score — the observation is always
`:derived`. Disclosed-fact namespaces: `:busshi.commodity/*`, `:busshi.producer/*`.
Derived: `:busshi.obs/*`, `:busshi.class/*`.

## Run

```bash
bb --classpath 20-actors 20-actors/busshi/methods/test_busshi_edn.cljc   # loader (3 tests)
bb --classpath 20-actors 20-actors/busshi/methods/test_analyze.cljc      # analytics + invariants (9 tests / 55 assert)
bb --classpath 20-actors 20-actors/busshi/methods/analyze.cljc           # print the resilience map
bb --classpath 20-actors 20-actors/busshi/methods/autorun.cljc           # heartbeat → append observations to ledger
# SoS score (ADR-2606212200): observation → measured ie-flow events → order-index/score:
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/busshi/methods/ie_flow.cljc          # flow-state
bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/busshi/methods/ie_flow.cljc --record # record to the SoS ledger (gitignored)
./20-actors/busshi/run_tests.sh                                          # 5 suites
```

## R0 → later waves

- R0 (ADR-2606161730): clj-native scaffold + `:representative` seed + analyze/datoms/coverage + tests.
- **Wave 2 (landed, ADR-2606171000)**: content-addressed observation-ledger persistence
  (`kotoba.cljc`) + deterministic, **idempotent-by-content** heartbeat (`autorun.cljc`) —
  observations appended to a tamper-evident commit-DAG (verify-chain) ONLY when they change
  (identical beat = no-op); resume-safe, no-server-key, gitignored. Mirrors ugachi (ADR-2606170900).
- **Wave 2+ first authoritative ingest (landed)**: operator-triggered G7 fold of real
  primary-source producer shares — USGS Mineral Commodity Summaries 2025 (2024e mine
  production, smelter for Al) for **14 metals**: cobalt / lithium / nickel / antimony /
  copper / aluminium / zinc / lead / tin / tungsten / gold / silver / platinum / palladium.
  Those rows carry `:sourcing :authoritative` + a cited `:busshi/source`; the
  analytics/datoms/report thread per-row provenance (mixed `:representative`/`:authoritative`).
  The seed header reserves live ingest for an operator step; this fold was operator-commanded.
  **+ uranium** folded from the **World Nuclear Association** 2024 table (tU): Kazakhstan 39 /
  Canada 24 / Namibia 12 / Australia 8 / Uzbekistan 7 (Canada 15→24 is the big move).
  **+ the whole energy class** folded: crude oil (EIA "Crude Oil incl. Lease Condensate",
  crude-only basis — US 16 / Saudi 12 / Russia 12 — resolving the earlier total-liquids
  mismatch), natural gas (OPEC ASB 2025 — US 26 / Russia 14 / Iran 7), coal (EIA 2024 — China
  50 / India 11 / Indonesia 9).
  **+ FAO ag-softs**: wheat (FAOSTAT 2022 — China 17 / India 13 / Russia 13), corn (2020 —
  US 31 / China 22 / Brazil 9), soybean (2022 — Brazil 35 / US 33 / Argentina 13), coffee
  (2023 green-coffee — Brazil 31 / Vietnam 18 / Indonesia 7). The final 4 `:representative`
  + **sugar** (USDA FAS centrifugal sugar, raw value, world 186.1 Mt — Brazil 23 / India 18 /
  EU 8 / China 7 / Thailand 5; the centrifugal-raw basis matches the #11 row, resolving the
  earlier sugarcane-crop mismatch).
  **+ REE + gallium** (correcting an earlier too-quick "no clean figure" call): REE from USGS
  MCS 2025 (2024e REO, world 390,000 t — China 69 / US 12 / Burma 8 / Australia 3; an
  authoritative AGGREGATE share, per-element depth still the rare-earth-coverage actor's job),
  gallium from the USGS narrative "China accounted for 99% of worldwide primary low-purity
  gallium production" (a clean citeable figure even without a per-country table). The **single**
  remaining `:representative` row is **germanium** — and that one is honest: USGS states its own
  germanium refinery-production estimates "were limited and difficult to verify," so there is no
  figure to cite. 25/26 authoritative; the 26th waits for a verifiable germanium source.
- **Wave 2+ MCS-2026 refresh (COMPLETE for metals)**: all **16 USGS metals** refreshed from MCS
  2025 (2024e) to MCS **2026** (2025e). Material §2(l) shifts captured:
  **nickel** Indonesia 59→**67** (:high→:critical), **antimony** China 60→**36** (USGS revised
  China's 2024 output 60k→40k amid the export ban; Russia 13→29; :critical→:moderate, route flips
  to resilience), **lithium** Australia 37→32 / China 17→21 (China overtakes Chile for #2),
  **cobalt** Congo 76→73 / Indonesia 10→14, **tungsten** China 83→79, **tin** Indonesia 17→**21**
  (Burma/Peru drop out of the named set, Brazil/Congo enter), **silver** Peru 12→14 (rises to #2),
  **REE** Australia 3→7, **palladium** Russia 40→44, **lead** China 44→42. Energy (EIA/OPEC) + ag
  (FAO/USDA) rows stay on their original vintage (different sources/release cadence). Germanium is
  still the only `:representative` row (USGS data unverifiable). 25/26 authoritative, now uniformly
  on the newest USGS vintage for the metals.
- Wave 2+: per-commodity depth (stocks/curve as facts, recycling-loop linkage to kanayama),
  Murakumo-narrated digest, fleet registration, lexicons.

## Related

- `/90-docs/adr/2606161730-busshi-commodity-materials-observatory-r0.md`
- `/90-docs/adr/2606161700-multigenerational-extraction-risk-gate-not-blanket-mining-ban.md` (the axis)
- `/20-actors/rare-earth-coverage/` (rare-metals specialist sibling)
- `/CHARTER-RIDER.md` §2(l) v3.2
