# tatara 鑪 — agent reference

> World manufacturing-plant + logistics geographic knowledge graph. Tier-B, R0 design-only.
> ADR-2606171800. Read the repo-root `CLAUDE.md` first; this file only adds actor-local rules.

## Identity

- **DID**: `did:web:etzhayyim.com:actor:tatara` — **registered** in
  `50-infra/etzhayyim-did-web/src/registry/infra-actors.ts` (`INFRA_ACTORS.tatara`, simeon
  libp2p host + pds service), so the apex Worker issues its did.json and it appears in `/search`.
- **Glyph**: 鑪 — the *tatara*, the traditional Japanese ironworks furnace (たたら製鉄): the
  literal heart of making. The kami of the forge, raised to planet scale.
- **Role**: the *geographic / facility-scale* face of the supply lineage. **kabuto 兜** holds the
  org→org supply-chain EDGES (who supplies whom); **uchiwake 内訳** decomposes the PRODUCT
  (GTIN→part→material); **tatara** places the producing FACILITIES on the map — where they SIT,
  at what scale, feeding which logistics corridor. `:plant/operator` joins `kabuto org.corp.*`.

## Why tatara exists

The roster had no actor that answers *"where on Earth does the world's manufacturing physically
sit, at what scale, and which shipping chokepoints does its export depend on?"* kabuto/uchiwake
model the abstract supply graph; watari/watatsuna model live craft + cables. tatara is the
missing geographic substrate that ties them together over **shared chokepoint keywords**.

## Hard rules (constitutional — do not weaken)

1. **Resilience, not interdiction (G2).** Every output is framed toward redundancy /
   diversification / reshoring. Never a "where to disrupt" / target-list framing. Mirrors
   kabuto + watatsuna **G2** and Charter Rider **§2(a)** (force-separation) + **§2(d)**.
2. **DISCLOSED AGGREGATE facility figures ONLY — the defining gate (G4).** `:plant/headcount-est`
   is the *disclosed aggregate* employment SIZE (the number an annual report or a city economic
   profile already prints), exactly like `:plant/floor-area-m2` or kabuto's `:company/market-cap`.
   There is **NO `:worker/*` and NO `:person/*` attribute** anywhere in the ontology — an
   individual worker, their pace, location, biometrics, or shift is **STRUCTURALLY
   UNREPRESENTABLE**. This is Charter Rider **§2(c)** on the *reciprocity axis* (no asymmetric
   labour surveillance; Wellbecoming §1.13) and mirrors sarutahiko / itonami / niyaku. Enforced
   **by construction + by tests** (`test_analyze` + `test_kotoba` both assert no worker/person
   namespace is representable). Aggregate disclosed size ≠ surveillance.
3. **Public disclosures + coarse geography (G1).** Ingest only public records (company
   disclosures, GLEIF, OSM, government industrial registers). Coordinates rounded to ~0.01°
   (city/campus) — never a precise interior or security-relevant coordinate.
4. **Sourcing honesty (G5).** Every node/edge carries `:*/sourcing` ∈
   `:authoritative | :representative | :synthesized`. Absence = "not yet ingested".
5. **kotoba-native (substrate boundary).** State = kotoba Datom log. No SQL / RisingWave / Lance.
6. **No confidential recipe / no process IP (G9).** Disclosed product CLASSES only (uchiwake
   N-boundary) — never a clone recipe.
7. **Murakumo-only (G6).** Any LLM narration routes through the Murakumo fleet.
8. **Outward-gated (G7).** Live disclosure / GLEIF / OSM ingest requires Council + operator.

## Vocabulary

`00-contracts/schemas/manufacturing-plant-ontology.kotoba.edn`:
- `:plant/*` — a manufacturing facility (id, name, operator→kabuto, country, **lat/lon**, sector,
  products, established, **`:headcount-est`** aggregate, **`:floor-area-m2`** scale,
  **`:capacity-value` + `:capacity-unit`** production capacity).
- `:hub/*` — a logistics node (seaport / airport / rail / DC) with lat/lon + throughput.
- `:flow/*` — a plant→hub export edge; **`:via`** = chokepoint keyword(s), **shared with watari
  `:lane/chokepoint` + watatsuna `:station/chokepoint`** — the join key that composes the three maps.
- derived (`:concentration/*`) — per-sector country HHI, single-source flag, chokepoint
  export-dependence. Computed by `analyze.cljc`, flagged `:concentration/derived`, **never
  re-ingested as fact**.

## Cells

- `cell:tatara.analyze` → `methods/analyze.cljc` (cljc, stdlib + clojure.edn). Pipeline:
  classify → per-sector country HHI + single-source → chokepoint export-dependence → country
  employment/floor rollup → per-sector capacity rollup → logistics modal/commodity split.
  Aggregate-first. Idempotent.
- `cell:tatara.kotoba` → `methods/kotoba.cljc`. Content-addressed EAVT commit-DAG persistence
  (graph-datoms + derived `:concentration/*`), append-only, verify-chain tamper-evident,
  resume-safe, no external I/O.
