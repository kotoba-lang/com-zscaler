# himawari 向日葵 — kotoba-WASM atproto deploy

> **⚠ Historical record, WASM-build section superseded (2026-07-14).** This doc's
> "Build (verified working — 2026-06-02)" section describes the old
> `componentize-py` Python build path (`agent.py`, since deleted — all 7
> `himawari/cells/*` are now `.cljc`, per-cell `cell.py`/`state_machine.py`
> pruned). The **current** WASM build is `deploy/agent.cljc` →
> `bb 20-actors/himawari/deploy/build_wasm.clj` (kotoba-clj, ADR-2606222100).
> Kept below as an accurate record of the prior verified build, not as current
> instructions. The record-ingest section (`ingest_records.py`) is unaffected —
> that's a separate PDS write-path script, still current.

Deploy scaffold for the **himawari solar-PV manufacturing actor** (ADR-2606021200)
as a **kotoba-node WASM LangGraph component** that runs in-WASM on a live kotoba
node (`:8077`) and writes its lexicon records into the canonical kotoba Datom log
via the kotoba-server **PDS XRPC write path** (ADR-2606015000).

This mirrors the proven `kotoba-langgraph-aria` (ADR-2605301625) and `okaimono`
(ADR-2606012100) deploy patterns. himawari is **solar-grade c-Si PV module
manufacturing** — NOT the logic/compute iwakura/fuigo/tsukuru silicon track (N1).

## What this directory contains

| File | Purpose |
|---|---|
| `agent.py` | WASM build entrypoint — a `kotoba_langgraph` StateGraph that **composes** the seven `himawari/cells/*` manufacturing cells into one ordered chain, plus an advisory `narrate` node routed through the Murakumo fleet (G5). Exposes `WitWorld.run`. |
| `requirements.txt` | langgraph build deps (no external LLM client — Murakumo-only, G5). |
| `schema.edn` | kotoba EAVT schema projecting the 7 `com.etzhayyim.himawari.*` lexicon records → `:himawari.*/*` Datom attributes. |
| `seed.edn` | one representative end-to-end manufacturing chain (lot → wafer → cell → module → loading → outbound + Council review), `:representative`. |
| `ingest_records.py` | PDS write path — parses `seed.edn`, projects each record to a `kg.ingest` entity, gates the write on an operator session PoP (`com.etzhayyim.pds.session.verify`), writes via `com.etzhayyim.apps.kotobase.kg.ingest`. |
| `deploy.sh` | orchestrator — health-check → record ingest (session-PoP-gated) → `kotoba commit` → componentize-py WASM build. |
| `agent.wasm` | build output (gitignored). |

## Graph

```
START → manufacture → narrate → END
```

`manufacture` runs the manifest-ordered chain, threading each cell's output forward
as the chain of custody (feedstock lot → wafer batch → cell batch → module serial →
loading cycle → outbound manifest):

```
supply_procurement → polysilicon_refine → ingot_wafer → cell_process
   → module_assembly → panel_loading → outbound_logistics
```

A cell that refuses on a gate (G2/G11/G12/…) returns a structured
`{"refused": True, "reason": …}` recorded in `chain_trace` — the deploy path stays
observable end-to-end and never aborts on a single refusal. The cells write their
own records to the kotoba Datom log via `datalog.transact` (G6/G8); with no host
binding (local dev) they compute but do not persist — never a fake write, never a
non-kotoba store (substrate boundary).

`narrate` is **advisory**: it routes a one-line run summary through `KotobaLLM`
(`kotoba:kais/llm.infer` WIT import → Murakumo fleet, LiteLLM `127.0.0.1:4000`,
Charter Murakumo-only invariant ADR-2605215000). It never changes the deterministic
attestations (G11). `model_cid=""` lets the host's `MURAKUMO_DEFAULT_MODEL` select
the deployed model.

## Inference model

`KotobaLLM(model_cid="")` emits a `kotoba:kais/llm.infer` WIT **import**. The WASM
component embeds **no** model and **no** network client. The kotoba host binds that
import to the Murakumo fleet at runtime. No external LLM is reachable from this
actor (G5 / ADR-2605215000).

## Build (verified working — 2026-06-02)

