# sumitsubo 墨壺 — cleanroom CAD interop + kotoba generative modeling

> ADR-2606033600 · Tier-B actor · status R0
> 墨壺 (sumitsubo) = the carpenter's ink-line marking instrument — the traditional drafting tool.

Cleanroom CAD interoperability for **kami-engine-sdk** (Vectorworks / Autodesk / AutoCAD)
covering **modeling** and **export**, plus **kotoba Pregel-LangGraph** generative + modeling
assistance. One geometry model, two runtimes (TypeScript kernel + python generative cell)
sharing an identical `ModelOp` vocabulary.

## Two surfaces

### 1. `sdk/` — `@etzhayyim/sumitsubo-cad` (TypeScript, zero-dep)

Re-export target for kami-engine-sdk: `export * from '@etzhayyim/sumitsubo-cad';`

- **Geometry model + kernel** — `Kernel`, `buildDrawing`, the neutral `ModelOp` vocabulary
  (line / polyline / rect / circle / arc / box / extrude / move / scale). Mesh-first + light
  prismatic modeling (exact B-rep solids are a non-goal, ADR N2).
- **Exporters** — `dxf` `svg` `obj` `gltf` (full) · `ifc` `step` (honest subset) ·
  `dwg` (proprietary → DXF fallback + `DWG_PROPRIETARY` advisory). `exportDrawing(d, fmt)`
  reports `fidelity: full|subset|fallback` honestly (G4).
- **Importer** — `importDxf` (LINE/CIRCLE/ARC/POINT/LWPOLYLINE/POLYLINE subset).
- **Published-API-shape adapters** — `VectorScript` (Vectorworks) and `AcadDatabase`/
  `BlockTableRecord` + `command()` (AutoCAD ObjectARX/.NET). These mirror only the **public
  call shapes**; no vendor SDK/headers/trademark code (cleanroom, G1).
- **kotoba bridge** — `drawingToDatoms` / `datomsToTxEdn` serialize to the canonical
  `:dwg.*` EAVT log (G2).

```ts
import { Kernel, exportDrawing, VectorScript } from "@etzhayyim/sumitsubo-cad";

const k = new Kernel("plan", "mm");
k.rect(0, 0, 5000, 4000, "walls");
k.extrude([[0,0],[5000,0],[5000,4000],[0,4000]], 2700, "walls");
const ifc = exportDrawing(k.drawing, "ifc"); // { fidelity: "subset", text: "ISO-10303-21;..." }

// drive the kernel from a Vectorworks-shaped script:
const vs = new VectorScript();
vs.Layer("design"); vs.Rect(0, 0, 100, 50); vs.Extrude([0,0,100,0,100,50,0,50], 10);
```

Build & test:
```bash
cd sdk && npm install && npm run build && npm test   # 15 tests
```

### 2. `py/` + `cells/` — kotoba Pregel-LangGraph cells

- `model` (langgraph) — **generative**: NL → Murakumo LLM → validated `ModelOp` plan → Datoms.
- `draft` (langgraph) — 2D drafting assistance (dimensions / constraints / layer hygiene).
- `interop` (langgraph) — Vectorworks/AutoCAD-shaped script → neutral ops (python mirror of
  the TS adapters).
- `export` (datalog) — format resolution + honest export record.
- `catalog` (datalog) — the drawing/layer/entity registry over the kotoba Datom log.

LLM is **Murakumo-only** (KotobaLLM `127.0.0.1:4000`, G3); state is **kotoba Datoms** (G2).

```bash
cd py && python3 test_agent.py   # offline heuristic planner + None host bindings
```

## Cleanroom posture (G1 / N1)

No vendor SDK headers, no decompilation, no vendored sample/trademark code. Implemented
against **public specs** (DXF group-code reference, IFC ISO 16739, STEP ISO 10303-242,
glTF 2.0, Wavefront OBJ) and the **published method shapes** of the vendor scripting APIs.
Vendor names are used nominatively (interoperability), never as endorsement. DWG (proprietary)
is never written natively.

## Gates

G1 cleanroom-invariant · G2 kotoba-EAVT-native · G3 murakumo-only · G4 open-format-fidelity-honesty ·
G5 dwg-proprietary-honesty · G6 no-server-key · G7 sourcing-honesty · G8 charter-rider ·
G9 outward-gated · G10 wellbecoming-tool. See `manifest.edn` for the full rules.

## Honest R0

Exporters are real but subset (DXF R12 / IFC4 tessellation / STEP point-set); DWG is
fallback-only; the kami WASM-kernel binding is by op-list (the cells emit ops; wiring them
into the live `kami-app-cad` WASM is follow-up); adapters cover the most common published
call shapes, not the full vendor API surface.
