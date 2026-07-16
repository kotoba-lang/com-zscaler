# funamori cells — kotoba-native Pregel cell specs (R0)

Declarative EDN cell specs (sanae pattern) — each is a Pregel state-graph over the `.cljc`
methods. **R0 is the spec + gates**; `.solve()` is R1 (Council + ≥1 membrane-chemist + mizuho R2
attested site). No Python/langgraph stack — the compute lives in `../methods/*.cljc`.

| Cell | Glyph | Method | Gates | Does |
|---|---|---|---|---|
| `site_qualification` | 河口 | salinity_gradient | G4 G7 | Δsalinity ≥30 g/L + mizuho attestation → site permitted + PRO/RED selection |
| `membrane_attestation` | 膜 | salinity_gradient | G1 G2 G3 | open-publication/in-house only; Toray/…/PFAS refused → formula CID |
| `power_characterization` | 出力 | plant | G5 G6 | ≥1 W/m² R3 gate → tidal capacity factor + smoothed hikari delivery |
| `stack_service` | 舫 | stack_robotics | G11 G13 G14 G15 | anti-fouling sweep + module swap, dry-run/no-server-key/witness-quorum |

Nodes: `zebulun` / `asher` (sea-tribe naming, Gen 49:13 — "Zebulun shall dwell at the haven of
the sea"). Murakumo-only inference (gemma3:4b @127.0.0.1:4000).

## Invariants (pinned by `test_cells.cljc`, babashka)

Each spec must: have all required keys · `entry ∈ nodes` · every edge endpoint declared (or `:end`)
· reach `:end` · non-empty gates · Murakumo-only LLM · `:cell/method ∈` manifest methods · every
read/written datom declared in `kotoba/schema.edn`. Run via `../run_tests.sh` (7 tests / 109 assertions).
