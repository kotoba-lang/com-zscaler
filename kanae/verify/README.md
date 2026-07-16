# kanae (鼎) — fiscal-flow ingest VERIFICATION harness

Deploy + run runbook for the `kanae-fiscal-ingest` kotoba WASM actor, which
demonstrates the kanae R0 pipeline end-to-end on the live runtime:

> **ingest fiscal slice → kotoba EAVT `fundFlowEdge` → Murakumo `gemma-4-26B-A4B-it`
> non-adjudicating narrative → kotoba EAVT `flowNarrative`**

- **ADR**: ADR-2605302300 (kanae) + ADR-2605301625 (kotoba actor deploy + Murakumo live) + ADR-2605253000 (Mac mini M4 16GB gemma-4-26B-A4B NVMe disk inference) + ADR-2605215000 (Murakumo-only inference)
- **Actor**: `kanae-fiscal-ingest/` (Rust → `wasm32-wasip2` component, 74 KB)
- **NOT** one of the 5 gated production kanae cells (those stay path-reserved until Council Lv6+ ratify). This is a verification harness with the same constitutional discipline (G4 non-adjudicating / G7 Murakumo-only / G2 kotoba-native).

## Status (session 2026-05-30)

| Step | State |
|---|---|
| Actor authored + compiled to `wasm32-wasip2` | ✅ `target/wasm32-wasip2/release/kanae_fiscal_ingest.wasm` (valid component v0x1000d; imports `kotoba:kais/{kqe,auth,llm,chain}`, exports `run`) |
| Deploy to live kotoba `:8077` + Murakumo `gemma-4-26B-A4B-it` run | ⏳ blocked on a reachable 26B Murakumo node (see "Bring up the 26B model") |

**Why blocked (2026-05-30 LAN probe from jacob 192.168.1.9, wired LAN up):**
- `192.168.1.17` (judah, LiteLLM gateway `:4000` + Ollama `:11434`) reachable — but Ollama there serves only `gemma4:e4b` / `gemma3:1b` / `qwen3.5:9b` (no 26B), and the LiteLLM `:4000` returns 401 (master key is on judah, not on this host).
- `192.168.1.70` (EVO-X2, the GPU node that serves the heavy 26B) — **no ping (powered down)**.
- ⇒ `gemma-4-26B-A4B-it` is not currently reachable from this host.

## 1. Build the actor

```bash
cd 20-actors/kanae/verify/kanae-fiscal-ingest
# GOTCHA: Homebrew rustc is on PATH and lacks the rustup wasm32-wasip2 std,
# so it shadows rustup's rustc and breaks the build. Force rustup's stable:
STABLE=~/.rustup/toolchains/stable-aarch64-apple-darwin/bin
RUSTC="$STABLE/rustc" PATH="$STABLE:$PATH" "$STABLE/cargo" \
  build --target wasm32-wasip2 --release
# → target/wasm32-wasip2/release/kanae_fiscal_ingest.wasm
```

Verify the component world:

```bash
wasm-tools component wit target/wasm32-wasip2/release/kanae_fiscal_ingest.wasm
# expect: imports kotoba:kais/{kqe,auth,llm,chain}; export run(ctx-cbor) -> result<list<u8>,string>
```

## 2. Bring up the 26B model (pick ONE — all are Murakumo-legal per ADR-2605215000)

**Option A — power on EVO-X2 (192.168.1.70)** and confirm it serves the model:

```bash
ping -c1 192.168.1.70
curl -s http://192.168.1.70:11434/api/tags | jq -r '.models[].name'   # expect a 26B / a4b tag
# inference endpoint (OpenAI-compat) = http://192.168.1.70:11434/v1
```

**Option B — run 26B on THIS Mac mini M4 16GB via NVMe disk inference** (ADR-2605253000;
`gemma-4-26B-A4B-it-UD-Q3_K_M.gguf` is 12.73 GB, ~7.7–8.4 tok/s). Needs ~13 GB free disk
(`df -h /` showed 21 GB) + llama.cpp serving an OpenAI-compat endpoint:

```bash
# see 70-tools/nvme-disk-inference/README.md for the exact llama-server flags
llama-server --model gemma-4-26B-A4B-it-UD-Q3_K_M.gguf --port 11500 ...
# inference endpoint = http://127.0.0.1:11500/v1
```

**Option C — LiteLLM gateway (192.168.1.17:4000)** once a 26B backend is up behind it +
the master key is available on this host:

