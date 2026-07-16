# hibiki 響 — ossekai proposal → persuasive presentation (動画化)

When an actor does **おせっかい (御節介)** at root, it doesn't act — it **proposes**, and
**ossekai** carries the proposal. **hibiki** turns that `:proposal` into a short narrated
**moving-image-with-SFX presentation**: 映像 (storyboard) + 音声 (neutral synthetic narration) +
効果音/BGM. The proposal-side sibling of **utsushie 写し絵** (article→video).

The point is **説得力 (せっとく力)** — but reframed: not compliance-engineering, but
**clarity + resonance** (the proposal lands clearly and resonates on its merits). hibiki walks the
same 御節介 knife-edge ossekai walks, structurally pinned to the caring side: it may make a
proposal *understood and felt*, but it may **not** manufacture urgency, optimize watch-time,
weaponize sound, or hide the exit. The **last storyboard scene is always the consent/opt-out card.**

## Run

```bash
bb 20-actors/hibiki/run_tests.clj            # charter-gates + present-plan, all green
bb -cp 20-actors -m hibiki.methods.test-present-plan
# auto-discovered fleet-wide:
bb run test:actors
```

## Pipeline

```
actor おせっかい ─► ossekai :proposal ─► hibiki.build-plan (R0, pure/offline)
   {finding, why, action, severity, aggregate?, linkUrl, lang}
        │ [H1–H8 structural gates — refuse on violation]
        ▼ presentation PLAN
     :narrationScript (≤ excerpt + action, per-lang via kataribe)   音声
     :storyboard      (context → finding → stakes → proposed → CONSENT)  映像
     :sfxCues         (allowlist :purpose, LUFS-normalized)         効果音
     :musicBed        (calm | neutral | hopeful-sober)              BGM
        ▼ render() → R0-gated (H5/G8 Murakumo-only; reuses utsushie's leg at R1)
        ▼ publish  → ossekai (member-signed, aggregate-first, mute/consent honored)
```

## Gates

H1 propose-not-act · H2 script ≤ excerpt · H3 anti-deepfake · **H4 the 説得力 knife-edge
(no engagement-opt / fake-urgency / weaponized-audio)** · H5 Murakumo-only render · H6
member-signed publish · H7 aggregate-first · H8 non-eschatological. See `CLAUDE.md` and
ADR-2606241600 for the full table.

- **ADR**: `90-docs/adr/2606241600-hibiki-ossekai-proposal-dougaka-presentation.md`
- **Status**: R0 — offline plan builder only. NO render, NO publish (those are R1, carried by ossekai).
