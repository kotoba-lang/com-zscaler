# 20-actors/wadachi — CLAUDE.md

## Identity
- **Name**: wadachi (轍 — wheel tracks/ruts)
- **DID**: `did:web:etzhayyim.com:wadachi`
- **ADR**: ADR-2605242000 (R0 scaffold)
- **Status**: R0 scaffold — all cells import-time RuntimeError
- **Parent actor**: etzhayyim religious-corp (autonomous mobility R&D)

## Architecture

5 Pregel cells in linear sequence:

```
route_planning → motion_control → obstacle_avoidance → safety_monitoring → telemetry_log
      (sora)        (wadachi)          (wadachi)            (wadachi)         (sora)
```

Each cell = Pregel graph with 5–7 LangGraph nodes.

## Robotics Fleet (New)

**Wadachi Class** (ground autonomous vehicle, new in Row 33):
- Payload: 100 kg (small materials, tools)
- Max speed: 5 m/s (R1 ≤1 m/s, R2 ≤8.3 m/s = 30 km/h)
- LIDAR: 50m range, 0.1° angular res
- GPS: RTK ±0.02m, 10 Hz update
- Comm: WiFi + 4G backup

**Partner robots** (existing kuni-umi classes):
- Sora: Aerial route planning + telemetry aggregation
- Hitogata: Payload loading/unloading

## Gates (G1–G12, immutable R0–R3)

See CLAUDE.md gates section — enforce via manifest.jsonld.

## Lexicon Namespace

**App lexicon root**: `com.etzhayyim.wadachi`

**Records** (3 types):

1. **`com.etzhayyim.wadachi.deliveryRoute`** — Mission input (origin, dest, obstacles, payload)
2. **`com.etzhayyim.wadachi.trajectoryPlan`** — Planned path with safety buffers
3. **`com.etzhayyim.wadachi.missionCompleteRecord`** — Telemetry + completion status

## Non-Goals (N1–N8, immutable R0–R3)

See README.md non-goals section.

## Testing (R0)

Smoke test: Verify all 5 cells import.
```bash
cd 20-actors/wadachi
python -c "from cells.route_planning import RoutePlanningCell; assert RoutePlanningCell"
# ... etc
```
