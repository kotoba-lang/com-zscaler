# mitsuho (瑞穂) — Food / Agriculture Tier-B Actor

**DID**: `did:web:etzhayyim.com:mitsuho`
**Namespace**: `com.etzhayyim.mitsuho.*`
**ADR**: ADR-2605261015 (R0 scaffold)
**Status**: R0 scaffold (2026-05-26) — all cells import-time RuntimeError
**Parent ADR**: ADR-2605261000 (Liberation Ladder — L2 Sustenance Tier gate)

## Overview

Food production actor for the Liberation Ladder L2 Sustenance Tier (≥4,500 kJ/day staple per adherent). Plant agriculture + freshwater aquaculture + alternative protein.

**R0 scope** excludes animal slaughter (deferred ethics gate), industrial monoculture, patented seeds, GMO without Council attestation, contract farming, commodities trading, ocean factory-fishing.

## Robotics Classes

| Class | Role | Inherited from | Notes |
|---|---|---|---|
| Giemon | tractor-equivalent crawler | kuni-umi | field cultivation, harvest |
| Otete | precision tool arm | kuni-umi | seeding, pruning, fine harvest |
| Mimi | metrology | kuni-umi | crop health, soil sampling |
| Sora | drone | kuni-umi | survey, spot treatment |
| Tsumugi (紡ぎ) | greenhouse / vertical-farm tending | R2+ placeholder | separate mech-design ADR required |

## Pregel Cells (5, R0)

All R0 cells are import-time RuntimeError (gate G14 + ADR-2605261000 §6 L2 activation barrier).

| Cell | Murakumo node | Phase | Input → Output |
|---|---|---|---|
| `field_cultivation` | naphtali | crop rotation | parcelDid, cropPlan → fieldStateRecord |
| `aquaculture` | zebulun | freshwater | parcelDid, speciesPlan → aquacultureStateRecord |
| `alt_protein_fermentation` | levi | bench bioprocess | strainDid, batchPlan → altProteinBatchRecord |
| `harvest_robotics` | joseph | harvest coordination | fieldStateRecord ∨ aquacultureStateRecord → harvestAttestation |
| `food_preservation` | simeon | shelf-stable | harvestAttestation → preservedFoodLot |

## Constitutional Gates (G1–G14)

See ADR-2605261015 for full list. **IMMUTABLE** per R0 scope.

Key gates:
- **G2**: Seed sovereignty (open-source seed banks only; no patented varietals)
- **G4**: Soil regeneration metric (≥0 net carbon balance per year)
- **G5**: Water cap (≤ regional sustainable yield)
- **G6**: No synthetic pesticides (IPM + organic-certified only)
- **G7**: No GMO without Council Lv6+ ≥3 attestation
- **G14**: Waste log per harvest (consumed/composted/spoiled/donated %)

## Non-Goals (N1–N10)

- N1: No animal slaughter (R0-R3; ethics gate deferred to R4+)
- N2: No industrial monoculture (>50 ha single-crop)
- N3: No patented seeds
- N4: No synthetic fertilizer factory operation (kuni-umi-adjacent carve-out)
- N5: No GMO without Council attestation
- N6: No contract farming
- N7: No commodities futures
- N8: No soil mining
- N9: No ocean factory-fishing (Funamori marine-actor carve-out)
- N10: No aquaculture in protected waters

## Roadmap

| Phase | Timeline | Scope | L-gate |
|---|---|---|---|
| **R0** | 2026-05-26 | Scaffold | — |
| **R1** | post-Council | Benchtop ≤0.01 ha + single crop | future ADR |
| **R2** | post-R1 | Pilot ≤1 ha + aquaculture + alt-protein | **L2 eligibility** |
| **R3** | post-R2 | Community-scale ≤10 ha + cold-chain | **L2→L3 required** |

## Lexicons (5, deferred to R1+)

```
com.etzhayyim.mitsuho.{
  parcelAttestation
  cropPlanAttestation
  harvestAttestation
  foodLotAttestation
  silenAgricultureReview
}
```

## Integration

- **Parent actor**: kuni-umi (robotics class lineage)
- **Peer actors**: yakushi (food-safety chemistry adjacency), hagukumi (meal-delivery consumer), hikari (energy supplier R2+), tatekata (greenhouse construction R2+)
- **Land**: LANDS.md parcel registration required for R2+

## References

- `/90-docs/adr/2605261000-labor-liberation-transition-mechanism.md` — L2 gate
- `/90-docs/adr/2605261015-mitsuho-food-agriculture-tier-b-actor-r0.md` — Master ADR
- `/CLAUDE.md` — Religious-corp status table
