# Create Horse Power CE â€” Packmaker & Modder Guide

Welcome to the **Create Horse Power â€” Community Edition 1.2** framework documentation. This guide covers worker stats, path properties, attachment items, redstone, diagnostics, compatibility defaults, and scripting on both supported loaders.

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
3. [Shared worker profiles](#shared-worker-profiles-both-loaders)
4. [NeoForge 1.21.1 Data Maps](#neoforge-1211-data-maps)
5. [Tags and attachment profiles](#tags)
6. [KubeJS Integration â€” NeoForge 1.21.1 only](#kubejs-integration--neoforge-1211-only)
7. [Forge 1.20.1 customization](#forge-1201-customization)
8. [Optional TerraFirmaCraft compatibility](#optional-terrafirmacraft-compatibility)
9. [Server Configuration](#server-configuration)
10. [Precedence Rules](#precedence-rules)
11. [In-Game Diagnostics & Commands](#in-game-diagnostics--commands)
12. [Migration from 1.1](#migration-from-11)

---

## Architecture Overview

Create Horse Power CE 1.2 turns the Horse Crank into a data-driven animal power framework.

```text
Execution Lifecycle:
  Leash Bound (#createhorsepower:attachment_items)
        â†“
  Worker Resolution & Validation (Alive, Species, Baby rules, Tamed rules, Undead rules)
        â†“
  Path Scanning (Weighted Average, Worst Block, Legacy)
        â†“
  Redstone Evaluation (Ignore, High Stops, High Runs)
        â†“
  beforeWorkStart (optional KubeJS hook on NeoForge)
        â†“
  Mechanical RPM / Stress Generation
        +
  Server-authoritative visual orbit gait (independent speed budget)
```

Worker profiles and attachment profiles have loader-neutral datapack JSON surfaces; path resolution also supports loader-specific NeoForge Data Maps/KubeJS. See [Precedence Rules](#precedence-rules). Since 1.2.1, visible gait is deliberately independent from generated RPM: path/output multipliers can increase mechanical output without making animals sprint unrealistically fast.

---

## Datapack and config file paths by loader

The two Minecraft versions use different tag-directory pluralization.

| Resource | NeoForge 1.21.1 | Forge 1.20.1 |
|---|---|---|
| Item tags root | `data/<namespace>/tags/item/` | `data/<namespace>/tags/items/` |
| Entity type tags root | `data/<namespace>/tags/entity_type/` | `data/<namespace>/tags/entity_types/` |
| Block tags root | `data/<namespace>/tags/block/` | `data/<namespace>/tags/blocks/` |
| Data Maps directory | `data/<namespace>/data_maps/entity_type/`, `data/<namespace>/data_maps/block/` | _Not available_ |
| Worker profiles | `data/<namespace>/createhorsepower/worker_profiles/` | same |
| Attachment profiles | `data/<namespace>/createhorsepower/attachment_profiles/` | same |
| Server config | `saves/<world>/serverconfig/createhorsepower-server.toml` | same |
| KubeJS startup scripts | `kubejs/startup_scripts/` | _Not available_ |
| KubeJS server scripts | `kubejs/server_scripts/` | _Not available_ |

Copying a NeoForge datapack into Forge 1.20.1 without renaming `tags/item` â†’ `tags/items` (and similar registry folders) leaves those tags unread by Forge.

---


## Shared worker profiles (both loaders)

Forge 1.20.1 and NeoForge 1.21.1 both load exact worker profiles from:

`data/<namespace>/createhorsepower/worker_profiles/<name>.json`

```json
{
  "entity": "minecraft:horse",
  "priority": 100,
  "rpm": 5.0,
  "stress": 600.0,
  "movement_radius": 2.5,
  "speed_scaling": 0.75,
  "speed_reference": 0.225,
  "health_scaling": 0.25,
  "health_reference": 22.0,
  "requires_tamed": false,
  "allow_baby": false,
  "machines": {
    "createhorsepower:horse_crank": {
      "rpm": 7.0,
      "movement_radius": 2.0,
      "allow_baby": true
    }
  }
}
```

`entity` is required and must be a namespaced entity ID. `priority` defaults to `0`. The remaining fields use the same snake_case schema and validation as `WorkerStats` Data Maps. Machine overrides are field-by-field: omitted fields inherit the selected base profile. If several resource IDs target the same entity, higher `priority` wins; equal priority is resolved by lexicographically smaller profile resource ID. Replacing the same resource ID through normal datapack pack priority still follows Minecraft's resource-stack rules.

This shared JSON layer is the primary exact-profile/machine-override authoring surface for Forge and is also supported on NeoForge. It is intentionally separate from CE's bundled fallback defaults.

---

## NeoForge 1.21.1 Data Maps

> **NeoForge 1.21.1 only.** Forge 1.20.1 does not expose the NeoForge Data Map API. Use the shared worker-profile JSON layer for exact worker/machine profiles on Forge; tags/config remain available for tier and legacy tuning.

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
| `machines` | Object | `{}` | Exact machine-ID field overrides applied after this base profile is selected. |

Machine overrides are field-by-field. Omitted fields inherit the selected base profile. Example:

```json
"machines": {
  "createhorsepower:horse_crank": {
    "rpm": 7.0,
    "movement_radius": 2.0,
    "allow_baby": true
  }
}
```

The source profile is chosen first using the normal KubeJS/shared-JSON/Data-Map/bundled/config precedence. Only then is the exact `machines[<machine-id>]` override applied; a lower-priority source never contributes a machine override to a higher-priority selected profile.

`speed_scaling` affects mechanical output only. The 1.2.1 visual gait uses the separate server settings under `[workers]` documented below.

Starting in 1.2.2, CE no longer ships its own bundled worker defaults as Data Map values. A value present in this Data Map is therefore an explicit datapack/packmaker override and stays above legacy server balance and CE bundled fallback profiles.

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

Starting in 1.2.2, bundled CE path defaults are also resolved in common code instead of being shipped as Data Map values. Explicit datapack Data Maps remain above legacy path config and bundled defaults.

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

### Attachment profiles

Attachment behavior can be defined on **both loaders** without Java under:

`data/<namespace>/createhorsepower/attachment_profiles/<name>.json`

```json
{
  "priority": 100,
  "items": ["firstworks:rope_harness"],
  "item_tags": ["example:animal_harnesses"],
  "mode": "harness",
  "max_working_radius": 4.0,
  "workers": ["minecraft:horse"],
  "worker_tags": ["createhorsepower:workers/large"],
  "machines": ["createhorsepower:horse_crank"],
  "consume_on_attach": true,
  "drop_on_detach": true,
  "output_multiplier": 1.0
}
```

`mode` accepts `vanilla_leash`, `harness`, `yoke`, or `virtual_tether`. CE ships vanilla lead as usable default content; `harness`, `yoke`, and `virtual_tether` are framework backends and require a pack/mod to provide a selecting item/profile. `max_working_radius` must be within `0.5..6.0`; the effective orbit radius is the smaller of the worker profile radius and attachment limit. `output_multiplier` must be finite and greater than zero and scales both the resolved RPM and stress capacity.

Selectors are deterministic: exact `items` beat `item_tags`; within the same selector class, higher `priority` wins and ties are broken by profile ID. `workers`/`worker_tags` and `machines` are allowlists; an empty allowlist means unrestricted. Invalid profiles fail the datapack reload with the profile ID in the error.

The existing `#createhorsepower:attachment_items` / `#createhorsepower:worker_leashes` path remains a compatibility fallback. Items that have no explicit profile but are in those tags use the legacy `vanilla_leash` backend, are not consumed, and retain the existing fence-knot behavior.

For non-vanilla backends, the current Horse Crank interaction intentionally preserves the established worker-selection UX: the target mob is first leashed to the player, then use the configured attachment item on the Horse Crank. CHP releases that temporary selection leash and persists its own attachment ownership marker. Detach/orphan recovery only cleans the backend recorded by CHP; it does not remove unrelated current leashes.
### Entity Tags

- `#createhorsepower:workers/small`
- `#createhorsepower:workers/medium`
- `#createhorsepower:workers/large`

These tier tags are available on both loaders and are fallbacks after higher-priority exact profiles. When legacy balance is overriding a bundled species profile, an explicit legacy creature list classification is checked before these tier tags so existing packs keep their intended small/medium/large assignment.

---

## KubeJS Integration â€” NeoForge 1.21.1 only

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
        requiresTamed: false,
        machines: {
            'createhorsepower:horse_crank': {
                rpm: 5.0,
                movementRadius: 2.5,
                allowBaby: true
            }
        }
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


KubeJS worker maps use **camelCase** JavaScript keys: `movementRadius`, `speedScaling`, `speedReference`, `healthScaling`, `healthReference`, `requiresTamed`, and `allowBaby`. Nested `machines[<machine-id>]` objects use the same camelCase names (plus `rpm`/`stress`). This differs from datapack/Data Map snake_case such as `movement_radius`. Unknown top-level or nested machine keys are rejected with an error instead of silently no-oping.

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

`pathEvaluated` setters replace the evaluator's final path multipliers. They are absolute values, not extra multiplicative factors; reading `event.speedMultiplier` or `event.stressMultiplier` returns the current evaluated value before any script adjustment.

KubeJS is optional. Without it, NeoForge Data Maps, tags, config, attachment, movement, and generation continue normally.

---

## Forge 1.20.1 customization

> **Forge 1.20.1 only.** NeoForge Data Maps and CHP KubeJS registration are unavailable.

Forge uses the shared worker-profile JSON layer for exact per-entity profiles and machine-specific overrides; it does not require Java or NeoForge Data Maps for those features. If no shared JSON profile exists, CE resolves bundled per-species fallbacks and then legacy tier/tag/config behavior. Changed legacy `creatureRPMRange` or tier stress values continue to replace bundled base RPM/stress for the applicable tier, while intended per-animal attribute scaling remains active.

For paths, explicit `poorPathBlock`, `normalPathBlock`, and `greatPathBlock` config entries beat CE bundled exact/family path defaults. The configured path profile is then fed into the selected evaluation mode normally, including `WEIGHTED_AVERAGE` for mixed tracks.

For worker authoring parity, use `createhorsepower/worker_profiles/*.json`. NeoForge Data Maps and KubeJS remain loader-specific convenience layers, not requirements for exact worker or machine-override profiles.

---

## Optional TerraFirmaCraft compatibility

TerraFirmaCraft is **not** a published hard dependency. CE's compatibility is registry-ID and data driven.

### Workers

Version 1.2.1 introduced built-in TFC defaults for these IDs when they exist:

- `tfc:horse`
- `tfc:donkey`
- `tfc:mule`
- `tfc:cow`
- `tfc:pig`
- `tfc:sheep`
- `tfc:dromedary_camel`
- `tfc:bactrian_camel`

Starting with 1.2.2, those TFC species defaults are bundled common-code fallbacks on both loaders rather than shipped NeoForge Data Map values. Shared worker JSON can override them on both loaders; NeoForge can additionally use an explicit Data Map or KubeJS profile. Changed legacy RPM/stress values can override their bundled base output according to the effective worker tier while preserving intended per-animal health scaling.

On Forge 1.20.1, registry IDs absent from the installed TFC version simply do nothing.

### Path families

Without any pack config, CE recognizes common TFC terrain families:

| TFC registry path family | CHP default |
|---|---|
| `tfc:grass/*`, `tfc:dirt/*`, `tfc:clay_grass/*`, `tfc:clay/*` | Dirt-like |
| `tfc:rock/gravel/*` | Gravel-like |
| `tfc:rock/cobble/*`, `tfc:rock/mossy_cobble/*` | Normal |
| `tfc:rock/smooth/*`, `tfc:rock/bricks/*`, `tfc:rock/mossy_bricks/*` | Great |

These defaults are intended to make ordinary TFC walking rings work out of the box. Explicit KubeJS/Data Map profiles override them on NeoForge, and pack/server legacy path lists override them on both loaders. Weighted mixed-path evaluation still combines the resolved per-block profiles normally.

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
poorPathBlock = []
normalPathBlock = []
greatPathBlock = []
smallCreatures = ["minecraft:wolf"]
mediumCreatures = ["minecraft:cow"]
largeCreatures = ["minecraft:horse"]

# Fresh 1.2+ configs intentionally leave these legacy path lists empty so bundled profiles apply.
# Existing 1.1 files are not reset; stored path entries remain explicit overrides.

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
    # Set false when path quality should change RPM/validity but not stress capacity.
    enableStressScaling = true
    minimumCoverage = 1.0
    checkIntervalTicks = 40

[automation]
    # Options: HIGH_STOPS, HIGH_RUNS, IGNORE
    defaultRedstoneMode = "HIGH_STOPS"
```

Bundled defaults keep vanilla dirt/grass, dirt path/gravel, and ice-family paths usable without manual config. Existing 1.1 config files retain their stored legacy path entries rather than being rewritten.

### Visual gait settings

`workerGroundSpeedScale` converts the mob's movement-speed attribute into a visual ground speed in blocks/second. The result is clamped between `minWorkerGroundSpeed` and `maxWorkerGroundSpeed`; angular movement is then derived as `linearSpeed / radius`, so workers at different configured radii retain the same ground speed.

These settings affect presentation/movement only. `rpm`, `speed_scaling`, path `speed_multiplier`, and KubeJS output changes continue to control mechanical output independently.

`path.enableStressScaling` defaults to `true` for backward compatibility. Set it to `false` to keep path speed/RPM multipliers, validity, and coverage rules while forcing the path stress multiplier to `1.0`. This is useful for packs that want road quality to change travel speed without changing the worker tier's configured SU capacity.

### Debug logging

`diagnostics.debugLogging = true` enables transition-oriented diagnostics for attachment/rejection, detach/leash cleanup, orphan recovery, AI-control ownership, work state, and path-state changes. It intentionally does **not** log normal orbit movement every tick.

Persistent retry states are rate limited: repeated `beforeWorkStart` veto diagnostics and deferred-recovery reminders emit immediately, then at most once per 1200 ticks per affected crank/worker state. The normal functional retry/recovery logic still runs at its normal cadence.

---

## Precedence Rules

Higher-priority entries replace lower-priority tuning for the same entity or block.

### NeoForge 1.21.1

Worker profile source precedence:

1. **KubeJS startup profile** (`HorsePowerEvents.workerProfiles`)
2. **Shared worker-profile JSON** (`createhorsepower/worker_profiles`)
3. **Explicit NeoForge Data Map** (`createhorsepower:worker_stats`)
4. **CE bundled exact registry-ID profile** (including optional TFC species)
5. **Explicit legacy config list / worker tier tag / CE built-in tier fallback** for otherwise unresolved workers

When a CE bundled profile is selected, changed legacy `creatureRPMRange` / tier-stress values are applied to its base output. Tier classification for that legacy override checks explicit config creature lists first, then tier tags, then CE's built-in tier. Intended health-based per-animal stress scaling remains active on top of the configured base SU.

Path stats:

**KubeJS â†’ explicit Data Map â†’ legacy path config â†’ CE bundled exact/family fallback**.

The selected per-block profiles are still processed by the configured path evaluation mode. `WEIGHTED_AVERAGE` remains the default and continues to average mixed paths.

### Forge 1.20.1

KubeJS and NeoForge Data Maps are unavailable, but shared worker-profile JSON is fully supported.

Worker precedence is **shared worker JSON → CE bundled exact profile → explicit legacy config/tier-tag fallback**. Bundled profiles use the same legacy balance override logic as NeoForge, including explicit legacy creature-list classification before tier tags/built-in tier fallback.

For paths: **legacy path config â†’ CE bundled exact/family fallback** (`greatPathBlock`, `normalPathBlock`, `poorPathBlock`).

This ordering is intentional: pack/server data must be able to override CE defaults, while bundled defaults still provide sensible behavior when a pack supplies nothing.

---

## In-Game Diagnostics & Commands

These apply on both loaders.

- **Engineer's Goggles:** worker name/status, path efficiency, individual bonuses, and redstone mode.
- **Wrench:** sneak-use a Create wrench to cycle `HIGH_STOPS` â†’ `HIGH_RUNS` â†’ `IGNORE`.
- **`/createhorsepower inspect`:** targeted crank state, mechanical RPM, visual gait blocks/second + radius, worker UUID/type, leash holder, attachment/AI marker state, and recovery anchor/chunk status when available.
- **`/createhorsepower worker <entity_type>`:** query effective worker stats.
- **`/createhorsepower path <block>`:** query effective path speed/stress multipliers.

For live incident debugging, enable `diagnostics.debugLogging`, reproduce the attach/detach/recovery sequence, and pair the resulting transition logs with `/createhorsepower inspect`. Disable it again when the incident is resolved if the extra INFO lines are no longer useful.

---

## Migration from 1.1

### 1. Server Configuration Compatibility

All 1.1 root keys (`creatureRPMRange`, `largeCreatureStressRange`, `poorPathBlock`, etc.) remain at the root of `createhorsepower-server.toml`. Existing configs load without a reset.

Fresh 1.2+ configs generate `poorPathBlock`, `normalPathBlock`, and `greatPathBlock` as empty arrays so bundled path profiles are not masked. Existing 1.1 config files keep their already-stored path lists and semantics.

Version 1.2.1 adds these non-breaking defaults:

- `workers.workerGroundSpeedScale = 10.0`
- `workers.minWorkerGroundSpeed = 0.8`
- `workers.maxWorkerGroundSpeed = 3.5`
- `diagnostics.debugLogging = false`

### 2. 1.2.2 precedence correction

CE 1.2.2 restores pack authority over CE bundled defaults:

- Changed legacy `creatureRPMRange` and tier stress values override bundled worker base output for the applicable tier.
- Explicit legacy creature lists are consulted before CE tier fallbacks when determining the tier for those overrides.
- Intended per-animal health scaling remains active on top of the configured base output.
- Legacy `poorPathBlock`, `normalPathBlock`, and `greatPathBlock` entries override CE bundled exact/family path defaults.
- Weighted mixed-path evaluation is unchanged; only per-block source precedence changed.

On NeoForge, KubeJS and explicit Data Maps remain above those legacy config overrides. CE's own bundled defaults are no longer shipped as Data Map values, so Data Maps consistently represent explicit pack/datapack overrides.

### 2b. Forge 1.20.1 Customization

Forge has no Data Map/KubeJS layer, but it does have the same shared worker-profile and attachment-profile JSON loaders as NeoForge. Use shared worker JSON for exact entity/machine profiles; legacy worker balance and path config remain supported fallbacks/overrides as documented above.

### 3. Existing Horse Cranks and Redstone

Pre-1.2 Horse Cranks have no saved redstone mode and migrate to `IGNORE`, preserving old behavior. Newly placed 1.2 cranks use `defaultRedstoneMode`.

### 4. Movement Radius

Version 1.2 accepts radii from `0.5` through `6.0` blocks. Values above 6 are rejected because normal Minecraft leads cannot reliably support larger circles. Update any older prerelease Data Map/KubeJS profile above 6 before loading it.

### 5. 1.2.1 worker ownership and leash recovery

Version 1.2.1 persists attachment ownership separately from temporary AI suppression. If a worker is unloaded when it is detached, the detach policy is stored at level scope and recovered after vanilla restores the worker's persisted leash. Recovery is bounded, does not force-load the old crank chunk, preserves foreign/current leashes, and keeps `detachWorker(false)` no-drop behavior across save/unload/reload.
