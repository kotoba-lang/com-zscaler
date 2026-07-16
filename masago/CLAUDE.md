# masago 真砂 — open materials-discovery (公開材料) Knowledge Graph mirror

**ADR**: 2606151027 · **depends**: 2606101000 (rasen 螺旋 / genome-ontology — the science-data
ingest/analyze/datom pattern) · 2606051200 (hotaru 蛍 — open-IP-only / no-fabrication stance) ·
2606073000 (inochi 命 / map-not-target lineage) · 2605312345 (Datom = canonical state) ·
2605262400 (public-data IPFS/DataLad ingestion) · 2605215000 (Murakumo-only / no-commercial-GPU) ·
2605231525 (no-server-key). **Status**: 🟡 R0 design-only (Clojure analyzer + schema + seed).

masago ("真砂" = 浜の真砂, the countless fine grains — the metaphor for the vast combinatorial
materials space mirrored grain-by-grain) is the **materials-science sibling** of **rasen 螺旋**
(public genetics) and **inochi 命** (the biosphere). Where rasen mirrors the public reference
text of life and inochi the living world, masago mirrors the **PUBLIC open-materials commons** —
Meta FAIR Chemistry **Open Materials 2024 (OMat24, CC-BY-4.0)** plus the Materials Project / OQMD /
NOMAD / AFLOW / JARVIS family — into the kotoba Datom log. It weaves materials (formula + crystal
structure), their constituent elements, the computed **properties** they carry (DFT / MLIP), and
the downstream **application** classes they are candidates for, and runs an **edge-primary
discovery-evidence** pass (a material's discovery-priority = the integral of disclosed
computed-evidence accumulated on its incident 縁) **routed to RESEARCH**, never to fabrication.

It answers the MIT-Tech-Review question *"the race to find new materials with AI needs more data,
and Meta is giving massive amounts away for free — is it integrated?"* — masago is that integration.

## Language / runtime (CRITICAL)

masago is **Clojure / kotoba-datomic native**, not Python — matching the Tier-B analyzer migration
(hotaru/mitooshi/nusa Python→Clojure). Methods are pure `.cljc`, run on **babashka**, classpath
root `20-actors`, namespace `masago.methods.*`. EDN `:…` keywords are kept as **strings** through
the pipeline (shared house style with the nusa/hotaru ports). File I/O only at the edges.

## Hard gates (constitutional — read before any change)

- **G1 — RESEARCH map, NEVER a weapons-design or synthesis-recipe tool.** The defining inversion
  (mirrors hotaru's no-fabrication, inochi's "never a target-list"). masago mirrors computed
  **PROPERTIES + crystal STRUCTURES only**. A synthesis-route / precursor / enrichment / processing
  field is **not representable** (`analyze.cljc/screen` raises `ex-info` — the Clojure ValueError
  analogue), and weaponizable `:application/class` values (`:weapon :energetic :explosive
  :propellant :warhead :fissile :enrichment`) are unrepresentable. Fabrication + force are
  structurally excluded (Charter §1.12).
- **G2 — edge-primary (N1).** Discovery evidence lives ONLY on edges (`:en/grasping-load` weighted
  by disclosed `:en/confidence`). A material's discovery-priority = the **integral of its incident
  `:has-property` + `:candidate-for` 縁**, computed **on read** — never a stored `:material/score`.
  The raw computed VALUE rides the edge as `:en/value` (DISCLOSED, never re-judged).
- **G3 — non-adjudicating (N3).** Property values + provenance (`:dft` / `:mlip-predicted` /
  `:experimental`) are **DISCLOSED source facts**, never masago verdicts. The output is a candidate
  **shortlist surfaced to human scientists**, routed to RESEARCH — never a make/buy/trade decision.
  There is no `:verdict` route; masago never trades.
- **G4 — open-license only.** Every `:dataset-source` carries an OPEN license (CC-BY / open); a
  non-open license is **refused** (`screen` raises). No proprietary / vendor formulations.
- **G5 — sourcing honesty.** Every record `:mat/sourcing :authoritative | :representative`. Coverage
  of the full ~10⁸ open-materials commons is **~0 by design**; `render-coverage` names thin/missing
  buckets (no fabricated coverage).
- **G6 — Murakumo-only / no-commercial-GPU.** Narration via LiteLLM 127.0.0.1:4000 (ADR-2605215000).
  **MLIP/ML model EXECUTION** (running the OMat24 potentials) is GPU compute → **owned/donated
  compute only, never RunPod / cloud-GPU rental** (Rider §2(i)). **R0/R1 mirror DISCLOSED source
  values — no model execution at all**; execution is R2+ Council-gated.
