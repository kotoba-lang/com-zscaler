# utsushie 写し絵 — News→Video Medium (mirror's projected-image sibling)

> Turns a kawaraban `:article`/`面` into a short **narrated, multilingual video** that
> links out. The 映像 (moving-image) sibling of 瓦版's printed sheet. ADR-2606161536 §D2.
> **R0 design scaffold** (2026-06-16).

写し絵 (utsushie) was the Edo-period magic-lantern moving-picture show — Japan's
pre-cinema projected image, the visual companion to the 瓦版 (kawaraban) news broadsheet.
utsushie is to video what kawaraban is to text: **a medium, never a source** (G11).

## What it is

An offline **render-PLAN builder** over kawaraban's bounded `:article` records. It does
**not** render and does **not** call a model at R0 — it produces a deterministic plan
(scenes + narration script + link card + target languages), and live render is **G8-gated
+ Murakumo-fleet only** (U5 = G6, ADR-2605215000).

```
kawaraban :article ─► utsushie.build_plan ─► {narrationScript ≤ excerpt, langs, linkUrl, gates}
   (mirror/actor-event)        (offline, pure)        │
                                                       └─► (R1, G8) Murakumo render → mp4 blob
                                                            → app.bsky.feed.post embed (i18n D3, membrane D4)
```

## The 6 gates (= kawaraban's, made concrete for video — see `lex/video.edn`)

| Gate | Meaning | Structural form |
|---|---|---|
| **U1 = G1** | no verdict — narration is attributive ("outlet X reported H at T") | `:verdict` const false |
| **U2 = G4** | script ≤ the article's ≤280-char fair-use excerpt; full body never narrated | `:narrationScript` maxLength 280 · `:fullTextNarration` const false |
| **U3 = G9** | **anti-deepfake** — no photoreal likeness / voice clone of a named real person | `:depictsPerson` + `:voiceClone` const false |
| **U4 = G2** | no engagement/dwell-driven edit; recency / 面-fit only | `:engagementOptimized` const false |
| **U5 = G6** | inference is Murakumo-fleet only; external-GPU render unrepresentable; render() R0-gated | `:externalGpuRender` const false |
| **U6 = G7** | publish is member-signed; no server-held key | `:serverHeldKey` const false |

## Layout

```
utsushie/
├── README.md / CLAUDE.md / MATURITY.md
├── run_tests.sh                     # one-command runner
├── lex/video.edn                    # com.etzhayyim.utsushie.video (U1–U6 structural gates)
└── methods/
    ├── render_plan.cljc             # offline, pure: build_plan() + R0-gated render()
    └── test_render_plan.cljc        # render-plan + charter-gates tests
```

## Siblings / boundaries

- **kawaraban 瓦版** — the text mirror + wire; supplies the `:article` utsushie narrates.
- **i18n** — supplies per-language narration scripts (149/200+ langs, Murakumo); ADR D3.
- **animeka / shinshi / yukkuri / ongakuka / kokoro-ts** — the I2V / TTS / BGM / ffmpeg
  primitives a future R1 render reuses (Murakumo-fleet only, per U5).
- **feed-post membrane (ADR-2605231902)** — the L1/L2/L3 publish path (D4).
