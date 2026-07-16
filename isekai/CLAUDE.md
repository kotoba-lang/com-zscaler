# etzhayyim-project-isekai — isekai.etzhayyim.com

**ISEKAI** — Minecraft voxel sandbox + Pokoa creature collection + Brainrot meme chaos。KAMI Engine WebGPU で異世界転生オープンワールド。Guest Mode でログイン不要プレイ。

## Architecture

| 項目 | 値 |
|---|---|
| Engine | **KAMI Engine** (`40-engine/kami-engine/`) — wgpu + hecs + Rapier 3D + KNP |
| Domain | `isekai.etzhayyim.com` (vanity), `is3k41w0.etzhayyim.com` (nanoid) |
| Runtime | Single Worker (TS Native + host-sdk) |
| UI | `game` mode — yoro profile hero 9:16 iframe |
| WIT | `etzhayyim:isekai@1.0.0` (`wit/isekai/package.wit`) — 5 interfaces |
| Persistence | W Protocol Event Stream (AT Record) + KNP real-time (non-persistent) |
| Scene | JSON-LD (`scenes/*.jsonld`) — procedural biome islands |

## Guest Mode

| Feature | Guest | Logged In |
|---|---|---|
| Explore overworld | Yes | Yes |
| Mine/craft voxel blocks | Yes | Yes |
| Catch wild Pokoa | Yes | Yes |
| Brainrot events | Yes | Yes |
| Save world | Ephemeral | Permanent |
| Trade Pokoa | No | Yes |
| Leaderboard | No | Yes |

## Game Design — 3 Pillars

### Pillar 1: Minecraft Voxel Sandbox (kami-voxel + kami-mesher)

- Procedural overworld: 6 biomes (plains, forest, desert, tundra, nether, skibidi-dimension)
- 16^3 chunk system (Sparse voxel → greedy mesh)
- Block types: dirt, stone, wood, ore, crystal, brainrot-block (animated)
- Mining + crafting + building
- Day/night cycle → wild Pokoa spawn rates change

### Pillar 2: Pokoa Creature Collection (etzhayyim:kami/pokoa)

- 151 base species + 6 Brainrot legendaries
- Type system: 18 standard types + `brainrot` hidden type
- Wild encounters in tall grass / caves / water
- Turn-based battle: 4 moves per Pokoa, type effectiveness, catch mechanics
- Evolution: level-based + item-based + friendship-based
- Pokoa follow player in overworld (party lead visible)

### Pillar 3: Brainrot Meme Chaos

- 6 Brainrot NPCs roam the overworld as random events
  - **Skibidi Toilet** — ambush encounter, spawns in bathrooms/caves
  - **Sigma Male** — appears at mountain peaks, philosophical dialogue
  - **Ohio Boss** — raid boss, 100 HP, drops rare loot
  - **Grimace Shake** — heals all Pokoa but causes `confusion` status
  - **Rizz Master** — charm NPC, gives friendship evolution items
  - **Fanum Tax** — steals random item from inventory, must chase to recover
- Brainrot Dimension: portal opened by collecting 6 Brainrot Shards
- Brainrot legendary Pokoa: catchable only in Brainrot Dimension

### Brainrot Legendary Pokoa

| Species | Type | Ability | Catch Location |
|---|---|---|---|
| Skibidion | Water/Brainrot | Toilet Flush (AOE water) | Skibidi Sewers |
| Sigmalord | Psychic/Brainrot | Sigma Stare (paralyze) | Sigma Summit |
| Ohiodon | Dark/Brainrot | Ohio Final Boss (1-hit KO chance) | Ohio Wasteland |
| Grimaceon | Poison/Fairy | Shake Heal (full team heal, self confuse) | Grimace Cave |
| Rizzler | Fairy/Brainrot | Rizz Charm (force switch) | Rizz Palace |
| Fanumoth | Ghost/Brainrot | Tax Steal (steal held item) | Fanum Market |

## W Protocol Lexicon

| Kind (W Protocol) | AT Collection NSID | WIT Source | 永続化 |
|---|---|---|---|
| `isekai.worldState` | `com.etzhayyim.isekai.worldState` | `etzhayyim:isekai/open-world` | AT Record |
| `isekai.chunkData` | `com.etzhayyim.isekai.chunkData` | `etzhayyim:isekai/voxel-terrain` | AT Record |
| `isekai.craftRecipe` | `com.etzhayyim.isekai.craftRecipe` | `etzhayyim:isekai/crafting` | AT Record |
| `isekai.brainrotEvent` | `com.etzhayyim.isekai.brainrotEvent` | `etzhayyim:isekai/brainrot-event` | AT Record |
| `isekai.creatureRoster` | `com.etzhayyim.isekai.creatureRoster` | `etzhayyim:isekai/creature` | AT Record |
| `isekai.game.battle` | `com.etzhayyim.isekai.game.battle` | `etzhayyim:kami/pokoa` | AT Record (analytics) |
| `isekai.game.capture` | `com.etzhayyim.isekai.game.capture` | `etzhayyim:kami/pokoa` | AT Record (analytics) |
| `isekai.game.craft` | `com.etzhayyim.isekai.game.craft` | `etzhayyim:isekai/crafting` | AT Record (analytics) |
| `isekai.game.brainrotEncounter` | `com.etzhayyim.isekai.game.brainrotEncounter` | `etzhayyim:isekai/brainrot-event` | AT Record (analytics) |
| `isekai.complianceDep` | `com.etzhayyim.isekai.complianceDep` | compliance graph | AT Record |

