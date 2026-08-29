# Create Horse Power CE — Packmaker & Modder Guide

Welcome to the **Create Horse Power — Community Edition 1.2** framework documentation. This guide details how to customize worker stats, path properties, items, redstone, and scripts using NeoForge Data Maps, Item/Entity Tags, Server Configs, and KubeJS events.

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Data Maps](#data-maps)
   - [Worker Stats Data Map (`createhorsepower:worker_stats`)](#worker-stats-data-map)
   - [Path Stats Data Map (`createhorsepower:path_stats`)](#path-stats-data-map)
3. [Tags](#tags)
   - [Item Tags](#item-tags)
   - [Entity Tags](#entity-tags)
4. [KubeJS Integration](#kubejs-integration)
   - [Startup Profile Registration](#startup-profile-registration)
   - [Server Lifecycle Events](#server-lifecycle-events)
5. [Server Configuration](#server-configuration)
6. [Precedence Rules](#precedence-rules)
7. [In-Game Diagnostics & Commands](#in-game-diagnostics--commands)
8. [Migration from 1.1](#migration-from-11)

---

## Architecture Overview

Create Horse Power CE 1.2 turns the Horse Crank into a fully data-driven animal power framework.

```
Data Layer:
  KubeJS Profiles → NeoForge Data Maps → Built-in Tier Tags → Legacy Config Lists

Execution Lifecycle:
  Leash Bound (#createhorsepower:attachment_items)
       ↓
  Worker Resolution & Validation (Alive, Species, Baby rules, Tamed rules, Undead rules)
       ↓
  Path Scanning (Multi-mode: Weighted Average, Worst Block, Legacy)
       ↓
  Redstone Evaluation (Ignore, High Stops, High Runs)
       ↓
  beforeWorkStart (KubeJS / NeoForge Event)
       ↓
  Kinetic Rotation & Stress Generation + Orbit Tracking
```

---

## Data Maps

### Worker Stats Data Map
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
      "requires_tamed": true,
      "allow_baby": false
    }
  }
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `rpm` | Float ($\ge 0$) | `4.0` | Base generation speed in RPM. |
| `stress` | Float ($\ge 0$) | `256.0` | Base stress capacity in Stress Units (SU). |
| `movement_radius` | Float ($[0.5, 32.0]$) | `2.5` | Orbital radius (in blocks) around crank center. |
| `speed_scaling` | Float ($\ge 0$) | `0.0` | Scaling weight for `generic.movement_speed` attribute. |
| `speed_reference` | Float ($> 0$) | `0.225` | Species benchmark movement speed. |
| `health_scaling` | Float ($\ge 0$) | `0.0` | Scaling weight for `generic.max_health` attribute. |
| `health_reference` | Float ($> 0$) | `20.0` | Species benchmark max health. |
| `requires_tamed` | Boolean | `false` | If true, untamed animals cannot generate power. |
| `allow_baby` | Boolean | `false` | If true, baby animals can be attached and generate power. |

---

### Path Stats Data Map
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

### Item Tags
- `#createhorsepower:attachment_items`: Root tag of items allowed to attach animals to the crank.
  - `#createhorsepower:worker_leashes`: Default member tag containing `minecraft:lead`.

#### Requiring a Custom Harness (Replacing Vanilla Lead)
Tags in NeoForge merge additively by default. If your modpack wants to **prohibit vanilla leads** and require a custom harness, set `"replace": true` in `data/createhorsepower/tags/item/attachment_items.json`:

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

---

## KubeJS Integration

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
```

---

## Server Configuration
Located at `saves/<world>/serverconfig/createhorsepower-server.toml`:

```toml
[balance]
    globalRpmMultiplier = 1.0
    globalStressMultiplier = 1.0
    enableIndividualAnimalStats = true

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

### Worker Stats Resolution:
1. **KubeJS Startup Profile** (`HorsePowerEvents.workerProfiles`)
2. **NeoForge Data Map** (`createhorsepower:worker_stats`)
3. **Tier Entity Tag** (`#createhorsepower:workers/*`)
4. **Config Entity List** (`largeCreatures`, `mediumCreatures`, `smallCreatures`)

### Path Stats Resolution:
1. **KubeJS Startup Profile** (`HorsePowerEvents.pathProfiles`)
2. **NeoForge Data Map** (`createhorsepower:path_stats`)
3. **Config Block List** (`greatPathBlock`, `normalPathBlock`, `poorPathBlock`)

---

## In-Game Diagnostics & Commands

- **Engineer's Goggles**: Look at any Horse Crank to view worker name, status, path efficiency, individual bonuses, and active redstone mode.
- **Wrench Interaction**: Sneak + Right-Click with Create Wrench cycles Redstone Mode (`HIGH_STOPS` $\to$ `HIGH_RUNS` $\to$ `IGNORE`).
- **Commands**:
  - `/createhorsepower inspect` — Inspect the targeted horse crank.
  - `/createhorsepower worker <entity_type>` — Query effective worker stats for an entity type.
  - `/createhorsepower path <block>` — Query speed and stress multipliers for a path block.
