# utsushie 写し絵 — CLAUDE instructions

News→video medium. ADR-2606161536 §D2. **Read the root `/CLAUDE.md` Charter + substrate
rules first**, then kawaraban's `CLAUDE.md` (utsushie inherits its 11 gates; the U1–U6
below are the same gates made concrete for video — they weaken nothing).

## One-sentence identity

utsushie turns a kawaraban `:article` into a short **narrated, multilingual video that
links out** — the 映像 sibling of 瓦版's printed sheet. It is a **medium, never a source**
(G11): it narrates a `:mirror`/`:actor-event`, never an `:original`.

## When editing

- `methods/render_plan.cljc` is **pure and offline** — no network, no model, no clock/RNG.
  Keep it deterministic. `render()` MUST keep raising at R0 (live render is G8-gated +
  Murakumo-fleet only, U5 = G6, ADR-2605215000). Do NOT wire it to RunPod/external GPU.
- The U1–U6 gates live in **two places**: `lex/video.edn` (`const false` / `maxLength`)
  AND `methods/render_plan.cljc` (`CharterRefusal`). Touch one, touch both or you create a
  representable charter violation.
- **U3 anti-deepfake is the highest-risk gate** — never emit a path that depicts a named
  real person photoreally or clones a voice. Neutral synthetic narrator only.
- Tests are standalone-runnable (`bash run_tests.sh`) AND pytest-style.
  Keep them so. One-command runner: `./run_tests.sh`.

## Roadmap

- **R0 (now)** — lexicon + offline render-plan builder + tests. No render, no publish.
- **R1 (G8)** — Murakumo-fleet render (reuse animeka/shinshi/yukkuri primitives) → mp4
  blob → i18n per-language scripts (D3) → feed-post membrane publish (D4). Council Lv6+.

## Siblings

kawaraban (source article) · i18n (narration scripts) · feed-post membrane (publish) ·
animeka/shinshi/yukkuri/ongakuka/kokoro-ts (render primitives, Murakumo-only).