**非永続 (KNP real-time):** player position, voxel edit stream, battle animation state, brainrot NPC movement

## Compliance — Patent/Lawsuit Dependency Graph

**Microsoft/Mojang (Minecraft) + Nintendo/TPC (Pokémon) 特許・訴訟・trade dress を SQL ノードで管理。各ノードに mitigation 戦略を記録。**

| ID | Type | Risk | Mitigation |
|---|---|---|---|
| US10232272B2 | Patent (procedural gen) | Medium | Value noise (not Perlin), fixed chunk grid |
| US9956475B2 | Patent (block edit) | High | DDA raycast (public domain 1987), greedy mesh (MIT 2012) |
| US20190329143A1 | Patent (creature capture) | Medium | Original type system (brainrot type), crafted balls |
| US10549210B2 | Patent (multiplayer sync) | Low | KNP protocol, W Protocol AT Records |
| JP6789012B2 | Patent (block UI) | Low | Standard FPS crosshair (prior art: Infiniminer 2009) |
| Nintendo v Palworld | Lawsuit | — | Original species, crafted balls, no throwing animation |
| Minecraft trade dress | Trade dress | — | PBR rendering, SDF characters, brainrot aesthetic |

**登録**: `com.etzhayyim.isekai.registerCompliance` コマンドで全ノードを graph に投入
**照会**: `com.etzhayyim.isekai.getCompliance` で risk level フィルタ可能

## KAMI Engine Integration

### Rust WASM (kami-game crate)

```rust
// 40-engine/kami-engine/kami-game/src/isekai.rs
pub struct IsekaiGame {
    world: VoxelWorld,          // kami-voxel Sparse chunks
    pokoa_system: PokoaSystem,  // creature roster + battle state
    brainrot: BrainrotEvents,   // NPC spawn + event timer
    player: PlayerState,        // position, inventory, party
    time_of_day: f32,           // 0.0–1.0 day cycle
}

impl IsekaiGame {
    pub fn new(scene: &IslandScene) -> Self { /* procedural world gen */ }
    pub fn update(&mut self, input: &InputState, dt: f32) { /* tick all systems */ }
    pub fn entities(&self) -> Vec<EntityUpdate> { /* ECS → render bridge */ }
}
```

### Scene Format

```json
{
  "@context": "https://etzhayyim.com/ns/kami/scene",
  "@type": "IslandScene",
  "@id": "kami:island/isekai-overworld-v1",
  "genre": "sandbox",
  "camera_mode": "first-person",
  "world_seed": 42,
  "biomes": ["plains", "forest", "desert", "tundra", "nether", "skibidi-dimension"],
  "spawn_point": [0, 64, 0],
  "pokoa_spawn_table": { "plains": ["grass-types"], "forest": ["bug-types"], "cave": ["rock-types"] },
  "brainrot_npc_spawns": ["skibidi", "sigma", "ohio", "grimace", "rizz", "fanum"]
}
```

## LOD System (Level of Detail)

**Distance-based voxel LOD + SDF character resolution scaling。kami-engine-sdk に TypeScript 型定義。**

| LOD | 距離 (blocks) | Voxel Mesh | SDF Resolution | 頂点数/chunk |
|---|---|---|---|---|
| **LOD 0** | 0–32 | Full greedy mesh (16^3) | 32 | ~800-2000 |
| **LOD 1** | 32–64 | 2×2×2 down-sample → 8^3 greedy | 16 | ~100-400 |
| **LOD 2** | 64–128 | 4×4×4 down-sample → 4^3 greedy | 8 | ~20-80 |
| **LOD 3** | 128+ | Single dominant-color cube | — | 24 |

- **更新間隔**: 10 フレーム毎に全チャンクの距離を再評価
- **Block edit 時**: 該当チャンクを即座に LOD 0 に昇格、remesh
- **頂点削減**: 全チャンク LOD 0 (~50K-100K verts) → LOD 適用 (~11K-22K verts, **75-80% 削減**)

### SDK 型定義

`40-engine/kami-engine/kami-engine-sdk/src/lib/types/engine.ts`:
- `VoxelLodConfig` — thresholds, updateInterval, forceLod
- `SdfLodConfig` — thresholds, baseResolution
- `LodConfig` — combined
- `BlockType` enum, `ChunkCoord`, `PlayerPhysicsState`, `SkyState`, `PerfMetrics`, `IsekaiGameState`

## Build & Deploy

```bash
# Game logic (Rust WASM)
cd 40-engine/kami-engine
cargo build -p kami-game
wasm-pack build kami-web --target web

# Domain agent (TS Native)
cd 60-apps/etzhayyim-project-isekai/wasm/etzhayyim-wasm-isekai-is3k41w0
etzhayyim build
etzhayyim deploy

# Scene edit (no build needed)
# Edit scenes/isekai-overworld.jsonld → browser reload
```
