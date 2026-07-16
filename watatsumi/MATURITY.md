# watatsumi 綿津見 — Maturity

**Stage: R0** (scaffold) — ADR-2605252200. Civilian submersible manufacturing (≤6500 m;
Funamori's submerged counter-form) + cable-laying robotics fleet (watatsuna operational arm).
Civilian only — naval weapons / nuclear propulsion / cable sabotage structurally excluded.

| Dimension | State |
|---|---|
| Lexicons | ✅ 8 under `com.etzhayyim.watatsumi.*` (pressureHull/sectionAssembly/weldInspection/systemIntegration/sectionJoining/pressureTest/seaTrial/silenSubmersibleReview) |
| Cells | ✅ 9 path-reserved; class_certification_binder now parses (syntax bug fixed, below) |
| Manifest | ✅ `manifest.jsonld` — `constitutionalGates` (G1–G14) machine-readable |
| Tests | ✅ `70-tools/scripts/audit/test_watatsumi_invariants.py` — **18 passed** (charter invariants, pre-existing) + `py/test_agent.py` |
| Methods | 🟡 cells R0 (`.solve()` Council-gated) |

## Bug fixed 2026-06-17

`cells/class_certification_binder/{state_machine,cell}.py` carried the bad-rename **broken
Python identifier** (`kotoba-datomicAnchor` / `transition_to_kotoba-datomic_anchored`, hyphen =
`SyntaxError: illegal target for annotation`). Fixed the identifier sites (field / function def
/ attribute accesses + cell.py import + call) → underscore form; the record-key string
`"kotoba-datomicAnchor"` was kept. Both files `ast.parse` OK; the watatsumi invariants suite
(18 tests) still green.

This was the **last of the 4-actor cluster** of the same bad-rename bug — sarutahiko (tick 19),
yamabiko (tick 20), kanayama + watatsumi (tick 21) all now parse. The bug came from a global
`kotoba_datomic` → `kotoba-datomic` rename that mangled Python identifiers in the `*_binder`
terminal cells; the lexicon-facing record key (which legitimately uses the hyphen) was preserved.

## Charter coverage

Charter invariants are pinned by `test_watatsumi_invariants.py` (G1 open CAD, G2 kotoba anchor,
G4 witness quorum, G8 sonar ≤180 dB cetacean, G12 depth/crew/time caps, G13 non-nuclear
propulsion, N1/N8 naval-weapons + cable-sabotage exclusion). No duplicate charter-gate test
added (avoids redundancy with the existing 70-tools suite).

## R0 → R1 gate

silenSubmersibleReview + Council Lv6+ + marine-surveyor SME; cell `.solve()` stays R0-gated.