- **G7 — outward-gated / no-server-key.** Live ingest (Materials Project REST / OMat24 dumps) +
  IPFS pin + publish require Council + operator DID; the analyzer loop does **no network I/O**;
  `serverHeldKey=false`. R0 = offline analyze + schema + seed only.
- **G8 — content-addressed canonical state / no git-lfs.** `render-datoms` is the canonical kotoba
  Datom log (EAVT ground `:add` + derived transient; ADR-2605312345). Large dumps via DataLad →
  IPFS (ADR-2605262400, `80-data/open-materials`), never git-lfs.

## Layout

```
20-actors/masago/
├── CLAUDE.md                              # this file
├── README.md                             # short orientation
├── manifest.edn                          # actor manifest (Clojure cells, 8 gates, 6 non-goals)
├── data/
│   └── seed-open-materials-graph.kotoba.edn   # hand-curated PUBLIC open-materials seed (mp-* ids)
├── methods/                              # pure Clojure (.cljc) — babashka-runnable
│   ├── analyze.cljc                      # EDN reader + classify + screen (G1/G4) + analyze
│   │                                     #   + render-report (discovery) + render-coverage
│   │                                     #   + render-datoms (canonical EAVT) + -main
│   └── test_analyze.cljc                 # 13 deftests (clojure.test), network-free
└── out/                                  # GENERATED — do not hand-edit / do not commit
    ├── discovery-report.md · coverage-report.md · materials-datoms.kotoba.edn
```

`methods/ingest.cljc` + `methods/publish.cljc` (live Materials Project REST / OMat24 ingest → CID →
IPFS/IPNS + `80-data/open-materials` snapshot) are the **R1 outward legs** (G7-gated); not in R0.

## Run

```bash
cd <repo-root>
# analyze (writes out/discovery-report.md, out/coverage-report.md, out/materials-datoms.kotoba.edn)
bb --classpath 20-actors -m masago.methods.analyze

# tests (13 green, network-free)
bb --classpath 20-actors -e "(require 'masago.methods.test-analyze)(require 'clojure.test)(clojure.test/run-tests 'masago.methods.test-analyze)"
```

When the Tier-B Clojure infra (`bb.edn`) merges to `origin/main`, register
`masago.methods.test-analyze` in the `test:pywasm` task alongside hotaru/nusa/mitooshi.

## Ontology (open-materials-ontology, `00-contracts/schemas/`)

- **nodes** `:mat/kind` ∈ `{:material :element :property :application :dataset-source}`, all keyed by
  `:mat/id`, with material (`:material/formula :material/spacegroup :material/crystal-system
  :material/source-id`), element (`:element/symbol :element/z`), property (`:property/kind
  :property/unit`), application (`:application/class`) and dataset-source (`:source/license
  :source/doi :source/url`) facets.
- **edges** `:en/kind` ∈ `{:composed-of :has-property :candidate-for :derived-from :similar-to}`
  carrying `:en/grasping-load` ∈ [0,1] (where evidence lives), and on `:has-property` a DISCLOSED
  `:en/value` + `:en/confidence` ∈ `{:experimental :dft :mlip-predicted :mlip-screened :estimated}`.
- **derived** `:bond/discovery-priority` · `:bond/application-readiness` · `:bond/composition-breadth`
  — transient, computed on read, never persisted (N1/G2).
- **confidence/weight** disclosed scale: `:experimental 1.0 :dft 0.8 :mlip-predicted 0.6
  :mlip-screened 0.5 :estimated 0.3`.

The invariant lives in **three places** (machine-checkable): schema `:db/allowed`/enums =
lexicon `enum`/`const` (`00-contracts/lexicons/com/etzhayyim/masago/`) = `analyze.cljc` constants
(`confidence-weight` / `forbidden-node-attrs` / `forbidden-app-classes`).

## Cross-links

`:mat/links` can name a node in a sibling graph — e.g. an **iwakura** silicon substrate, a **hikari**
battery cell, or a **hotaru** III-V substrate — bridging the materials scale back to the device/energy
actors that consume it. masago observes the public open-materials commons; it does not adjudicate,
synthesize, fabricate, or trade.

## Do not

- Do not add a synthesis-route / precursor / enrichment / processing field, or a weaponizable
  `:application/class` — G1, structurally unrepresentable.
- Do not store a per-material score on a node — edge-primary only (G2).
- Do not run MLIP/ML models on commercial GPU rental — owned/donated compute only, R2+ (G6).
- Do not add live network I/O to the analyzer loop — ingest/publish are separate R1 G7-gated cells.
- Do not introduce a non-open dataset-source license (G4).
- Do not rewrite the methods in Python — masago is Clojure/kotoba-datomic native.
