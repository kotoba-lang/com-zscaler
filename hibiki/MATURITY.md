# hibiki 響 — Maturity

**Stage: R0** (design scaffold) — created 2026-06-24 per ADR-2606241600.
The ossekai-proposal sibling of utsushie 写し絵 (ADR-2606161536).

| Dimension | State |
|---|---|
| Lexicon | ✅ `lex/presentation.edn` — `com.etzhayyim.hibiki.presentation` with H1–H8 structural gates |
| Methods | ✅ `methods/present_plan.cljc` — offline, pure, deterministic plan builder + R0-gated `render` |
| Tests | ✅ `methods/test_charter_gates.cljc` (8/14, lexicon conformance) + `methods/test_present_plan.cljc` (7/21, builder) — `bb run_tests.clj` green; auto-discovered by `bb run test:actors` (ADR-2606131500) |
| Cells | ⛔ none yet (R1 — Pregel cell wrapping build-plan + the G8 render step) |
| Manifest | ✅ `manifest.jsonld` — `did:web:etzhayyim.com:actor:hibiki`, Tier-B, H1–H8 gates, sibling map (R0 runtime=offline-method) |
| Render | ⛔ R0-gated (H5/G8 + Murakumo-only) — `render` raises by design; reuses utsushie's render/TTS leg at R1 |
| Publish | ⛔ R1 — carried by ossekai (member-signed H6/G7, aggregate-first H7, mute/consent honored) |

## The 説得力 knife-edge (why this actor is constitutionally delicate)

A naive "persuasion" actor is a dark-pattern factory and is charter-forbidden. hibiki resolves
this by reframing 説得力 from compliance-engineering → **clarity + resonance**, pinned dually in
the lexicon (const-false fields) and the builder (refuse). The last storyboard scene is ALWAYS
the consent/opt-out card — the structural proof it is an invitation, never coercion.

## R0 → R1 gate

R1 lift requires: Council Lv6+ + operator (H5/G8), Murakumo-fleet render/TTS capacity
(reuse utsushie's leg), and the ossekai publish path (mention_dispatcher / aggregate_publisher,
member-signed) wired as the carrier.
