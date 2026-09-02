# Create Horse Power CE — Packmaker & Modder Guide

Welcome to the **Create Horse Power — Community Edition 1.2** framework documentation. This guide covers worker stats, path properties, attachment items, redstone, diagnostics, compatibility defaults, and scripting on both supported loaders.

> **Loader availability**
>
> Core Horse Crank gameplay, tags, server configuration, Jade, commands,
> diagnostics, durable leash recovery, visual-gait controls, and optional
> TerraFirmaCraft defaults are supported on both NeoForge 1.21.1 and Forge 1.20.1.
>
> NeoForge Data Maps, KubeJS integration, and Ponder scenes are NeoForge
> 1.21.1-only and are not available on Forge 1.20.1.

If a section is not labeled as "NeoForge 1.21.1 only", it applies to both loaders. Always use the loader-appropriate tag and config paths described below.

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Datapack and config file paths by loader](#datapack-and-config-file-paths-by-loader)
3. [NeoForge 1.21.1 Data Maps](#neoforge-1211-data-maps)
4. [Tags](#tags)
5. [KubeJS Integration — NeoForge 1.21.1 only](#kubejs-integration--neoforge-1211-only)
6. [Forge 1.20.1 customization](#forge-1201-customization)
7. [Optional TerraFirmaCraft compatibility](#optional-terrafirmacraft-compatibility)
8. [Server Configuration](#server-configuration)
9. [Precedence Rules](#precedence-rules)
10. [In-Game Diagnostics & Commands](#in-game-diagnostics--commands)
11. [Migration from 1.1](#migration-from-11)

---

## Architecture Overview

Create Horse Power CE 1.2 turns the Horse Crank into a data-driven animal power framework.

```text
Execution Lifecycle:
  Leash Bound (#createhorsepower:attachment_items)
        ↓
  Worker Resolution & Validation (Alive, Species, Baby rules, Tamed rules, Undead rules)
        ↓
  Path Scanning (Weighted Average, Worst Block, Legacy)
        ↓
  Redstone Evaluation (Ignore, High Stops, High Runs)
        ↓
  beforeWorkStart (optional KubeJS hook on NeoForge)
        ↓
  Mechanical RPM / Stress Generation
        +
  Server-authoritative visual orbit gait (independent speed budget)
```

Worker/path resolution differs by loader; see [Precedence Rules](#precedence-rules). Since 1.2.1, visible gait is deliberately independent from generated RPM: path/output multipliers can increase mechanical output without making animals sprint unrealistically fast.

---

## Datapack and config file paths by loader

The two Minecraft versions use different tag-directory pluralization.

| Resource | NeoForge 1.21.1 | Forge 1.20.1 |
|---|---|---|
| Item tags root | `data/<namespace>/tags/item/` | `data/<namespace>/tags/items/` |
| Entity type tags root | `data/<namespace>/tags/entity_type/` | `data/<namespace>/tags/entity_types/` |
| Block tags root | `data/<namespace>/tags/block/` | `data/<namespace>/tags/blocks/` |
| Data Maps directory | `data/<namespace>/data_maps/entity_type/`, `data/<namespace>/data_maps/block/` | _Not available_ |
| Server config | `saves/<world>/serverconfig/createhorsepower-server.toml` | same |
| KubeJS startup scripts | `kubejs/startup_scripts/` | _Not available_ |
| KubeJS server scripts | `kubejs/server_scripts/` | _Not available_ |

Copying a NeoForge datapack into Forge 1.20.1 without renaming `tags/item` → `tags/items` (and similar registry folders) leaves those tags unread by Forge.

---

## NeoForge 1.21.1 Data Maps

> **NeoForge 1.21.1 only.** Forge 1.20.1 does not expose the NeoForge Data Map API. Use tags/config and Forge's built-in profile layer there instead.

### Worker Stats Data Map (`createhorsepower:worker_stats`)

- **Path:** `data/<namespace>/data_maps/entity_type/worker_stats.json`
- **Target registry:** `minecraft:entity_type`

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
| `rpm` | Float (>= 0) | `4.0` | Base mechanical generation speed in RPM. |
| `stress` | Float (>= 0) | `256.0` | Base stress capacity in SU. |
| `movement_radius` | Float `[0.5, 6.0]` | `2.5` | Orbital radius. The cap keeps workers within normal lead mechanics. |
| `speed_scaling` | Float (>= 0) | `0.0` | Mechanical-output scaling weight for `generic.movement_speed`. |
| `speed_reference` | Float (> 0) | `0.225` | Species movement-speed benchmark. |
| `health_scaling` | Float (>= 0) | `0.0` | Mechanical-output scaling weight for `generic.max_health`. |
| `health_reference` | Float (> 0) | `20.0` | Species max-health benchmark. |
| `requires_tamed` | Boolean | `false` | Require a tame worker. |
| `allow_baby` | Boolean | `false` | Allow baby workers. |

`speed_scaling` affects mechanical output only. The 1.2.1 visual gait uses the separate server settings under `[workers]` documented below.

### Path Stats Data Map (`createhorsepower:path_stats`)

- **Path:** `data/<namespace>/data_maps/block/path_stats.json`
- **Target registry:** `minecraft:block`

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
| `speed_multiplier` | Float (>= 0) | `1.0` | Multiplier applied to mechanical RPM. |
| `stress_multiplier` | Float (>= 0) | `1.0` | Multiplier applied to stress capacity. |

---

## Tags

Tag directory layout is loader-dependent; see the [paths table](#datapack-and-config-file-paths-by-loader).

### Item Tags

- `#createhorsepower:attachment_items`: root tag of items allowed to attach workers.
- `#createhorsepower:worker_leashes`: default member tag containing `minecraft:lead`.

To replace vanilla leads entirely:

```json
{
  "replace": true,
  "values": ["firstworks:rope_harness"]
}
```

To add another attachment item while preserving defaults, omit `replace`:

```json
{
  "values": ["some_mod:custom_leash"]
}
```

### Entity Tags

- `#createhorsepower:workers/small`
- `#createhorsepower:workers/medium`
- `#createhorsepower:workers/large`

These tier tags are available on both loaders and are fallbacks after higher-priority exact profiles.

---

## KubeJS Integration — NeoForge 1.21.1 only

> Forge 1.20.1 does not register CHP KubeJS profile or lifecycle events.

### Startup Profile Registration

Use `kubejs/startup_scripts/horsepower.js`:

```javascript
HorsePowerEvents.workerProfiles(event => {
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
    event.add('create:industrial_iron_block', {
        speedMultiplier: 1.30,
        stressMultiplier: 1.15
    })

    event.addTag('c:concrete', {
        speedMultiplier: 1.20,
        stressMultiplier: 1.10
    })
})
```

### Server Lifecycle Events

Use `kubejs/server_scripts/horsepower.js`:

```javascript
HorsePowerEvents.beforeAttach(event => {
    if (event.worker.type === 'minecraft:wolf' && !event.player.isCreative()) {
        event.cancel()
    }
})

HorsePowerEvents.beforeWorkStart(event => {
    if (event.level.isRaining()) {
        event.cancel()
    }
})

HorsePowerEvents.workStarted(event => {
    console.log(`Crank started at ${event.crankPos}`)
})

HorsePowerEvents.workStopped(event => {
    console.log(`Crank stopped at ${event.crankPos}`)
})

HorsePowerEvents.outputCalculated(event => {
    if (event.level.dimension === 'minecraft:the_nether') {
        event.setStressMultiplier(1.5)
    }
})

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

KubeJS is optional. Without it, NeoForge Data Maps, tags, config, attachment, movement, and generation continue normally.

---

## Forge 1.20.1 customization

> **Forge 1.20.1 only.** NeoForge Data Maps and CHP KubeJS registration are unavailable.

Forge uses a deterministic fallback chain:

1. Built-in per-species profiles (matching the canonical NeoForge defaults where applicable).
2. Canonical worker tier tags.
3. Legacy worker tags/config fallback for additional workers (`smallCreatures`, `mediumCreatures`, `largeCreatures`).
4. Built-in path profiles, then legacy path/config fallback for additional blocks.

There is no arbitrary per-entity override API comparable to NeoForge Data Maps/KubeJS on Forge 1.20.1. Built-in exact profiles take priority over legacy tag/config tuning for the same built-in entity or block.

---

## Optional TerraFirmaCraft compatibility

TerraFirmaCraft is **not** a published hard dependency. CE's compatibility is registry-ID and data driven.

### Workers

Version 1.2.1 includes built-in TFC defaults for these IDs when they exist:

- `tfc:horse`
- `tfc:donkey`
- `tfc:mule`
- `tfc:cow`
- `tfc:pig`
- `tfc:sheep`
- `tfc:dromedary_camel`
- `tfc:bactrian_camel`

On NeoForge 1.21.1, exact TFC Data Map entries are guarded by `neoforge:mod_loaded` and use `replace=true`, so they replace generic tier-tag defaults. A later datapack may still override or remove those entries normally. The shared registry-ID fallback also exists as a defensive cross-loader fallback.

On Forge 1.20.1, the shared registry-ID fallback supplies the matching built-in species profile for IDs present in the installed TFC version. IDs absent from that TFC version simply do nothing.

### Path families

Without any pack config, CE recognizes common TFC terrain families:

| TFC registry path family | CHP default |
|---|---|
| `tfc:grass/*`, `tfc:dirt/*`, `tfc:clay_grass/*`, `tfc:clay/*` | Dirt-like |
| `tfc:rock/gravel/*` | Gravel-like |
| `tfc:rock/cobble/*`, `tfc:rock/mossy_cobble/*` | Normal |
| `tfc:rock/smooth/*`, `tfc:rock/bricks/*`, `tfc:rock/mossy_bricks/*` | Great |

These defaults are intended to make ordinary TFC walking rings work out of the box. Higher-priority NeoForge Data Maps/KubeJS profiles can override them where available.

---

## Server Configuration

Located at `saves/<world>/serverconfig/createhorsepower-server.toml`. The schema is identical on both loaders.

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

    # 1.2.1 visual gait controls. These do not change generated RPM.
    workerGroundSpeedScale = 10.0
    minWorkerGroundSpeed = 0.8
    maxWorkerGroundSpeed = 3.5

[diagnostics]
    # Transition-oriented field diagnostics; off by default.
    debugLogging = false

[path]
    # Options: WEIGHTED_AVERAGE, WORST_BLOCK, LEGACY
    evaluationMode = "WEIGHTED_AVERAGE"
    minimumCoverage = 1.0
    checkIntervalTicks = 40

[automation]
    # Options: HIGH_STOPS, HIGH_RUNS, IGNORE
    defaultRedstoneMode = "HIGH_STOPS"
```

### Visual gait settings

`workerGroundSpeedScale` converts the mob's movement-speed attribute into a visual ground speed in blocks/second. The result is clamped between `minWorkerGroundSpeed` and `maxWorkerGroundSpeed`; angular movement is then derived as `linearSpeed / radius`, so workers at different configured radii retain the same ground speed.

These settings affect presentation/movement only. `rpm`, `speed_scaling`, path `speed_multiplier`, and KubeJS output changes continue to control mechanical output independently.

### Debug logging

`diagnostics.debugLogging = true` enables transition-oriented diagnostics for attachment/rejection, detach/leash cleanup, orphan recovery, AI-control ownership, work state, and path-state changes. It intentionally does **not** log normal orbit movement every tick.

Persistent retry states are rate limited: repeated `beforeWorkStart` veto diagnostics and deferred-recovery reminders emit immediately, then at most once per 1200 ticks per affected crank/worker state. The normal functional retry/recovery logic still runs at its normal cadence.

---

## Precedence Rules

Higher-priority entries replace lower-priority tuning for the same entity or block.

### NeoForge 1.21.1

Worker stats:

1. **KubeJS startup profile** (`HorsePowerEvents.workerProfiles`)
2. **NeoForge Data Map** (`createhorsepower:worker_stats`)
3. **Built-in exact registry-ID fallback** (including optional TFC species)
4. **Tier entity tag** (`#createhorsepower:workers/*`)
5. **Config entity list** (`largeCreatures`, `mediumCreatures`, `smallCreatures`)

Path stats:

**KubeJS → Data Map → built-in exact/family fallback → legacy tier/config fallback**.

The built-in TFC exact Data Map entries are conditional and `replace=true`, so when TFC is loaded they beat generic tier-tag defaults but remain overrideable by later datapacks/KubeJS.

### Forge 1.20.1

KubeJS and NeoForge Data Maps are unavailable:

1. **Built-in per-species / registry-ID profile**
2. **Tier entity tag**
3. **Legacy tag/config fallback**

For paths: **built-in exact/family profile → legacy path/config fallback** (`greatPathBlock`, `normalPathBlock`, `poorPathBlock`).

Built-in Forge profiles take priority over legacy tag/config values for the same built-in entry. Legacy config remains useful for otherwise unresolved workers/blocks.

---

## In-Game Diagnostics & Commands

These apply on both loaders.

- **Engineer's Goggles:** worker name/status, path efficiency, individual bonuses, and redstone mode.
- **Wrench:** sneak-use a Create wrench to cycle `HIGH_STOPS` → `HIGH_RUNS` → `IGNORE`.
- **`/createhorsepower inspect`:** targeted crank state, mechanical RPM, visual gait blocks/second + radius, worker UUID/type, leash holder, attachment/AI marker state, and recovery anchor/chunk status when available.
- **`/createhorsepower worker <entity_type>`:** query effective worker stats.
- **`/createhorsepower path <block>`:** query effective path speed/stress multipliers.

For live incident debugging, enable `diagnostics.debugLogging`, reproduce the attach/detach/recovery sequence, and pair the resulting transition logs with `/createhorsepower inspect`. Disable it again when the incident is resolved if the extra INFO lines are no longer useful.

---

## Migration from 1.1

### 1. Server Configuration Compatibility

All 1.1 root keys (`creatureRPMRange`, `largeCreatureStressRange`, `poorPathBlock`, etc.) remain at the root of `createhorsepower-server.toml`. Existing configs load without a reset.

Version 1.2.1 adds these non-breaking defaults:

- `workers.workerGroundSpeedScale = 10.0`
- `workers.minWorkerGroundSpeed = 0.8`
- `workers.maxWorkerGroundSpeed = 3.5`
- `diagnostics.debugLogging = false`

### 2. Precedence and Data Map Overrides (NeoForge 1.21.1)

Vanilla workers now have dedicated Data Map profiles, so old tier/config values no longer override those exact built-ins. Override `createhorsepower:worker_stats` or use KubeJS for exact tuning. Built-in path Data Maps follow the same rule.

Optional TFC exact species Data Maps are also higher priority than generic tier tags when TFC is installed; they remain normal datapack entries and can be replaced/removed by a later pack.

### 2b. Forge 1.20.1 Customization

Forge emulates canonical built-in worker/path defaults without Data Maps. Use supported tags or legacy config for additional unresolved workers/blocks; exact built-ins remain higher priority than the legacy fallback.

### 3. Existing Horse Cranks and Redstone

Pre-1.2 Horse Cranks have no saved redstone mode and migrate to `IGNORE`, preserving old behavior. Newly placed 1.2 cranks use `defaultRedstoneMode`.

### 4. Movement Radius

Version 1.2 accepts radii from `0.5` through `6.0` blocks. Values above 6 are rejected because normal Minecraft leads cannot reliably support larger circles. Update any older prerelease Data Map/KubeJS profile above 6 before loading it.

### 5. 1.2.1 worker ownership and leash recovery

Version 1.2.1 persists attachment ownership separately from temporary AI suppression. If a worker is unloaded when it is detached, the detach policy is stored at level scope and recovered after vanilla restores the worker's persisted leash. Recovery is bounded, does not force-load the old crank chunk, preserves foreign/current leashes, and keeps `detachWorker(false)` no-drop behavior across save/unload/reload.
