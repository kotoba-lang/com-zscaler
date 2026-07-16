# rasen 螺旋 — public-genetics (公開遺伝) Knowledge Graph mirror

**ADR**: 2606101000 · **depends**: 2606073000 (inochi 命 / biosphere-ontology) + 2606073800
(tsugite / peoples-ontology) · 2606011000 (§D7 産霊の網 / engi-organism) + 2606011500
(spirit-ontology) · 2605312345 (Datom = canonical state) · 2605215000 (Murakumo-only) ·
2605181100 (himotoki PII-envelope discipline). **Status**: 🟡 R0 design-only.

rasen ("螺旋" = the double helix) is the **molecular-scale sibling** of **inochi 命** (the
living-world / biosphere mirror). Where inochi weaves species / ecosystems / biomes, rasen
weaves the shared, **PUBLIC reference text of life** — genes, sequence variants, the
phenotypes/conditions they associate with, the pathways they participate in, and
**population-AGGREGATE** allele frequencies — across **humans, animals, plants and microbes**
into the kotoba Datom log. It runs an **edge-primary clinical/functional evidence** pass
(integrated burden over a gene = the disclosed clinical weight accumulated on its incident
縁) **routed to CARE & RESEARCH** (release), never to discrimination.

It answers the question "is there an actor that hosts public genetic data and analyses it?"
— inochi stops at the species scale; rasen is its gene-scale counterpart.

## Hard gates (constitutional — read before any change)

