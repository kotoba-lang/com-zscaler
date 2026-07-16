# sanae 早苗 — field-agriculture robotics

> *早苗* = rice seedlings ready for transplanting. The first robotics actor of the labour-liberation
> wave, freeing the largest pool of human toil on Earth: field agriculture.

**LPS #1** (ADR-2606032100). ISIC **A01** · ISCO **6111/9211** · UNSPSC **70**. The robotics body of
mitsuho 瑞穂 (L2 Sustenance). DID `did:web:etzhayyim.com:actor:sanae`.

## Why agriculture first

~27% of world employment is agriculture; the field-labour share is the bulk — back-breaking,
heat-exposed, poverty-locked work. mitsuho/suki exist as designs but have no field robots. sanae is
that body: sow (早乙女), weed (草薙, herbicide-free), harvest (稲架), survey (案山子), sense (田螺).

## The deal: automate the toil, never abandon the worker

sanae cannot deploy live until the field-labourers it displaces are registered for the
**tenure-weighted Displacement Dividend** (ADR-2606032130, gate **G2**) — in-kind Basic High Income
weighted by years of service (勤続年数), funded by sanae's own surplus-donation, cash≡0.

## Layout

```
manifest.edn / manifest.jsonld   actor blueprint + DID
cells/                           5 langgraph cells (autonomous_weeding fully coded)
lex/                             com.etzhayyim.sanae.* lexicons
kotoba/                          EAVT schema + :representative seed
data/fleet.kotoba.edn            robotics fleet (design)
methods/labor_liberation.py      LPS ranking + freed-labour-hours (7/7 tests)
```

**R0 design-only.** No hardware. Regenerative, seed-sovereign, herbicide-free by constitution (G9).