```bash
KEY=$(security find-generic-password -s etzhayyim.litellm -a MASTER_KEY -w)
curl -s http://192.168.1.17:4000/v1/models -H "Authorization: Bearer $KEY" | jq -r '.data[].id'
# inference endpoint = http://192.168.1.17:4000/v1 (+ KOTOBA_INFERENCE_API_KEY=$KEY)
```

## 3. Start kotoba with Murakumo wired to the 26B model

```bash
kotoba init    # one-time: operator identity in macOS Keychain
KOTOBA_INFERENCE_URL=<endpoint from step 2, e.g. http://192.168.1.70:11434/v1> \
KOTOBA_INFERENCE_MODEL=gemma-4-26B-A4B-it \
kotoba serve   # :8077 (prod) / :8080 (dev)
# expect log line: "HTTP inference engine active model=gemma-4-26B-A4B-it"
curl -s http://127.0.0.1:8077/health | jq .
```

## 4. Deploy + trigger the actor

`invoke.run` is operator-JWT gated. The kotoba CLI builds the operator JWT from the
Keychain identity; export it as `KOTOBA_TOKEN`, or mint a JWT-shaped token.

```bash
WASM_B64=$(base64 -i kanae-fiscal-ingest/target/wasm32-wasip2/release/kanae_fiscal_ingest.wasm)
CTX_B64=$(printf '{}' | base64)

curl -sS -X POST http://127.0.0.1:8077/xrpc/com.etzhayyim.apps.kotoba.invoke.run \
  -H "Authorization: Bearer ${KOTOBA_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{
    \"program_cid\":  \"kanae-fiscal-ingest-verify\",
    \"program_type\": \"wasm-node\",
    \"agent_did\":    \"$(kotoba whoami | sed -n 's/.*did: *//p' | head -1)\",
    \"wasm_b64\":     \"${WASM_B64}\",
    \"ctx_b64\":      \"${CTX_B64}\",
    \"graph_cid\":    \"etzhayyim/kanae/fiscal-flow\"
  }" | jq .
```

Expected response (assert_count = 3 edges × 8 + 1 narrative × ~9 ≈ 33 datoms):

```json
{ "status": "ok", "gas_used": <n>, "output_b64": "<b64>", "assert_count": 33 }
```

Decode `output_b64` — expect:

```
kanae-fiscal-ingest ok | did=did:key:... | ctx_len=2 | edges_asserted=3
 | narrative=kanae-narrative-verify-0001 | inference=murakumo-inferred
 | model=gemma-4-26B-A4B-it | graph=etzhayyim/kanae/fiscal-flow
```

(`inference=murakumo-inferred` confirms the 26B leg ran. If the model node is down it
reads `inference=pending-inference` and the `fundFlowEdge` datoms are still persisted —
the ingest + EAVT-persist half does not depend on the LLM.)

## 5. Read back the persisted EAVT datoms (the "datomic kotoba api")

```bash
# Datomic EAVT index over the graph:
curl -sS -X POST http://127.0.0.1:8077/xrpc/com.etzhayyim.apps.kotoba.datomic.datoms \
  -H "Authorization: Bearer ${KOTOBA_TOKEN}" -H 'Content-Type: application/json' \
  -d '{"graph":"etzhayyim/kanae/fiscal-flow","index":":eavt"}' | jq .

# Or SPARQL:
curl -sS -X POST http://127.0.0.1:8077/xrpc/com.etzhayyim.apps.kotoba.graph.sparql \
  -H "Authorization: Bearer ${KOTOBA_TOKEN}" -H 'Content-Type: application/json' \
  -d '{"graph":"etzhayyim/kanae/fiscal-flow","sparql":"SELECT ?s ?p ?o WHERE { ?s ?p ?o }"}' | jq .
```

Expect the `kanae/fundFlowEdge/*` datoms for the 3 sample edges + the
`kanae/flowNarrative/*` datoms (including `nonAdjudicatingNotice=true` and
`murakumo/inferenceSubstrate=murakumo`).

## Constitutional notes

- **G4 non-adjudicating**: the narrative prompt forbids legal-conclusion language; the
  `flowNarrative` datom carries `nonAdjudicatingNotice=true`. The actor never asserts
  wrongdoing.
- **G7 Murakumo-only**: inference flows through the kotoba host `llm.infer` bridge, wired
  to a Murakumo node via `KOTOBA_INFERENCE_*`. No vendor-LLM path exists in the guest.
  A local Mac-mini Ollama / llama.cpp node (Options A/B) is a Murakumo fleet member per
  ADR-2605215000; RunPod / OpenAI-direct / Vertex are prohibited.
- **G2 kotoba-native**: every datom is written via `kqe.assert-quad` into kotoba EAVT — no
  RisingWave / Postgres / Lance.
