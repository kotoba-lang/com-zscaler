# kanae 鼎 — Maturity

**Stage: R0** (scaffold) — ADR-2605302300. Global government fiscal-flow VISUALIZATION
substrate ("danjo finds, kanae renders"). Assembles the state's OWN pre-published fiscal
records into kotoba EAVT, narrates Murakumo-only, renders aggregate-first kami-engine WASM
visualizations — renders NO verdict (鼎の軽重を問う: weigh openly, judge not).

| Dimension | State |
|---|---|
| Lexicons | ✅ 4 under `com.etzhayyim.kanae.*` (fundFlowEdge / flowNarrative / methodNote / visualizationManifest) |
| Cells | 🟡 path-reserved (assembly / narrative / render, R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–**G15**) + `nonGoals` (N1–N14) machine-readable |
| Tests | ✅ green — `methods/test_charter_gates.cljc` (**8**, added 2026-06-16: G1–G15 gate set + non-adjudication + Murakumo + aggregate-first + method/provenance + 1-SBT-1-vote + bounded vocab) **+** `methods/test_pipeline.py` (10, pipeline); `./run_tests.sh` aggregates both |
| Methods | 🟡 `assemble_flows.py` + `project_yoro.py` present; live ingest = R1 (danjo upstream) |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G15 (kanae carries 15 gates).
- **G1 non-adjudication** — `flowNarrative` requires `nonAdjudicatingNotice`; no
  `verdict`/`truthRating`/`score`/`ranking` field exists in any kanae lexicon.
- **G6 Murakumo-only narration** — `flowNarrative` requires `inferenceSubstrate` + `model` +
  `murakumoInferenceAttestation`.
- **aggregate-first + public-basis** — `visualizationManifest` requires `aggregateOnly` +
  `publiclyNamedBasis` (names no individual beyond the source record).
- **method + provenance transparency** — `fundFlowEdge` / `flowNarrative` require
  `methodNoteCid` + `sourceRecordCids`; `methodNote` requires `definition` + `inputs` + `version`.
- **1-SBT-1-vote governance** — `visualizationManifest` requires `oneSbtOneVoteChainCid` +
  `councilReviewCid`.
- **bounded vocab** — `fundFlowEdge.flowClass` (8 flow classes) + `visualizationManifest.renderType`
  (sankey / treemap / globe / timeline) are exact sets.

## R0 → R1 gate

danjo fiscal-flow upstream live (ADR-2605302245) + Council review; kanae reads danjo datoms
read-only, never writes back (engine/visualizer split per CLAUDE.md).

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `kanae.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
