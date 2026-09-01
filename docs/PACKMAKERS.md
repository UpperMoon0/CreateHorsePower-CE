# Create Horse Power CE — Packmaker & Modder Guide

Welcome to the **Create Horse Power — Community Edition 1.2** framework documentation. This guide details how to customize worker stats, path properties, items, redstone, and scripts on both supported mod loaders.

> **Loader availability**
>
> Core Horse Crank gameplay, tags, server configuration, Jade,
> commands, and diagnostics are supported on both NeoForge 1.21.1 and
> Forge 1.20.1.
>
> NeoForge Data Maps, KubeJS integration, and Ponder scenes are
> NeoForge 1.21.1-only and are not available on Forge 1.20.1.

If a section is not labeled as "NeoForge 1.21.1 only", its content applies to both loaders. Always use the loader-appropriate tag and config file paths described below.

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Datapack and config file paths by loader](#datapack-and-config-file-paths-by-loader)
3. [NeoForge 1.21.1 Data Maps](#neoforge-1211-data-maps)
   - [Worker Stats Data Map (`createhorsepower:worker_stats`)](#worker-stats-data-map-createhorsepowerworker_stats)
   - [Path Stats Data Map (`createhorsepower:path_stats`)](#path-stats-data-map-createhorsepowerpath_stats)
4. [Tags](#tags)
5. [KubeJS Integration — NeoForge 1.21.1 only](#kubejs-integration--neoforge-1211-only)
6. [Forge 1.20.1 customization](#forge-1201-customization)
7. [Server Configuration](#server-configuration)
8. [Precedence Rules](#precedence-rules)
9. [In-Game Diagnostics & Commands](#in-game-diagnostics--commands)
10. [Migration from 1.1](#migration-from-11)

---

## Architecture Overview

Create Horse Power CE 1.2 turns the Horse Crank into a data-driven animal power framework.

```
Execution Lifecycle:
  Leash Bound (#createhorsepower:attachment_items)
        ↓
  Worker Resolution & Validation (Alive, Species, Baby rules, Tamed rules, Undead rules)
        ↓
  Path Scanning (Multi-mode: Weighted Average, Worst Block, Legacy)
        ↓
  Redstone Evaluation (Ignore, High Stops, High Runs)
        ↓
  beforeWorkStart (optional KubeJS hook on NeoForge)
        ↓
  Kinetic Rotation & Stress Generation + Orbit Tracking
```

Worker and path stats resolution differs by loader — see [Precedence Rules](#precedence-rules).

---

## Datapack and config file paths by loader

Packmakers must use the directory layout that matches the running mod loader. The two Minecraft versions do not agree on the plural tag sub-directory names.

| Resource | NeoForge 1.21.1 | Forge 1.20.1 |
|---|---|---|
| Item tags root | `data/<namespace>/tags/item/` | `data/<namespace>/tags/items/` |
| Entity type tags root | `data/<namespace>/tags/entity_type/` | `data/<namespace>/tags/entity_types/` |
| Block tags root | `data/<namespace>/tags/block/` | `data/<namespace>/tags/blocks/` |
| Data Maps directory | `data/<namespace>/data_maps/entity_type/`, `data/<namespace>/data_maps/block/` | _Not available_ |
| Server config | `saves/<world>/serverconfig/createhorsepower-server.toml` | `saves/<world>/serverconfig/createhorsepower-server.toml` |
| KubeJS startup scripts | `kubejs/startup_scripts/` | _Not available_ |
| KubeJS server scripts | `kubejs/server_scripts/` | _Not available_ |

Copying a NeoForge datapack folder into a Forge 1.20.1 installation without renaming `tags/item` → `tags/items` (and similar) will silently leave tags unread by the Forge path.

---

## NeoForge 1.21.1 Data Maps

> **NeoForge 1.21.1 only.** Forge 1.20.1 does not provide the NeoForge Data
> Map API. Do not place these files in a Forge pack expecting CHP to read
> them — use the supported tags and config fallback described in
> [Forge 1.20.1 customization](#forge-1201-customization) instead.

### Worker Stats Data Map (`createhorsepower:worker_stats`)
- **Path**: `data/<namespace>/data_maps/entity_type/worker_stats.json`
- **Target Registry**: `minecraft:entity_type`

#### Schema
```json
{
  "values": {
    "minecraft:horse": {
      "rpm": 5.0,
      "stress": 600.0,
      "movement_radius": 2.5,
      "speed_scaling": 0.75,
      "speed_reference": 0.225,
      "health_scaling": 0.25,
      "health_reference": 22.0,
      "requires_tamed": false,
      "allow_baby": false
    }
  }
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `rpm` | Float ($\ge 0$) | `4.0` | Base generation speed in RPM. |
| `stress` | Float ($\ge 0$) | `256.0` | Base stress capacity in Stress Units (SU). |
| `movement_radius` | Float ($[0.5, 6.0]$) | `2.5` | Orbital radius in blocks. The 6-block maximum keeps workers within vanilla lead mechanics. |
| `speed_scaling` | Float ($\ge 0$) | `0.0` | Scaling weight for `generic.movement_speed` attribute. |
| `speed_reference` | Float ($> 0$) | `0.225` | Species benchmark movement speed. |
| `health_scaling` | Float ($\ge 0$) | `0.0` | Scaling weight for `generic.max_health` attribute. |
| `health_reference` | Float ($> 0$) | `20.0` | Species benchmark max health. |
| `requires_tamed` | Boolean | `false` | If true, untamed animals cannot generate power. |
| `allow_baby` | Boolean | `false` | If true, baby animals can be attached and generate power. |

### Path Stats Data Map (`createhorsepower:path_stats`)
- **Path**: `data/<namespace>/data_maps/block/path_stats.json`
- **Target Registry**: `minecraft:block`

#### Schema
```json
{
  "values": {
    "minecraft:stone_bricks": {
      "speed_multiplier": 1.25,
      "stress_multiplier": 1.10
    },
    "minecraft:dirt": {
      "speed_multiplier": 0.70,
      "stress_multiplier": 0.90
    }
  }
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `speed_multiplier` | Float ($\ge 0$) | `1.0` | Multiplier applied to crank output RPM. |
| `stress_multiplier` | Float ($\ge 0$) | `1.0` | Multiplier applied to crank stress capacity. |

---

## Tags

Tag directory layout is loader-dependent — see the [paths table](#datapack-and-config-file-paths-by-loader).

### Item Tags
- `#createhorsepower:attachment_items`: Root tag of items allowed to attach animals to the crank.
  - `#createhorsepower:worker_leashes`: Default member tag containing `minecraft:lead`.

#### Requiring a Custom Harness (Replacing Vanilla Lead)
Tags merge additively by default. To prohibit vanilla leads and require a custom harness, set `"replace": true` in the loader-appropriate attachment-items file:

```json
{
  "replace": true,
  "values": [
    "firstworks:rope_harness"
  ]
}
```

#### Adding Alternative Leashes (Additive)
To allow additional leash items alongside the vanilla lead without removing it, omit `"replace"`:

```json
{
  "values": [
    "some_mod:custom_leash"
  ]
}
```

### Entity Tags
- `#createhorsepower:workers/small` (fallback: 4 RPM, small tier stress)
- `#createhorsepower:workers/medium` (fallback: 4 RPM, medium tier stress)
- `#createhorsepower:workers/large` (fallback: 4 RPM, large tier stress)

These tier tags are honored by both loaders and act as the canonical non-Data-Map fallback on Forge 1.20.1.

---

## KubeJS Integration — NeoForge 1.21.1 only

> KubeJS event hooks are NeoForge 1.21.1-only. The Forge 1.20.1 platform
> does not register KubeJS profile or lifecycle events; use tags and
> server config to customize behavior on Forge.

### Startup Profile Registration
Register custom worker and path profiles directly in `kubejs/startup_scripts/horsepower.js`:

```javascript
HorsePowerEvents.workerProfiles(event => {
    // Add custom mob from another mod
    event.add('alexsmobs:bison', {
        rpm: 3.5,
        stress: 1000.0,
        movementRadius: 3.0,
        healthScaling: 0.4,
        healthReference: 40.0,
        requiresTamed: false
    })
})

HorsePowerEvents.pathProfiles(event => {
    // Add single block
    event.add('create:industrial_iron_block', {
        speedMultiplier: 1.30,
        stressMultiplier: 1.15
    })

    // Add entire tag of blocks
    event.addTag('c:concrete', {
        speedMultiplier: 1.20,
        stressMultiplier: 1.10
    })
})
```

### Server Lifecycle Events
Handle interaction, validation, and dynamic output adjustments in `kubejs/server_scripts/horsepower.js`:

```javascript
// Cancellable before attachment
HorsePowerEvents.beforeAttach(event => {
    // event.player, event.worker, event.crankPos, event.level, event.profile
    if (event.worker.type === 'minecraft:wolf' && !event.player.isCreative()) {
        event.cancel()
    }
})

// Cancellable before crank starts spinning
HorsePowerEvents.beforeWorkStart(event => {
    // event.worker, event.crankPos, event.level
    if (event.level.isRaining()) {
        event.cancel() // Prevents the crank from starting during rain
    }
})

// Notifications
HorsePowerEvents.workStarted(event => {
    console.log(`Crank started at ${event.crankPos}`)
})

HorsePowerEvents.workStopped(event => {
    console.log(`Crank stopped at ${event.crankPos}`)
})

// Dynamic output scaling
HorsePowerEvents.outputCalculated(event => {
    // Apply environmental bonus
    if (event.level.dimension === 'minecraft:the_nether') {
        event.setStressMultiplier(1.5)
    }
})

// Adjust the evaluated path result
HorsePowerEvents.pathEvaluated(event => {
    if (event.invalidBlocks === 0) {
        event.setSpeedMultiplier(event.speedMultiplier * 1.1)
    }
})
```

Available server events:

| Event | Cancellable | Available values / controls |
|---|---|---|
| `beforeAttach` | Yes | `player`, `worker`, `crankPos`, `level`, `profile` |
| `workerAttached` | No | `worker`, `crankPos`, `level`, `profile` |
| `workerDetached` | No | `worker` (nullable), `crankPos`, `level` |
| `beforeWorkStart` | Yes | `worker`, `crankPos`, `level` |
| `workStarted` | No | `worker`, `crankPos`, `level` |
| `workStopped` | No | `crankPos`, `level` |
| `outputCalculated` | No | `worker`, `crankPos`, `level`, `baseRpm`, `baseStress`, `setRpmMultiplier()`, `setStressMultiplier()` |
| `pathEvaluated` | No | `crankPos`, `level`, `result`, `validBlocks`, `invalidBlocks`, `efficiencyPercent`, `setSpeedMultiplier()`, `setStressMultiplier()` |

KubeJS is optional. Without it, Data Maps, tags, config, attachment, movement, and generation continue to work normally on NeoForge.

---

## Forge 1.20.1 customization

> **Forge 1.20.1 only.** NeoForge Data Maps and CHP KubeJS profile
> registration are not available on Forge 1.20.1. The Forge platform
> layer emulates the canonical CHP profile lookup using a fixed chain
> of sources instead.

Forge 1.20.1 customization follows a deterministic fallback chain (see [Precedence Rules](#precedence-rules) for details):

1. **Built-in per-species profiles** that ship with the Forge platform layer
   (same defaults as the NeoForge 1.21.1 Data Maps).
2. **Canonical worker tier tags** (`#createhorsepower:workers/small|medium|large`).
3. **Legacy worker tier tags and config fallback** for additional workers
   (`smallCreatures`, `mediumCreatures`, `largeCreatures`).
4. **Server config lists** for otherwise unresolved workers.
5. **Built-in path profiles**, followed by the **legacy path / config
   fallback** for additional blocks.

There is no per-entity override API beyond tags and config on Forge 1.20.1. If a pack needs arbitrary per-mob tuning on 1.20.1, promote that mob to a KubeJS or Data Map override on NeoForge 1.21.1.

---

## Server Configuration

Located at `saves/<world>/serverconfig/createhorsepower-server.toml`. The file is identical on both loaders.

```toml
# Legacy root keys (preserved for 1.1 backward compatibility)
creatureRPMRange = 4
smallCreatureStressRange = 128
mediumCreatureStressRange = 256
largeCreatureStressRange = 512
poorMultiplier = 0.5
normalMultiplier = 1.0
greatMultiplier = 2.0
poorPathBlock = ["minecraft:dirt", "minecraft:grass_block"]
normalPathBlock = ["minecraft:dirt_path", "minecraft:gravel"]
greatPathBlock = ["minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"]
smallCreatures = ["minecraft:wolf"]
mediumCreatures = ["minecraft:cow"]
largeCreatures = ["minecraft:horse"]

[balance]
    globalRpmMultiplier = 1.0
    globalStressMultiplier = 1.0
    enableIndividualAnimalStats = true
    minSpeedScalingClamp = 0.5
    maxSpeedScalingClamp = 2.5
    minHealthScalingClamp = 0.5
    maxHealthScalingClamp = 3.0

[workers]
    allowBabies = false
    requireTamedHorse = false
    allowUndeadWorkers = true

[path]
    # Options: WEIGHTED_AVERAGE, WORST_BLOCK, LEGACY
    evaluationMode = "WEIGHTED_AVERAGE"
    minimumCoverage = 1.0
    checkIntervalTicks = 40

[automation]
    # Options: HIGH_STOPS, HIGH_RUNS, IGNORE
    defaultRedstoneMode = "HIGH_STOPS"
```

---

## Precedence Rules

Higher-priority entries replace lower-priority tuning for the same entity or block.

### NeoForge 1.21.1

1. **KubeJS Startup Profile** (`HorsePowerEvents.workerProfiles`)
2. **NeoForge Data Map** (`createhorsepower:worker_stats`)
3. **Tier Entity Tag** (`#createhorsepower:workers/*`)
4. **Config Entity List** (`largeCreatures`, `mediumCreatures`, `smallCreatures`)

For path stats: KubeJS → Data Map → tier / config fallback.

### Forge 1.20.1

KubeJS and NeoForge Data Maps are not available. Use the canonical chain:

1. **Built-in per-species profile** (emulated by the Forge platform layer)
2. **Tier Entity Tag** (`#createhorsepower:workers/*`)
3. **Legacy tag and config fallback** (`smallCreatures`, `mediumCreatures`, `largeCreatures`, plus any user-supplied tier tags)

For path stats on Forge: built-in path profile → legacy path / config fallback (`greatPathBlock`, `normalPathBlock`, `poorPathBlock`).

Built-in Forge profiles take priority over the legacy tag/config fallback. Legacy config can add or tune otherwise unresolved blocks, but cannot override a built-in per-block path profile in the current Forge 1.20.1 implementation (so the built-in `minecraft:dirt` entry wins over `poorMultiplier`). The same limitation applies to built-in per-species worker profiles.

---

## In-Game Diagnostics & Commands

These apply identically on both loaders.

- **Engineer's Goggles**: Look at any Horse Crank to view worker name, status, path efficiency, individual bonuses, and active redstone mode.
- **Wrench Interaction**: Sneak + Right-Click with Create Wrench cycles Redstone Mode (`HIGH_STOPS` $\to$ `HIGH_RUNS` $\to$ `IGNORE`).
- **Commands**:
  - `/createhorsepower inspect` — Inspect the targeted horse crank.
  - `/createhorsepower worker <entity_type>` — Query effective worker stats for an entity type.
  - `/createhorsepower path <block>` — Query speed and stress multipliers for a path block.

---

## Migration from 1.1

### 1. Server Configuration Compatibility
- All 1.1 configuration keys (`creatureRPMRange`, `largeCreatureStressRange`, `poorPathBlock`, etc.) remain at the **root** of `createhorsepower-server.toml`.
- Existing server config files from 1.1 will load seamlessly without reset or missing properties.

### 2. Precedence and Data Map Overrides (NeoForge 1.21.1)
- In 1.1, mob stats were solely governed by the 3 config lists (`smallCreatures`, `mediumCreatures`, `largeCreatures`).
- In 1.2, vanilla mobs (horse, donkey, mule, camel, llama, wolf, cow, pig, sheep) now have dedicated **Data Map profiles** (e.g. Horse defaults to `5 RPM / 600 SU` with individual speed/health scaling).
- Because Data Maps take precedence over fallback config lists, modifying `largeCreatureStressRange` in config will only affect mobs without a Data Map entry.
- To customize vanilla mobs in 1.2, override `data/createhorsepower/data_maps/entity_type/worker_stats.json` in a datapack or use `HorsePowerEvents.workerProfiles` in KubeJS.
- Built-in path Data Maps use the same precedence. Changes to legacy path multipliers do not affect blocks such as dirt, dirt path, or gravel while those blocks have Data Map entries; override `createhorsepower:path_stats` or use a KubeJS path profile instead.

### 2b. Forge 1.20.1 Customization
- Forge 1.20.1 does not provide Data Maps. The platform emulates the canonical built-in worker and path profiles; customization for additional mobs and blocks must use the supported tags (`#createhorsepower:workers/*`) or the legacy config lists at the root of `createhorsepower-server.toml`.
- Built-in path profiles ship with the Forge platform layer; legacy `poorPathBlock` / `normalPathBlock` / `greatPathBlock` lists act as the user-facing override path for additional blocks.

### 3. Existing Horse Cranks and Redstone
- Pre-1.2 Horse Cranks have no saved redstone mode and migrate to `IGNORE`, preserving their old behavior.
- Newly placed 1.2 Horse Cranks use `defaultRedstoneMode` from the server config.

### 4. Movement Radius
- Version 1.2 accepts radii from `0.5` through `6.0` blocks. Values above 6 are rejected because normal Minecraft leads cannot reliably support larger working circles.
- On NeoForge 1.21.1, profile values outside the supported range (Data Map or KubeJS) fail validation.
- On Forge 1.20.1, saved block-entity radii are clamped safely, but profile definitions outside the supported range fail validation.
- If a pack used an earlier 1.2 prerelease with a larger radius, change its Data Map (NeoForge) or KubeJS profile (NeoForge) before updating.
