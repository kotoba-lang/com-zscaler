# mitsuho 瑞穂 — Maturity

**Stage: R0** (scaffold) — food / agriculture actor (plant + aquaculture + alt-protein; L2
Sustenance). The harvest side of suki's tractors. Seed sovereignty, soil regeneration,
no synthetic pesticides, GMO Council-gated, no animal slaughter in R0–R3.

| Dimension | State |
|---|---|
| Lexicons | ✅ 5 under `com.etzhayyim.mitsuho.*` (parcel / cropPlan / harvest / foodLot / silenAgricultureReview) |
| Cells | 🟡 path-reserved (R0) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) + `nonGoals` (N1–N10) machine-readable |
| Tests | ✅ **cljc-only** — `methods/test_charter_gates.cljc` (**8**: gate set + seed sovereignty + pesticide/GMO hooks + soil-carbon + witness/agronomist + biodiversity/LANDS + non-chemical preservation + N1 R4-gate) **+** `methods/test_agent.cljc` (11, agent layer) **+** `cells/test_cells.cljc` (6 R0 cell scaffolds raise); `./run_tests.sh` aggregates all three. Fully ported py→cljc (ADR-2606160842); legacy `py/` pruned. |
| Methods | ✅ clj-native `methods/agent.cljc`; offline agronomy engine = R1 |

## Charter gates pinned by the new charter-gate test

- **Full gate set** — manifest declares exactly G1–G14.
- **G2 seed sovereignty** — `cropPlanAttestation` requires `seedSourceAttestation` + `varietalManifest`.
- **G6/G7 hooks** — `cropPlanAttestation` carries `pesticideManifest` (synthetic-pesticide screen)
  + `gmoAttestationCid` (Council-gated GMO).
- **G4 soil regeneration** — `harvestAttestation` requires `soilCarbonDeltaTonsCo2Eq` +
  `yieldKgDryMatter` + `photoCid` + `cropPlanAttestationCid` (negative carbon delta → halt).
- **witness + agronomist** — harvest requires `attestingRobots`; crop plan requires `attestingAgronomistDid`.
- **parcel** — `parcelAttestation` requires `biodiversityNoHarmAttestationCid` + `landsRegistryCid`.
- **non-chemical preservation** — `foodLotAttestation.preservationMethod` is exactly
  {dried, canned, lacto-fermented, cold-stored, vacuum-sealed, freeze-dried}.
- **N1 animal product** — `silenAgricultureReview.scope` carries `n1-animal-product-r4-gate`
  (animal slaughter is an explicit R4 Council gate, not R0–R3).

## R0 → R1 gate

silenAgricultureReview `r1-benchtop-activation` + Council; cell `.solve()` stays R0-gated.
G6 pesticide blocklist (neonicotinoid/glyphosate/paraquat/organochlorine) + G4 soil-carbon
halt threshold enforced in the R1 cell logic.
