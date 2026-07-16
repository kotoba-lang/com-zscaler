# kiyome (清め) — domestic / janitorial cleaning robotics actor

**DID**: `did:web:etzhayyim.com:actor:kiyome` · **Tier**: B · **Status**: R0 · **ADR**: 2606032100 (+ 2606032130)

## What this is

**LPS #3** in the labour-liberation wave (ADR-2606032100). Frees domestic + janitorial cleaning labour —
**ISIC T/N81 · ISCO 9111/9112/9121 · UNSPSC 76** — enormous (~75 M domestic workers + tens of millions
of cleaners), invisible, gendered, and dignity-poor; no actor existed. The most Wellbecoming-relevant
gap (it frees the most under-recognized labour).

## Fleet (design/research, open-source, `:representative`)

箒 Houki (sweep/vacuum/mop) · 拭 Nugui (wipe/sanitize) · 厨 Kuriya (kitchen/dish, **research**) ·
塵取 Chiritori (in-building waste → haraedo at curb) · 濯 Susugi (laundry/linen, seeds roadmap #12).

## Cells

site_assessment · **surface_cleaning** (coded reference cell — enforces G9/N5) · sanitization ·
waste_segregation · linen_laundry. langgraph→WASM, `.solve()` raises at R0, Murakumo-only.

## Gates

G1 open-source firmware · **G2 displacement-dividend coupling** · G3 witness quorum · G4 Murakumo-only ·
G5 no-payroll/cash≡0 · G6 Wellbecoming (restore dignity) · G7 outward-gated · G8 sourcing-honesty ·
**G9 privacy-by-construction** — homes & private spaces: on-device only, **no cloud imagery, no
surveillance feed, no biometric capture** (encrypted envelope, ADR-2605181100). The cleaner robot is
the opposite of a spy.

## Non-goals

N1 no military/policing/patrol · N2 no surveillance product / in-home sensor feed to third parties ·
N3 no gig-cleaning dispatch substrate · N4 no replacing human care/companionship · N5 no
facial/biometric recognition of occupants.

## Honest

Dexterous kitchen/dish manipulation in unstructured homes is `:research` maturity. R0 = design +
`:representative` seed only; deployment in homes is Council Lv6+ + operator gated (G7) **and** privacy
gates (G9) are hard invariants in the lexicons (`onDeviceOnly const true`, `imageryRetained const false`).
