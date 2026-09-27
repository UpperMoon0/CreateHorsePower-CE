# Create Horse Power - CE

Create Horse Power - Community Edition adds animal-powered rotational generation to Create. Attach a supported worker to a Horse Crank, build a valid circular path, and use the resulting RPM and stress capacity in an early-game Create network.

This is an independent, community-maintained fork of [SteamPunkNation/CreateHorsePower](https://github.com/SteamPunkNation/CreateHorsePower). It retains the `createhorsepower` mod ID for world compatibility, so do not install CE and the original mod together.

## Compatibility

| Component | NeoForge | Forge |
|---|---|---|
| Minecraft | 1.21.1 | 1.20.1 |
| Mod loader | NeoForge 21.1.215 or newer | Forge 47.x |
| Create | 6.0.8 up to, but not including, 6.1.0 | 6.0.8 up to, but not including, 6.1.0 |
| Java | 21 | 17 |

Create and the respective loader are required. The core gameplay — worker profiles, path evaluation, redstone modes, commands, goggles tooltips — is identical on both versions. See the [compatibility matrix](docs/COMPATIBILITY.md) for feature-level differences (KubeJS, Ponder, data maps).

On both loaders, Jade integration activates automatically when Jade is installed. NeoForge also supports the optional KubeJS integration; neither mod is required. TerraFirmaCraft is also optional: when present, CE provides built-in worker defaults for supported TFC livestock and path defaults for common TFC soil/rock families without adding a hard dependency.

## Highlights

- Persistent Horse Cranks with durable worker ownership and orphan-leash recovery across chunk unload/save/reload cycles.
- Data-driven worker profiles on both loaders through shared datapack JSON, with RPM, stress capacity, movement radius, taming/baby rules, attribute scaling, and machine-specific overrides.
- Data-driven path profiles with weighted-average, worst-block, and legacy evaluation modes.
- Believable worker gait that is independent from mechanical RPM; high-output cranks no longer force animals to sprint around the ring.
- Per-crank redstone modes: `HIGH_STOPS`, `HIGH_RUNS`, and `IGNORE`.
- Create Goggles and optional Jade (both loaders) diagnostics for worker, path, output, and veto state.
- Optional transition-based field logging through `diagnostics.debugLogging` for attach/detach, leash recovery, AI control, work state, and path state.
- Datapack attachment profiles on both loaders with `vanilla_leash`, `harness`, `yoke`, and `virtual_tether` backends, plus worker/item tags for compatibility and selection.
- NeoForge 1.21.1 only: optional KubeJS startup profiles and lifecycle/output events.
- Inspection commands for cranks, workers, and path blocks.

Worker movement radii are supported from `0.5` to `6.0` blocks. The upper limit is intentional: larger circles are incompatible with normal Minecraft lead behavior.

## Installation

1. Install your target Minecraft version with the matching loader and a compatible Create 6.0.x release:
   - NeoForge 21.1.215+ on Minecraft 1.21.1 (Java 21), or
   - Forge 47.x on Minecraft 1.20.1 (Java 17).
2. Place the Create Horse Power CE jar for your loader/version in the `mods` directory.
3. Do not keep the original Create Horse Power jar in the same instance.
4. Optionally install Jade on either loader for HUD details, KubeJS on NeoForge 1.21.1 for scripted profiles and events, or TerraFirmaCraft for CE's built-in TFC compatibility defaults.

## Playing

Craft and place a Horse Crank, prepare a complete valid path around it, then attach an eligible mob with an item in `#createhorsepower:attachment_items` (a vanilla lead by default). Connect the crank to a Create kinetic network.

- Sneak-use a Create wrench on the crank to cycle its redstone mode.
- Wear Engineer's Goggles or use Jade to inspect its current state.
- Use `/createhorsepower inspect`, `/createhorsepower worker <entity_type>`, or `/createhorsepower path <block>` for diagnostics.

## Packmakers

The full [Packmaker and Modder Guide](docs/PACKMAKERS.md) documents:

- Shared worker-profile JSON (`data/<namespace>/createhorsepower/worker_profiles/`) with machine-specific overrides on both versions.
- Shared attachment-profile JSON plus worker, attachment-item, and leash tags on both versions.
- Optional `createhorsepower:worker_stats` / `path_stats` NeoForge Data Maps and KubeJS scripting on NeoForge 1.21.1.
- Server configuration, including the 1.2.1 visual-gait and debug-logging settings.
- Built-in optional TerraFirmaCraft worker/path defaults and precedence behavior.
- KubeJS startup registration and server lifecycle events (NeoForge 1.21.1 only for now).
- Migration behavior from CE 1.1.

CE's bundled worker/path profiles are fallback defaults rather than pack-override data. Worker customization is loader-neutral through shared datapack JSON; on NeoForge 1.21.1, KubeJS remains highest priority and shared worker JSON sits above explicit Data Maps. Legacy server balance/path config can still override bundled fallbacks where documented, and weighted mixed-path evaluation is unchanged.

Vanilla lead support is bundled and works on a fresh install. `harness`, `yoke`, and `virtual_tether` are framework backends for packs/mods; CE does not ship player-facing items using those modes by default.

## Migrating from CE 1.1

- Existing 1.1 server-config keys remain at the TOML root.
- Existing Horse Cranks without a saved redstone mode migrate to `IGNORE`; new cranks use `defaultRedstoneMode`.
- In 1.2.2, changed legacy RPM/stress values override CE bundled worker base output while intended per-animal health scaling remains active. Explicit legacy creature lists also take precedence over CE tier fallbacks for classification.
- Fresh 1.2+ configs leave the legacy path lists empty so bundled per-block defaults apply (dirt `0.70`, gravel `1.10`). Existing 1.1 config files retain their stored path lists and continue to override bundled path defaults. On NeoForge, explicit KubeJS/Data Map path profiles still win.
- Update any prerelease worker profile above a 6-block movement radius before loading it in 1.2; out-of-range Data Map or KubeJS values are rejected.
- Back up important worlds before changing mod versions.

### 1.2.1 maintenance notes

- Unloaded detach intent and recovery timeout age now persist across save/unload/reload, including `detachWorker(false)` no-drop semantics.
- Orphan recovery waits for vanilla leash restoration and removes only the stale crank-owned leash/knot; it does not force-load the old crank chunk or steal a worker already attached elsewhere.
- Visual orbit speed is configured separately from generated RPM through `workers.workerGroundSpeedScale`, `workers.minWorkerGroundSpeed`, and `workers.maxWorkerGroundSpeed`.
- `diagnostics.debugLogging` is off by default and emits transition-oriented diagnostics rather than per-tick movement spam.

### 1.2.2 balance and pack-precedence notes

- Legacy pack/server worker balance now overrides CE bundled species base RPM/SU instead of being shadowed by bundled profiles.
- Intended per-animal health scaling is preserved on top of the configured base output.
- Pack-defined path data overrides CE bundled path defaults; weighted mixed-path evaluation still averages the resolved per-block profiles normally.
- NeoForge bundled defaults are no longer shipped as Data Maps, so Data Maps consistently represent explicit datapack/packmaker overrides.

See the unified [1.2.7 release notes](changelog/1.2.7.md) for the current cross-loader release changes.

## Building

The repository is a multi-version workspace. Shared logic lives in `common/`
(single source of truth consumed by both platforms); `neoforge-1.21.1/` and
`forge-1.20.1/` contain only loader glue and version adapters. No Architectury involved.

Root verification tasks (used by CI):

```text
./gradlew buildAll               # build every platform
./gradlew testAllVersions        # run the shared tests against every platform
./gradlew verifySharedSources    # fail when duplicated platform sources drift
./gradlew verifySharedResources  # fail on duplicated shared assets and on cross-version datapack drift
./gradlew runtimeSmokeAll        # boot both loader GameTest servers and run their required smoke tests
./gradlew verify                 # all of the above
```

On Windows:

```powershell
.\gradlew.bat verify
```

On Linux or macOS:

```bash
./gradlew verify
```

Generated jars are written to `<platform>/build/libs` (e.g.
`neoforge-1.21.1/build/libs/createhorsepower-ce-1.21.1-<version>.jar`).
The semantic mod version lives once in the root `gradle.properties`; artifacts are
versioned as `<minecraft_version>-<mod_version>`.
Report bugs through the [issue tracker](https://github.com/UpperMoon0/CreateHorsePower-CE/issues).

## Credits and License

The original mod and concept were created by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from the Create Horse Power community.

Licensed under the [MIT License](LICENSE).