componentize-py 0.23 lives in the repo venv (`.venv/bin`). Run `deploy.sh`, which
builds the component directly (it needs three import roots at once — the cells, the
kotoba_langgraph package, and site-packages — which the shared `build-pywasm.sh`'s
single-path override cannot supply):

```sh
# from anywhere — deploy.sh resolves its own paths
PATH="$(git rev-parse --show-toplevel)/.venv/bin:$PATH" \
  20-actors/himawari/deploy/deploy.sh
```

Equivalent manual build (what `deploy.sh` runs):

```sh
ROOT="$(git rev-parse --show-toplevel)"
export PATH="$ROOT/.venv/bin:$PATH"
cd "$ROOT/40-engine/kotoba"
WIT_DIR="crates/kotoba-runtime/wit"               # DIRECTORY, not world.wit (see gotcha #1)
BIND="target/himawari-pywasm-bindings"
DEPLOY="$ROOT/20-actors/himawari/deploy"
SITE="$(python3 -c 'import site; print(site.getsitepackages()[0])')"

componentize-py -d "$WIT_DIR" -w kotoba-node bindings "$BIND"
componentize-py -d "$WIT_DIR" -w kotoba-node componentize agent \
  -p "$DEPLOY" -p "$BIND" -p py -p "$ROOT/20-actors" -p "$SITE" \
  -o "$DEPLOY/agent.wasm"
# → 20-actors/himawari/deploy/agent.wasm  (≈20 MB, valid WASM component magic)
```

**Verified**: produces a valid `agent.wasm` (~20 MB, magic `\0asm` + component-model
layer `0d00 0100`) bundling all 7 cells + `kotoba_langgraph` + `langgraph`.

## Deploy + invoke (in-WASM on the running :8077 node)

The node at `:8077` loads `agent.wasm` and invokes `WitWorld.run` via the same path
the other `kotoba-langgraph-*` actors use (`kotoba_wasm_run` MCP tool / invoke.run).

`POST /xrpc/com.etzhayyim.apps.kotoba.invoke.run` (operator-JWT-gated):

```json
{
  "program_cid":  "<content-CID of agent.wasm>",
  "program_type": "wasm-node",
  "agent_did":    "did:web:etzhayyim.com:himawari",
  "wasm_b64":     "<base64 of agent.wasm>",
  "ctx_b64":      "<base64 of the CBOR InvokeContext>"
}
```

**Gotcha #1 — `-d` points at the wit DIRECTORY, not `world.wit`.** The kotoba-node
world imports `wasi:http/outgoing-handler@0.2.0`; componentize-py only loads the
vendored `deps/` packages when `-d` is the `wit/` directory. Pointing it at the file
fails with `package 'wasi:http@0.2.0' not found`.

**Gotcha #2 — encode `ctx_cbor` with `kotoba_langgraph._cbor.dumps`, NOT `cbor2`.**
The guest's minimal `_cbor.loads` mis-decodes `cbor2`'s nested maps (duplicates the
top key as `None`), silently emptying `state["context"]`.

**Gotcha #3 — InvokeContext wire format is `{"args": {"input": <state>}}`**, not the
bare state (see `kotoba_langgraph/_entry.py::handle_invoke`). Wrap it or
`state["context"]` arrives empty.

**Gotcha #4 — ProgramStore caches by `program_cid`.** Use a content-addressed
`program_cid` (CID of the actual wasm bytes) per version so a new build is not
shadowed by a previously-cached component under the same agent DID.

A minimal invocation context (one manufacturing run) carries per-cell inputs under
`context.<cell>`; e.g. to drive the module-assembly attestation:

```json
{ "context": {
  "module": {
    "moduleSerial": "mod.2026-06.000001",
    "cellBatchId":  "cell.2026-06.0001",
    "feedstockLotId": "lot.poly.2026-06.0001",
    "bomCid": "cid:himawari:bom:representative:000001",
    "ratedWp": 440, "measuredWp": 438,
    "destinationActorDid": "did:web:etzhayyim.com:hikari",
    "attestingRobots": ["otete", "mimi"],
    "flashIv": {"...": "..."}, "elImage": {"...": "..."}
  }
} }
```