- `cell:tatara.autorun` → `methods/autorun.cljc`. The autonomous heartbeat — same shape as
  watari/watatsuna: each cycle observes the OFFLINE seed → classify → analyze → compose (the
  cross-actor 静↔動↔cable picture, sibling seeds read OFFLINE) → **persists a content-addressed
  tx** (graph + derived `:concentration/*` + `:composition/*`) to the append-only LOCAL kotoba
  log, linking the previous tx's CID. Deterministic / resume-safe. Live feed + live-node push
  stay G7-gated. G2/G4 hold by construction (only aggregate concentration/composition is
  representable; no per-worker datom, no target attr).
- `cell:tatara.crosscheck` → `methods/crosscheck.cljc` (uchiwake pattern). MEASURES
  `:plant/operator` ⇄ kabuto `:company/id` linkage (bridging tatara's short `org.corp.tsmc`
  to kabuto's country-qualified `org.corp.tw.tsmc` by normalized id+name token), emits a
  coverage % + a prioritized **ingest worklist** of unresolved operators + derived `:linkage/*`
  datoms (symmetric with analyze/compose — an unresolved operator persists `:linkage/resolved
  false` + empty kabuto, never a fabricated id, G5). Pure measurement; current: 24/29 = 83%.
- `cell:tatara.compose` → `methods/compose.cljc`. The cross-actor SSoT for the "one maritime
  resilience picture": fuses 静 tatara plant export-dependence · 動 watari live craft transit ·
  静-infra watatsuna cable load over the SAME chokepoint keyword → `resilience-composition.md`
  + derived `:composition/*` datoms. Routed to REDUNDANCY, never interdiction (G2 — only
  per-chokepoint counts representable). build_viz consumes this fn to render the world globe.
- `cell:tatara.maturity` → `methods/maturity.cljc`. Generates `MATURITY.md` — the actor's
  maturity SSoT, derived from the live graph (coverage / concentration / chokepoint / kabuto
  linkage) + an artifact-presence R0→R1 checklist + an honest deferred/gated section. Generated,
  never hand-edited, so it cannot drift.
- `cell:tatara.viz` → `viz/build_viz.cljc`. Emits three self-contained canvas globes —
  `viz/plant-globe.htm` (C, plants by sector), `viz/world-supply-globe.htm` (A, plants + live
  craft + watatsuna cable stations, 3-way composed per chokepoint), `../watari/viz/craft-globe.htm` (B, watari's first viz).
  Every coordinate DERIVED from a seed (regenerable; none hand-copied).

```bash
bb 20-actors/tatara/run_tests.clj                                            # 56 tests / 5,095 assertions
bb -cp 20-actors -e "(require 'tatara.methods.analyze)(tatara.methods.analyze/-main)"  # → out/concentration-report.md
bb -cp 20-actors -e "(require 'tatara.viz.build-viz)(tatara.viz.build-viz/-main)"      # → the three globes
bb -cp 20-actors -e "(require 'tatara.methods.autorun)(tatara.methods.autorun/-main)"  # autonomous heartbeat → LOCAL kotoba log
```

## Lexicons (kotoba-native)

`00-contracts/lexicons/com/etzhayyim/tatara/{registerPlant,registerHub,recordFlow,registerChokepoint}.json` — the
write surface onto the kotoba Datom log. `recordFlow.via` reuses the shared chokepoint enum
(watari `:lane/chokepoint` + watatsuna `:station/chokepoint`). The G4 boundary lives here too:
no lexicon carries a per-worker / per-person field (asserted by `test_lexicons`, which also pins
lexicon↔manifest↔ontology↔seed parity so the four declarations can't drift).

## Pairing (静 manufacturing ↔ 動 craft ↔ cable) — one resilience picture

Because `:flow/via`, watari `:lane/chokepoint`, and watatsuna `:station/chokepoint` are the SAME
keywords (`:malacca :luzon-strait :suez-red-sea :gibraltar :hormuz …`), a chokepoint's
**manufacturing export-dependence** (tatara), **live vessel transit** (watari), and
**submarine-cable load** (watatsuna) compose into one maritime resilience map — all routed to
redundancy + safer routing + faster repair, **never to interdiction** (G2). The integrated
`world-supply-globe.htm` renders all THREE layers together (plants · live craft · cable stations),
with a per-chokepoint composition bar (静 plants · 動 craft · 静-infra cable).

## Distinct from the factory-building / factory-operating actors (N3)

`sarutahiko` (truck factory sim), `funadaiku` (shipyard), `giemon-factory` (4D-BIM plant build),
`itonami` (live OEE telemetry) BUILD or OPERATE specific factories. tatara only OBSERVES the
world's facilities at aggregate geographic scale. Observe ≠ operate — tatara runs no line and,
by its load-bearing G4 gate, surfaces no per-worker telemetry.
