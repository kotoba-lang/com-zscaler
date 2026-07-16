# sumitsubo 墨壺 — actor instructions

ADR-2606033600. Cleanroom CAD interop (Vectorworks/Autodesk/AutoCAD) for kami-engine-sdk +
kotoba Pregel-LangGraph generative/modeling assistance.

## Invariants (do not weaken)

- **G1 cleanroom**: never copy vendor SDK headers, decompile vendor binaries, vendor vendor
  sample code, or use vendor trademarks as endorsement. Only public format specs
  (DXF/IFC/STEP/glTF/OBJ) + published API *shapes*. Adapters translate call shapes onto the
  kami kernel — they are ideas, not vendor code.
- **G2 kotoba-EAVT-native**: all drawing/entity/layer/export state is `:dwg.*` Datoms. No
  RW/SQL/Lance as canonical (ADR-2605262130 / 2605312345).
- **G3 Murakumo-only**: the generative/assist LLM is KotobaLLM `127.0.0.1:4000`. Never add an
  external LLM client (ADR-2605215000) — `py/requirements.txt` lists none by design.
- **G4/G5 honesty**: export fidelity is reported (`full|subset|fallback`); DWG is never
  claimed native (proprietary → DXF fallback + `DWG_PROPRIETARY`).
- **G6 no-server-key**: export/publish artifacts are content-addressed + member/operator
  signed; no platform signing key (ADR-2605231525).
- **G7 sourcing**: generated geometry is `:representative` unless dimensioned from
  authoritative input.

## Shared ModelOp vocabulary (CRITICAL)

`sdk/src/geometry/types.ts` (`ModelOp`) and `py/agent.py` (`OP_SCHEMA`) MUST stay in lockstep.
The TS kernel **applies** ops; the python generative cell **emits** them. Adding/renaming an
op means editing both, plus the kotoba datom mapping in `sdk/src/kotoba/datom.ts` and
`agent._emit_datoms`.

## Layout

- `sdk/` — `@etzhayyim/sumitsubo-cad` TS module (kami-engine-sdk re-export). `npm test` = 15 tests.
- `py/` — langgraph cells. `python3 py/test_agent.py` (stdlib, offline).
- `cells/` — Pregel cell defs · `lex/` — lexicons · `kotoba/` — EAVT schema + seed + deploy.

## Tests

```bash
( cd sdk && npm install && npm run typecheck && npm test )   # TS: tsc clean + 15 vitest
python3 py/test_agent.py                                     # py: all checks
```

## Non-goals (N1–N6)

Native DWG write (N1) · full ACIS/Parasolid B-rep parity (N2) · hosted CAD cloud (N3) · live
IPC sync with a running Vectorworks/AutoCAD instance (N4) · GIS/PLM/MES integration (N5) ·
certified IFC/STEP conformance (N6).