- **G1 — CARE/RESEARCH map, NEVER an individual-genotype registry.** This is the defining
  inversion (mirrors inochi's "restoration, never a target-list"). rasen carries **no
  individual genotypes, no identifiable personal/sample/family sequence**. The unit is
  always a **gene / variant / population-aggregate**. Allele frequencies are
  super-population aggregates (gnomAD scale) that **cannot reconstruct a person**.
  Chromosomal location is **coarse (cytoband)** — never a precise, re-identifiable
  coordinate. The routing is care, research and equity — never insurance / employment /
  eugenic / forensic-identification targeting.
- **G2 — edge-primary (N1).** Evidence/burden lives ONLY on edges (`:en/grasping-load`
  weighted by disclosed `:en/clinsig`). A gene's care-priority = the **integral of its
  incident variant-association (via `:located-in`) + gene-linkage 縁**, computed **on read**
  — never a stored per-gene score. There is no `:genome/score-of-gene`.
- **G3 — non-adjudicating (N3).** Clinical-significance categories (ClinVar / OMIM /
  GWAS-Catalog style) are **DISCLOSED facts**, never rasen verdicts. rasen datafies the
  variant–gene–phenotype structure; it never diagnoses, never rates a person, never asserts
  the worth of any genotype.
- **G4 — public venue.** Open-source + on-chain + 1 SBT = 1 vote. **PUBLIC reference data
  only.** Never a private/covert registry.
- **G5 — sourcing honesty.** Every record carries `:genome/sourcing :authoritative |
  :representative`. The committed seed is a **bounded** clinically-actionable backbone;
  coverage of all genes/variants is ~0 by design (`coverage_report.py` makes this
  measurable and names the gaps).
- **G6 — Murakumo-only narration.** Any LLM narration routes through Murakumo (ADR-2605215000).
- **G7 — outward-gated PUBLIC ingest.** `methods/ingest.py` pulls a **bounded, public,
  aggregate-only** slice (MyGene.info + MyVariant.info → Ensembl/NCBI + ClinVar + gnomAD
  super-population AGGREGATE) and content-addresses it to a kotoba IPFS CID. The committed
  bounded slice runs freely; **expanding scope** (more ids, more sources, anything beyond
  public reference data) requires Council + operator DID. The ingest cell runs operator /
  mesh-side (it needs the network), never in-browser WASM. It never requests individual-level
  endpoints and reads only aggregate `af_*` super-population fields (G1 by construction).
- **G8 — no git-lfs.** Large reference assets (FASTA/VCF) via DataLad → IPFS (`80-data/genome`).

## Layout

```
20-actors/rasen/
├── CLAUDE.md                              # this file
├── README.md                             # short orientation
├── manifest.jsonld                        # actor manifest (3 cells, 8 gates)
├── data/
│   ├── seed-genome-graph.kotoba.edn       # hand-curated PUBLIC reference-genetics seed
│   └── ingest-sources.edn                 # bounded public source + id allowlist (ingest input)
├── methods/                               # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                         # edge-primary clinical/functional evidence analyzer
│   ├── datom_emit.py                      # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── coverage_report.py                 # honest coverage + gap map (G5)
│   ├── ingest.py                          # OUTWARD (G7): public APIs (MyGene/MyVariant/GO/Reactome) → EDN/Datom → IPFS CID
│   ├── publish.py                         # OUTWARD (G7): pin + IPNS-publish + snapshot to 80-data/genome
│   └── cid.py                             # kotoba IPFS CIDv1 (raw/sha2-256) — ipfs-parity, no daemon
├── tests/                                 # 23 tests, pure stdlib (network-free)
│   ├── test_analyze.py · test_coverage.py · test_ingest.py · test_wasm.py
│   └── fixtures/{myvariant_rs334, mygene_brca1_go, reactome_brca1}.json
├── wasm/                                  # kotoba pywasm component (cherry+ComponentizeJS (ADR-2606261200))
│   ├── README.md · wit/world.wit          # WIT world (analyze/datoms/coverage exports)
│   ├── app.py                             # export bodies (runnable in dev; embeds seed for WASM)
│   └── build.sh                           # embed seed → componentize → CID → DID service descriptor
└── out/                                   # GENERATED — do not hand-edit
    ├── care-report.md · genome-datoms.kotoba.edn · coverage-report.md     # from the seed
    ├── ingested-genome-graph.kotoba.edn · ingested-genome-datoms.kotoba.edn # from live ingest
    ├── ingested.cid                       # kotoba IPFS CIDv1 of the ingested graph
    └── ingest-provenance.json             # sources, licenses, counts, CID, pin result
```

## Run

```bash
cd 20-actors/rasen
bb -cp 20-actors -m rasen.methods.analyze          # → out/care-report.md
bb -cp 20-actors -m rasen.methods.datom-emit       # → out/genome-datoms.kotoba.edn (EAVT)
bb -cp 20-actors -m rasen.methods.coverage-report  # → out/coverage-report.md

# OUTWARD (G7) — pull bounded PUBLIC reference genetics → kotoba EDN/Datom → IPFS CID
bb -cp 20-actors -m rasen.methods.ingest           # live fetch (MyGene.info + MyVariant.info) + ipfs pin
bb -cp 20-actors -m rasen.methods.ingest --offline --no-pin   # re-content-address an existing ingested graph
bb -cp 20-actors -m rasen.methods.cid out/ingested-genome-graph.kotoba.edn   # print the kotoba IPFS CID

bash run_tests.sh  # cljc tests, green
```

The ingest is **PUBLIC + aggregate only** (G1): gene reference models (Ensembl + coarse
cytoband), **public GO pathway membership** (Gene Ontology / EBI-GOA via MyGene `go.BP`),
public variant ids, DISCLOSED ClinVar significance, and gnomAD **super-population AGGREGATE**
allele frequencies — never sample/cohort/individual data. The artifact is content-addressed
to a kotoba IPFS CIDv1 (raw/sha2-256) that is **byte-identical to `ipfs add --cid-version=1
--raw-leaves`** and verifiable without a daemon (`methods/cid.py`).

Pathways come from **two PUBLIC sources**, both bounded + honest (G5), both feeding
`:participates-in` (gene→pathway) edges that enrich the **pleiotropy / cascade** readout:
- **GO** (Gene Ontology / EBI-GOA via MyGene `go.BP`): per gene dedupe terms, keep best
  evidence weight (`GO_EVIDENCE_WEIGHT`: experimental > author-stated > phylogenetic >
  computational — the GO annotation is the DISCLOSED fact, N3), cap `:ingest/go :max-per-gene`.
- **Reactome** (curated pathways via UniProt→Reactome mapping; `:ingest/reactome`): per gene
  dedupe by `stId`, prefer top-level (lowest `maxDepth`), cap `:max-per-gene`; curated
  membership carries a fixed representative weight (DISCLOSED Reactome curation, N3).
  Operator-authorized scope expansion (G7) — `:enabled true` + the PR merge is the gate record.

Latest live run (GO + Reactome): 20 genes + 12 variants → **268 nodes / 305 縁** (117 GO +
85 Reactome `:participates-in` edges), 0 errors.

## Publish (G7, operator/mesh-side)

`methods/publish.py` takes the ingested artifacts and (1) pins them + asserts the daemon CID
equals `cid.py`'s, (2) publishes the graph CID under the node's IPNS `self` key (a stable
`/ipns/<id>` that resolves to the latest graph), and (3) snapshots graph + datoms + provenance
+ a `PUBLISH.md`/`publish-manifest.json` into the git-tracked data layer **`80-data/genome/`**
(bounded EDN, no git-lfs, G8). PUBLIC reference only — publishing is safe by construction (G1).
Pinata / other public pinning services are an operator API-key add-on (out of scope); IPNS + a
trustless gateway already make the content publicly fetchable + re-verifiable.

## WASM component (build-ready; ADR-2606014500/2606014600)

`wasm/` holds the kotoba pywasm component: `wit/world.wit` (the `rasen-actor` world exporting
`analyze`/`datoms`/`coverage`), `app.py` (the export bodies — runnable now in dev mode:
`python3 wasm/app.cljs analyze`, and embedding the seed for the no-FS WASM runtime), and
`build.sh` (embed seed → `cherry+ComponentizeJS (ADR-2606261200)` → CID → DID-doc `EtzhayyimWasmComponent` service
descriptor). The build itself is the operator step (cherry+ComponentizeJS (ADR-2606261200) toolchain); the export
logic is CI-covered by `tests/test_wasm.py`. G1 holds in WASM — the component embeds only the
bounded PUBLIC seed, so it cannot leak what it does not contain.

## Ontology (genome-ontology, `00-contracts/schemas/`)

- **nodes** `:genome/kind` ∈ `{:gene, :variant, :phenotype, :population, :pathway}` with gene
  (`:gene/symbol :gene/cytoband :gene/ensembl :gene/taxon`), variant (`:variant/rsid
  :variant/category`), phenotype (`:phenotype/code :phenotype/inheritance`), population
  (`:population/code` — super-pop aggregate) and pathway (`:pathway/source :pathway/acc`)
  facets.
- **edges** `:en/kind` ∈ `{:located-in, :associated-with, :linked-to, :participates-in,
  :allele-frequency, :interacts-with}` carrying `:en/grasping-load` ∈ [0,1] (where evidence
  lives) and, on clinical edges, a DISCLOSED `:en/clinsig`.
- **derived** `:bond/care-priority` · `:bond/locus-burden` · `:bond/pleiotropy` — transient,
  computed on read, never persisted (N1/G2).
- **clinsig/weight** disclosed scale: `:pathogenic 1.0 :likely-pathogenic 0.8 :risk-factor 0.5
  :drug-response 0.4 :uncertain/:vus 0.3 :likely-benign 0.1 :benign 0.05 :protective 0.1`.

## Cross-links

`:genome/links` can name a node in a sibling graph — e.g. an **inochi** species
(`bio.species.*`, as the elephant TP53 expansion does) or a **mitsuho** crop — bridging the
gene scale back up to the organism/biosphere scale. PII-bearing live ingest (if ever
Council-approved beyond public reference data) routes through **himotoki** envelopes
(ADR-2605181100). rasen observes public reference genetics; it does not adjudicate, diagnose,
or target.
