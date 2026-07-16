# tsumugi deploy — Murakumo-only intel narration (langgraph + kotoba)

ADR-2606092000 + **ADR-2605215000 (Murakumo-only inference)**. Sets up the power-dynamics
intel (scale (A) + 旗 banner (B)) to be **narrated by the Gemma-4 Murakumo Mac-mini fleet**
via **LangGraph** running **in-WASM on a kotoba node**, per the founder direction.

## Two forms (same Murakumo invariant)

| file | runtime | LLM path | use |
|---|---|---|---|
| `methods/narrate.py` | plain Python (stdlib) | Murakumo LiteLLM HTTP (`127.0.0.1:4000`) via urllib, **operator-gated** | local dev / CI / this `/loop`; runs + tests OFFLINE (dry-run) |
| `deploy/agent.py` | **in-WASM on a kotoba :8077 node** | `kotoba:kais/llm` WIT import → host-bound to the Murakumo fleet | canonical deploy (mirrors `himawari/deploy/agent.py`) |

Both are **Murakumo-only (G6)**: an external provider is structurally unreachable. `narrate.py`
**refuses** any non-fleet host (`_assert_murakumo`); `agent.py` embeds **no model and no network
client** — only the `llm.infer` import, bound by the host to LiteLLM `127.0.0.1:4000` (gemma 4 /
`MURAKUMO_DEFAULT_MODEL`; the Maxwell weight is the target, gemma3:4b the deployed baseline).

## Run the local-dev twin (dry-run — no fleet needed)

```bash
python3 20-actors/tsumugi/methods/narrate.py
#   → out/narration.dryrun.md  (SYSTEM + USER aggregate-intel prompt; status=unreachable/dry-run)
```

## Run a real Murakumo narration (on a fleet host)

```bash
# requires the operator gate AND the LiteLLM gateway reachable (127.0.0.1:4000 or EVO-X2 LAN)
TSUMUGI_MURAKUMO_GATE=1 TSUMUGI_OPERATOR_DID=did:web:etzhayyim.com:… \
  MURAKUMO_MODEL=gemma3:4b \
  python3 20-actors/tsumugi/methods/narrate.py
#   → out/narration.md          (the terse 4-sentence mirror observation)
```

## Build + deploy the in-WASM graph (canonical)

```bash
PATH="$PWD/.venv/bin:$PATH" KOTOBA_SITE_PKG="$PWD/20-actors" \
  ./scripts/build-pywasm.sh 20-actors/tsumugi/deploy/agent.py
# then run in-WASM on the live node (kotoba_wasm_run / invoke.run with agent.wasm)
```

## Charter guards (do not weaken)

- **G6 Murakumo-only** — inference never leaves the fleet (ADR-2605215000). `narrate.py` raises
  on a non-fleet host; `agent.py` has no client to call out with.
- **S6 / H2 mirror + non-adjudicating** — the system prompt forbids verdict tokens
  (corruption/collusion/extremist) and any name/implication of a private individual; the
  analyzers are person-excluded (S2/H4) by construction, so the LLM never receives one.
- **G7 outward-gated** — `published=false`. This emits an **advisory** narration only; a
  published social post is a separate Council + operator-gated step (not done here).
- The deterministic analyzer readouts are the source of truth; the LLM only renders them.
