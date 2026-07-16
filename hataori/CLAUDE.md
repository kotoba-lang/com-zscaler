# hataori (機織) — garment / apparel robotics actor

**DID**: `did:web:etzhayyim.com:actor:hataori` · **Tier**: B · **Status**: R0 · **ADR**: 2606032100 (+ 2606032130)

## What this is

**LPS #2** in the labour-liberation wave (ADR-2606032100). Automates cut-make-trim garment work —
**ISIC C13-14 · ISCO 7531/7532/8219 · UNSPSC 53** — the sweatshop, the canonical image of the
exploited labour the Mission Charter targets (~65 M workers, overwhelmingly women, wage-theft- and
disaster-prone). hataori exists to **end** that labour, not to out-compete it into a worse one.

## Fleet (design / research, open-source, `:representative`)

裁 Tachi (cut) · 栲幡 Takuhata (weave/knit) · 縫殿 Nuidono (sew — hard long-tail, **research** maturity,
honest G8) · 検針 Kenshin (needle-detect QC) · 畳 Tatami (fold/pack).

## Cells

pattern_grading · fabric_cutting · garment_assembly · quality_inspection · **finishing_packing**
(coded reference cell — enforces G9/G2/N4). langgraph→WASM, `.solve()` raises at R0, Murakumo-only.

## Gates

G1 open-source patterns/firmware · **G2 displacement-dividend coupling** · G3 witness quorum ·
G4 Murakumo-only · G5 no-payroll/cash≡0 · G6 Wellbecoming · G7 outward-gated ·
**G8 sourcing-honesty** (seam manipulation is unsolved at scale industry-wide — no over-claim) ·
**G9 fair-labor-provenance** (every finished lot: no displaced worker re-employed below the
Basic-High-Income standard; dividend-attested).

## Non-goals

N1 no arms-related · N2 no wearer-surveillance products · N3 no faster sweatshop substrate ·
N4 no fast-fashion overproduction · N5 no pattern-IP gatekeeping.

## Honest

Seam-level robotic sewing of limp fabric is the long-tail unsolved problem; nuidono is `:research`
maturity. R0 = design + `:representative` seed only.
