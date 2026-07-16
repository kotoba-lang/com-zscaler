# rasen 螺旋

Public-genetics (公開遺伝) Knowledge Graph mirror — the **gene-scale sibling of inochi 命**.

rasen hosts and analyses **PUBLIC reference genetics only** — genes, sequence variants, the
conditions they associate with, the pathways they sit in, and **population-aggregate** allele
frequencies — across humans, animals, plants and microbes, woven into the kotoba Datom log.

It is a **CARE / RESEARCH map, never an individual-genotype registry or discrimination tool**
(G1). No individual genotypes, no personal sequence, no precise coordinates: the unit is
always a gene / variant / population aggregate, and clinical-significance is a *disclosed*
fact (ClinVar/OMIM style), never a rasen verdict (N3).

```bash
cd 20-actors/rasen
bb -cp 20-actors -m rasen.methods.analyze          # → out/care-report.md          (gene care-priority)
bb -cp 20-actors -m rasen.methods.datom-emit       # → out/genome-datoms.kotoba.edn (EAVT canonical state)
bb -cp 20-actors -m rasen.methods.coverage-report  # → out/coverage-report.md       (honest coverage + gaps)
bb -cp 20-actors -m rasen.methods.ingest           # OUTWARD (G7): public APIs → kotoba EDN/Datom → IPFS CID (+pin)
bb -cp 20-actors -m rasen.methods.publish          # OUTWARD (G7): pin + IPNS-publish + snapshot to 80-data/genome
python3 wasm/app.cljs analyze         # the WASM component's export body, dev mode
bash run_tests.sh  # cljc tests, green
```

`ingest.py` pulls a bounded, **public + aggregate-only** slice (MyGene.info + MyVariant.info +
Reactome → Ensembl/NCBI + ClinVar + gnomAD super-population frequencies + GO & Reactome
pathways), normalises it into the genome-ontology kotoba graph, and content-addresses it to a
kotoba IPFS CIDv1 that matches `ipfs add --cid-version=1 --raw-leaves` (verifiable without a
daemon). `publish.py` pins + IPNS-publishes it and snapshots the durable record into
`80-data/genome/`. `wasm/` is the build-ready cherry+ComponentizeJS (ADR-2606261200) component. No individual data, ever.

See `CLAUDE.md` for the constitutional gates and ontology, and
`00-contracts/schemas/genome-ontology.kotoba.edn` for the vocabulary. Status: 🟢 R1+ — public
ingest (3 sources) + publish + WASM-ready; scope expansion is operator/Council-gated (G7).
