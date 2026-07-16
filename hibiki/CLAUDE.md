# 20-actors/hibiki — CLAUDE.md

## Identity

- **Name**: hibiki (響 — resonance)
- **DID**: `did:web:etzhayyim.com:actor:hibiki`
- **ADR**: ADR-2606241600 (R0 scaffold)
- **Sibling**: utsushie 写し絵 (ADR-2606161536) — article→video medium; hibiki is its **proposal→presentation** twin
- **Parent**: ossekai 御節介 (ADR-2605264000) — upstream source + downstream carrier
- **Tier**: Tier-B
- **Status**: R0 — offline presentation-PLAN builder only. NO render, NO publish.

## What hibiki is

A **MEDIUM**, never a source (G11). When an actor does おせっかい at root — i.e. routes a
Wellbecoming-nudge / info-arbitrage **proposal** to ossekai — hibiki turns that `:proposal`
into a short narrated **moving-image-with-SFX presentation** (動画化): 映像 (storyboard) +
音声 (neutral synthetic narration) + 効果音/BGM. The point is **説得力 = せっとく力**: that the
proposal *lands clearly and resonates on its merits*.

## The 説得力 knife-edge (constitutional novelty — IMMUTABLE)

A naive "persuasion" actor is a dark-pattern factory and is **charter-forbidden** — ossekai is
aggregate-first, anti-engagement, anti-addictive (§1.13/§1.4). hibiki resolves this by walking
the **same 御節介 knife-edge** ossekai walks, structurally pinned to the caring side:

> 説得力 is reframed from **compliance-engineering → CLARITY + RESONANCE.**
> A presentation may make a proposal *understood and felt on its own terms*.
> It may NOT manufacture urgency, optimize watch-time, weaponize sound, or hide the exit.

The pin is **dual** (lexicon const-false fields AND the builder refuses):

| Gate | = | Rule |
|------|---|------|
| H1 | G1  | PROPOSE-not-act — builds a PLAN; render/publish G8-gated, carried by ossekai; no truth-verdict |
| H2 | G4  | narration ≤ the proposal's ≤280-char finding excerpt + the proposed action; nothing more |
| H3 | G9  | ANTI-DEEPFAKE — neutral synthetic narrator/visuals; no real-person likeness or voice clone |
| H4 | G2  | the knife-edge — no watch/dwell/conversion edit, no fake urgency, no weaponized audio |
| H5 | G6  | MURAKUMO-ONLY render/TTS (ADR-2605215000); external-GPU / commercial-TTS unrepresentable |
| H6 | G7  | MEMBER-SIGNED publish (ADR-2605231525); no server-held key; carried by ossekai |
| H7 | —   | AGGREGATE-FIRST (ossekai G4) — default audience aggregate; targeted secondary + consent-bound |
| H8 | §1.15 | NON-ESCHATOLOGICAL — sober; SFX/music allowlisted by :purpose; no doom/fear/euphoria edit |

The **last storyboard scene is ALWAYS the consent/opt-out card** — the structural proof that
this is an invitation (which always shows the exit), never coercion.

## Pipeline

```
ossekai :proposal ──► hibiki.build-plan (R0, pure/offline)
   {finding, why, action, severity, aggregate?, linkUrl, lang}
        │  [H1–H8 structural gate check — refuse on violation]
        ▼
   presentation PLAN
     :narrationScript  (≤ excerpt + action, per-lang via kataribe)   ← 音声
     :storyboard       (context → finding → stakes → proposed → CONSENT) ← 映像
     :sfxCues          (allowlist :purpose, loudness-normalized)      ← 効果音
     :musicBed         (calm | neutral | hopeful-sober)               ← BGM
     :narrator         "synthetic-neutral"
        │
        ▼  render()  → R0-gated (H5/G8 Murakumo-only; reuses utsushie's render leg at R1)
        ▼  publish   → ossekai (member-signed, aggregate-first, mute/consent honored)
```

## Run

```bash
bb -cp 20-actors -m hibiki.methods.test-present-plan   # 7 tests / 21 assertions green
```

## Non-goals

- Not a source / not an :original — narrates an ossekai proposal, never makes a first-person claim.
- Not a renderer at R0 — `render` raises (Murakumo-only, G8-gated).
- Not a publisher — ossekai carries (member-signed, no server key).
- Not an engagement engine — no metric optimizes the edit; 説得力 = clarity + resonance.
