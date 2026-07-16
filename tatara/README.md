# tatara 鑪 — world manufacturing-plant + logistics geographic KG

> Tier-B · R0 design-only · ADR-2606171800 · `did:web:etzhayyim.com:actor:tatara`

**Where on Earth the world's manufacturing physically sits** — at what scale (employment / floor
area / production capacity), feeding which logistics corridor — on the kotoba Datom log. The
geographic / facility-scale layer of the supply lineage: **kabuto 兜** (who supplies whom) +
**uchiwake 内訳** (the product BOM) + **tatara** (where the facilities are) + **watari 渡り** (live
craft) + **watatsuna 綿津綱** (cables), all composing over one shared chokepoint vocabulary.

Mirror-lineage sibling of kabuto / tsumugi / inochi: edge-primary geographic **concentration**
(per-sector country HHI + chokepoint export-dependence) routed to **redundancy / reshoring** — a
resilience map, **never a target-list** (G2).

## The worker-data boundary (read this)

`:plant/headcount-est` is the **disclosed aggregate** facility-employment figure — a SIZE, like
floor area or market-cap. There is **no `:worker/*` / `:person/*` attribute** anywhere: an
individual worker, their pace, location, or shift is **structurally unrepresentable** (G4 —
Charter Rider §2(c) reciprocity axis; Wellbecoming §1.13). Enforced by construction and by tests.

## Run

```bash
bb 20-actors/tatara/run_tests.clj
# Testing tatara.methods.test-analyze
# Testing tatara.methods.test-kotoba
# … (10 suites)
# Ran 56 tests containing 5095 assertions.  0 failures, 0 errors.
# ── tatara: ALL suites green ──

# kabuto-linkage crosscheck → out/kabuto-crosscheck.md (24/29 = 83% linkage)
bb -cp 20-actors -e "(require 'tatara.methods.crosscheck)(tatara.methods.crosscheck/-main)"

# concentration report → out/concentration-report.md
bb -cp 20-actors -e "(require 'tatara.methods.analyze)(tatara.methods.analyze/-main)"

# autonomous heartbeat → LOCAL append-only kotoba Datom log (commit-DAG)
bb -cp 20-actors -e "(require 'tatara.methods.autorun)(tatara.methods.autorun/-main)"

# maturity scorecard → MATURITY.md (R0→R1 checklist + live coverage/linkage)
bb -cp 20-actors -e "(require 'tatara.methods.maturity)(tatara.methods.maturity/-main)"

# the three globes (open the .htm in a browser)
bb -cp 20-actors -e "(require 'tatara.viz.build-viz)(tatara.viz.build-viz/-main)"
#   viz/plant-globe.htm          — (C) world plants by sector + export flows
#   viz/world-supply-globe.htm   — (A) plants + live craft + cable stations, 3-way composed per chokepoint
#   ../watari/viz/craft-globe.htm — (B) watari's first visualization
```

## What's in the seed (R0, `:representative`)

33 real public plants across 9 sectors / 16 countries — semiconductor (TSMC ×2, Samsung, Intel, SK
hynix), automotive (Hyundai, Toyota, VW, Tesla, Ford), battery (CATL, LG, Tesla, Northvolt), steel
(POSCO, Baowu, Nippon Steel, Tata Steel), chemicals (BASF, SABIC), electronics (Foxconn), aerospace
(Boeing, Airbus, Embraer), shipbuilding (HD Hyundai), pharma (Pfizer, Serum Institute) — spanning 16 countries incl. MX/VN/ZA/ID/BR (Nissan, Samsung VN, Sasol, Gerdau, Tsingshan IMIP) — + 6
logistics hubs + 33 export flows + 7 first-class chokepoint geographic nodes (the shared anchor for watari + watatsuna).

Top chokepoint export-dependence in the seed: **malacca 12 plants · luzon-strait 8 · suez-red-sea 5 ·
gibraltar 5 · panama 3 · hormuz 1 · taiwan-strait 1** — these compose with watari (live vessel
transit) and watatsuna (submarine-cable load) over the same keywords.

## Files

| path | what |
|---|---|
| `00-contracts/schemas/manufacturing-plant-ontology.kotoba.edn` | ontology (`:plant/* :hub/* :flow/* :concentration/*`) |
| `data/seed-plant-graph.kotoba.edn` | bounded `:representative` seed |
| `methods/analyze.cljc` | concentration / HHI / chokepoint / capacity engine |
| `methods/kotoba.cljc` | content-addressed EAVT commit-DAG persistence |
| `methods/autorun.cljc` | autonomous heartbeat → content-addressed commit-DAG (resume-safe) |
| `methods/compose.cljc` → `resilience-composition.md` | cross-actor SSoT: 静 plants · 動 craft · 静-infra cable per chokepoint |
| `methods/crosscheck.cljc` | measures :plant/operator ⇄ kabuto :company/id linkage (84%) + ingest worklist |
| `methods/maturity.cljc` → `MATURITY.md` | generated maturity scorecard (R0→R1 checklist, can't drift) |
| `00-contracts/lexicons/com/etzhayyim/tatara/*.json` | write surface (registerPlant/registerHub/recordFlow/registerChokepoint) |
| `methods/test_{analyze,kotoba,autorun,lexicons,crosscheck,maturity,seed-integrity,viz,compose,robustness}.cljc` | 56 tests / 5,095 assertions |
| `viz/build_viz.cljc` | globe generator (derives all coords from the seeds) |
| `viz/plant-globe.htm` · `viz/world-supply-globe.htm` | the (C) and (A) globes |
| `manifest.jsonld` · `CLAUDE.md` | actor manifest + agent rules |

Live ingest (company disclosures / GLEIF / OSM facility geometry) is G7 Council+operator-gated.
