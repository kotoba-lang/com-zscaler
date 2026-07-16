# shirabe 調べ — WASM actor (kotoba-clj)

The pure `.cljc` methods (`analyze` / `retrieve` / `synthesize` / `session` / `kotoba`) are
written to run **both** under babashka (the reference oracle, ADR-2606131300) and inside the
**kotoba Clojure engine** as a content-addressed WASM actor (the One-Worker-many-WASM pattern,
ADR-2606014500). They take no ambient I/O: the two live legs are **injected**

- `fetcher` — a read-only public-web search/fetch (G1), and
- `infer`   — a Murakumo-fleet gemma4 call (G2),

so the WASM component is a pure function of `(question, fetcher, infer, as-of)`. The host owns
the live legs and the Datom-log write, which keeps G7 (the loop does no implicit network I/O)
**structural** rather than a policy note — exactly the meisai/ibuki stateless-heartbeat shape.

## Boundary at the WASM edge

| leg | who runs it | gate |
|---|---|---|
| web search/fetch | host (browser-local `manako`/`ameno` tier, or a donated mesh node) | G1 read-only public web |
| gemma4 inference | host → Murakumo loopback (LiteLLM / EVO-X2 / per-node Ollama gemma 4 E4B QAT) | G2 Murakumo-only |
| Datom write | host → `com.etzhayyim.apps.kotoba.datomic.transact` on the kotoba node | G7 operator-gated |

## Build (operator step)

The componentize step (jco / the kotoba-clj→wasm toolchain) is the operator leg, gated as usual.
The reference run that proves the loop end-to-end today is the babashka driver:

```bash
# from repo root
bb --classpath 20-actors 20-actors/shirabe/methods/live.clj "青山の島田は今日やっている?" --asof 2026-06-13
```

which performs a LIVE DuckDuckGo search → LIVE gemma 4 E4B (local Ollama, Murakumo-conformant)
→ cited answer → appends a content-addressed tx to the local kotoba Datom log (verify-chain ok).
