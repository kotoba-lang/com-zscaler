# 剪定 sentei — Council as Pruner

The Council's **post-hoc pruning** organ (ADR-2606072000). etzhayyim's organism grows from its root
unstoppably; branches manifest on the append-only Datom log; **sentei prunes** the overgrown /
charter-violating ones *after* they manifest — to keep the organism beautiful (美しく保つ).

Not prior restraint. Actors **self-publish**; sentei cuts back afterward — transparently, signed,
voted, and **reversibly** (`regraft` heals a mistaken cut; the pruned branch's history is never
deleted — 非終末論).

- Charter + invariants: [`CLAUDE.md`](./CLAUDE.md)
- Pruning engine: [`methods/prune.py`](./methods/prune.py) · 15 tests (`./run_tests.sh`)
- Vocab: [`data/pruning-ontology.kotoba.edn`](./data/pruning-ontology.kotoba.edn) ·
  [`lex/com.etzhayyim.sentei.prune.json`](./lex/com.etzhayyim.sentei.prune.json)

DID `did:web:etzhayyim.com:actor:sentei`.
