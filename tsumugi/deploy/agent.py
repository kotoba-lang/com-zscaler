"""tsumugi 紡ぎ — power-dynamics intel narration langgraph actor (kotoba WASM cell).

ADR-2606092000 + ADR-2605215000 (Murakumo-only) + ADR-2605301625/2605302355 (kotoba
LangGraph actor runs in-WASM, LLM via the kotoba host → Murakumo fleet). Mirrors
`himawari/deploy/agent.py`. The file MUST be named ``agent.py`` and expose ``WitWorld.run``.

This is the *deploy-side* graph: it runs the (A) scale + (B) 旗 banner analyzers over the
committed seeds and routes ONE advisory ``narrate`` node through ``KotobaLLM`` — on a live
kotoba node the host binds that import to the Murakumo fleet (LiteLLM 127.0.0.1:4000, EVO-X2
LAN, per-node gemma3:4b / the Maxwell weight). Leave ``model_cid=""`` so the host's
``MURAKUMO_DEFAULT_MODEL`` (gemma 4) selects the deployed model. The WASM component embeds NO
model and NO network client — it only emits the ``kotoba:kais/llm`` infer import (Charter
"Murakumo-only inference" invariant; an external provider is structurally unreachable).

The narration is ADVISORY + a MIRROR (G6/S6/H2): it never adjudicates, never names a private
person (the analyzers are person-excluded by construction), never emits a published post (G7 —
``published=false``). The deterministic analyzer readouts are the source of truth; the LLM only
renders a terse 4-sentence observation over them.

Build (on a kotoba toolchain host):
    PATH="$PWD/../../.venv/bin:$PATH" KOTOBA_SITE_PKG="$PWD/../../20-actors" \
      ./scripts/build-pywasm.sh ../../20-actors/tsumugi/deploy/agent.py
Deploy (in-WASM on the running :8077 node — see deploy/README.md):
    kotoba_wasm_run / invoke.run with the produced agent.wasm
"""
from __future__ import annotations

from typing import TypedDict

import wit_world

from kotoba_langgraph import (
    StateGraph,
    KotobaLLM,
    START,
    END,
    handle_invoke,
)

# componentize-py static analysis needs these at module scope (mirrors himawari/aria/okaimono).
import kotoba_langgraph._cbor   # noqa: F401
import kotoba_langgraph._entry  # noqa: F401
import wit_world.imports.llm    # noqa: F401

# The pure-stdlib analyzers (no numpy, pywasm-ready) are bundled as the deterministic core.
from tsumugi.methods import analyze_scale as A      # noqa: E402
from tsumugi.methods import analyze_banner as Bnr   # noqa: E402
from tsumugi.methods.narrate import build_prompt, SYSTEM_PROMPT  # noqa: E402


class TsumugiState(TypedDict, total=False):
    context: dict
    scale: dict
    banner: dict
    prompt: str
    narrative: str
    narrate_error: str


# ── LLM (routed through kotoba:kais/llm WIT → Murakumo; G6 Murakumo-only) ────
# model_cid="" → host MURAKUMO_DEFAULT_MODEL (gemma 4). The component embeds no model/client.
_llm = KotobaLLM(model_cid="", system_prompt=SYSTEM_PROMPT)


def _analyze(state: TsumugiState) -> dict:
    """Deterministic core — run (A) scale + (B) banner analyzers over the committed seeds."""
    scale = A.analyze(*A.load())
    banner = Bnr.analyze(*Bnr.load())
    return {"scale": scale, "banner": banner, "prompt": build_prompt(scale, banner)}


def _narrate(state: TsumugiState) -> dict:
    """Advisory mirror narration via Murakumo. A failure NEVER corrupts the deterministic core."""
    try:
        return {"narrative": _llm.invoke(state.get("prompt", ""))}
    except Exception as e:  # noqa: BLE001
        return {"narrate_error": str(e)[:200]}


def _build_graph():
    g = StateGraph(TsumugiState)
    g.add_node("analyze", _analyze)
    g.add_node("narrate", _narrate)
    g.add_edge(START, "analyze")
    g.add_edge("analyze", "narrate")
    g.add_edge("narrate", END)
    return g.compile()


_GRAPH = _build_graph()


class WitWorld(wit_world.WitWorld):
    def run(self, request: str) -> str:
        # published=false (G7) — this returns the advisory narration; it does not post.
        return handle_invoke(_GRAPH, request)
