# masago 真砂

Open materials-discovery (公開材料) Knowledge Graph mirror — the **materials-science sibling of
rasen 螺旋 (genetics) and inochi 命 (biosphere)**.

masago mirrors the **PUBLIC open-materials commons** — Meta FAIR Chemistry **Open Materials 2024
(OMat24, CC-BY-4.0)** plus the Materials Project / OQMD / NOMAD / AFLOW / JARVIS family — into the
kotoba Datom log: materials (formula + crystal structure), constituent elements, the computed
**properties** they carry (DFT / MLIP), and the downstream **application** classes they are
candidates for. It runs an edge-primary discovery-evidence pass routed to **RESEARCH** (a candidate
shortlist for human scientists), never to a make/buy decision.

It is a **RESEARCH map, never a weapons-design or synthesis-recipe tool** (G1): masago mirrors
computed properties + structures only — no synthesis route, precursor, or enrichment procedure is
representable, and weaponizable application classes are unrepresentable. Property values + provenance
are *disclosed* source facts, never masago verdicts (N3).

Clojure / kotoba-datomic native (not Python), babashka-runnable:

```bash
# from repo root
bb --classpath 20-actors -m masago.methods.analyze          # → out/{discovery,coverage}-report.md + materials-datoms.kotoba.edn
bb --classpath 20-actors -e "(require 'masago.methods.test-analyze)(require 'clojure.test)(clojure.test/run-tests 'masago.methods.test-analyze)"   # 13 green
```

`analyze.cljc` parses the open-materials seed, screens it for charter-compliance (G1 no
synthesis-routes / weaponizable classes; G4 open-license only — both raise `ex-info` on violation),
computes the edge-primary discovery / application-readiness / composition-breadth integrals, and
emits a discovery report, an honest coverage report, and the canonical kotoba Datom log (EAVT ground
`:add` + derived transient). Live ingest (Materials Project REST / OMat24 dumps) + IPFS publish are
the **R1 outward legs** (G7-gated); MLIP model execution is **R2+** on owned/donated compute only
(G6, no commercial GPU).

See `CLAUDE.md` for the constitutional gates and ontology, and
`00-contracts/schemas/open-materials-ontology.kotoba.edn` for the vocabulary. Status: 🟡 R0
design-only (analyzer + schema + seed); scope expansion is operator/Council-gated (G7).
