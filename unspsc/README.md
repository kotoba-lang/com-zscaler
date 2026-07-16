# unspsc — UNSPSC commodity actor

The concrete **18,342 UNSPSC commodity actors** = the generic `kotodama` organism
runtime + a per-code data table + a segment-capability library. One framework +
data → every UNSPSC code is a real, commodity-specific, deployable actor (no hollow
stubs). Persisted as-of on the kotoba Datom log.

Relocated here from `etzhayyim/kototama` per the ADR superseding 2606131645, which
splits the **generic organism runtime** (→ `com-junkawasaki/kotodama`) from this
**concrete UNSPSC actor** (an actor like any other under `20-actors/`).

## Layers

```
kotoba (Rust substrate)  ◂ kotoba-db XRPC / checkpointer (langchain-clj / langgraph-clj)
kotodama (generic organism runtime: life + organism graph + ReAct)
unspsc (THIS: capability + taxonomy + fleet + 18,342-code data, injected into kotodama)
```

## Namespaces

- `unspsc.capability` — segment-capability library (33/36 segments bespoke; charter-clean
  33/33; 15/20/46 excluded by design) + universal/risk-tag checks. The injected `:validate`.
- `unspsc.taxonomy` — the per-code data table (`resources/unspsc-taxonomy.edn`, 18,342 codes).
- `unspsc.organism` — wires `kotodama.organism/actor` with `cap/run` + the UNSPSC result shape
  (`{:code :title :segment :did :ok ...}`, DID `did:web:etzhayyim.com:actor:c<code>`).
- `unspsc.react` — the ReAct loop (inspect_requirements / validate_line tools) on `kotodama.react`.
- `unspsc.fleet` — full-fleet sweep; `:kotoba` store persists checkpoints to a kotoba node
  (operator Bearer JWT, ADR-2605231525).
- `unspsc.build-taxonomy` / `unspsc.enrich` — build the data table from
  `00-contracts/actor-registry/unispsc.json` ⨝ `80-data/unspsc_v26_ucalypt.jsonl`.

## Use

```bash
clojure -X:test                 # 39 tests / 212 assertions (kotodama as a git dep)
clojure -X:test -A:dev          # … against the local kotodama checkout (main checkout only)
clojure -M:fleet 200            # subset fleet sweep
```

Inference is Murakumo-only at runtime (ADR-2605215000). Apache-2.0 + Charter Rider.