`chain_trace` reports `ok` / `refused` / `error` per stage; `narrative` carries the
Murakumo one-liner (empty + `narrate_error` if the gateway is unbound — host↔gateway
plumbing, not an actor bug, exactly as observed for aria).

## Lexicon records → PDS write path (ADR-2606015000)

The seven `com.etzhayyim.himawari.*` lexicon records (manifest `:lexiconNamespaces`)
are written into the canonical kotoba Datom log through the kotoba-server PDS XRPC
surface — **no separate TS PDS** (ADR-2606015000 retired it):

1. **`com.etzhayyim.pds.session.verify`** (ADR-2606015000 D1, landed) — the operator
   presents a compact EdDSA JWS **session Proof-of-Possession**. kotoba-server
   resolves the signer DID (did:key trustless / did:web via ERC725-mirror doc) and
   verifies the signature **zero-access** (server holds no key). Every write is gated
   on a valid session PoP — the no-server-key substrate boundary (G15-equivalent).
2. **`com.etzhayyim.apps.kotobase.kg.ingest`** — each record is projected to an entity
   (`id` from the record's `:db.unique/identity` attr; literals → `claims`, refs →
   `relations`) and asserted into the `com.etzhayyim.himawari` named graph (canonical
   EAVT; G6/G8). `kotoba commit` seals the hot arrangement.

`ingest_records.py` implements both legs. Without `KOTOBA_SESSION_POP` it is a
**dry run** (parse + project + count; no writes — outward writes are operator-gated):

```sh
# dry run — parse + project + count (verified: 7 records / ~45 datoms)
python3 20-actors/himawari/deploy/ingest_records.py --dry-run

# live ingest — operator session PoP present
KOTOBA_SESSION_POP=<compact-eddsa-jws> \
  python3 20-actors/himawari/deploy/ingest_records.py \
    --url http://127.0.0.1:8077 --graph com.etzhayyim.himawari
```

> AT Protocol PDS `repo.*` / `sync.*` write endpoints are still **phased** in
> ADR-2606015000 (only session-PoP verify landed; D2 repo/sync ports are
> in-progress). himawari therefore writes records through the kotoba-native
> `kg.ingest` datom path (the canonical Datom log is first-class state per
> ADR-2605312345); when the faithful `com.atproto.repo.putRecord` surface lands on
> kotoba-server, `ingest_records.py` swaps its second leg to it with no change to
> the schema or the session-PoP gate.

## End-to-end (deploy.sh)

```sh
KOTOBA_URL=http://127.0.0.1:8077 \
KOTOBA_SESSION_POP=<operator-session-pop-jws> \
  20-actors/himawari/deploy/deploy.sh
```

`deploy.sh` health-checks the node, ingests the records (dry-run without a PoP),
seals with `kotoba commit`, then builds `agent.wasm`.

## Status (honest)

- `agent.py` — ✅ written; graph compiles + invokes locally (StateGraph 2-node;
  7-cell chain runs, `chain_trace` correct, `narrate` degrades gracefully without a
  host LLM binding).
- `agent.wasm` — ✅ **BUILT** (~20 MB, valid WASM component magic) via componentize-py
  0.23 with all 7 cells + kotoba_langgraph + langgraph bundled. **2026-06-02.**
- `schema.edn` / `seed.edn` — ✅ 7 record types; `ingest_records.py` dry-run parses
  7 records / ~45 datoms with correct claim/relation split.
- `ingest_records.py` — ✅ PDS write path wired (`session.verify` gate +
  `kg.ingest`); ⚠️ **not yet run against a live node** (needs a running `:8077` +
  an operator session PoP — outward writes are operator/Council-gated, G11/G10).
- **In-WASM invoke on a live `:8077`** — ⚠️ **NOT YET EXECUTED here** (no running
  node in this environment). The build is verified; the invoke recipe above is the
  drop-in path the aria/okaimono actors use and is reproducible once a node is up.
- Cells themselves: R0.1 — `.solve()` fully implemented (88 pure-logic tests green,
  per actor CLAUDE.md); operational Pregel/Murakumo runtime wiring + sim + live
  kotoba materialization light up at R1 activation (ADR-2606021200 §R1 triggers).
