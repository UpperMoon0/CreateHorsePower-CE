# Create Horse Power - CE

Create Horse Power - Community Edition adds animal-powered rotational generation to Create. Attach a supported worker to a Horse Crank, build a valid circular path, and use the resulting RPM and stress capacity in an early-game Create network.

This is an independent, community-maintained fork of [SteamPunkNation/CreateHorsePower](https://github.com/SteamPunkNation/CreateHorsePower). It retains the `createhorsepower` mod ID for world compatibility, so do not install CE and the original mod together.

## Compatibility

| Component | Supported version |
|---|---|
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.215 or newer |
| Create | 6.0.8 up to, but not including, 6.1.0 |
| Java | 21 |

Create and NeoForge are required. Jade and KubeJS integrations activate automatically when those mods are installed; neither is required.

## Highlights

- Persistent Horse Cranks that recover attached workers safely across chunk reloads.
- Data-driven worker profiles with RPM, stress capacity, movement radius, taming/baby rules, and optional movement-speed and max-health scaling.
- Data-driven path profiles with weighted-average, worst-block, and legacy evaluation modes.
- Per-crank redstone modes: `HIGH_STOPS`, `HIGH_RUNS`, and `IGNORE`.
- Create Goggles and optional Jade diagnostics for worker, path, output, and veto state.
- Datapack tags for worker tiers and custom attachment items.
- Optional KubeJS startup profiles and lifecycle/output events.
- Inspection commands for cranks, workers, and path blocks.

Worker movement radii are supported from `0.5` to `6.0` blocks. The upper limit is intentional: larger circles are incompatible with normal Minecraft lead behavior.

## Installation

1. Install Minecraft 1.21.1, NeoForge, and a compatible Create 6.0.x release.
2. Place the Create Horse Power CE jar in the `mods` directory.
3. Do not keep the original Create Horse Power jar in the same instance.
4. Optionally install Jade for HUD details or KubeJS for scripted profiles and events.

## Playing

Craft and place a Horse Crank, prepare a complete valid path around it, then attach an eligible mob with an item in `#createhorsepower:attachment_items` (a vanilla lead by default). Connect the crank to a Create kinetic network.

- Sneak-use a Create wrench on the crank to cycle its redstone mode.
- Wear Engineer's Goggles or use Jade to inspect its current state.
- Use `/createhorsepower inspect`, `/createhorsepower worker <entity_type>`, or `/createhorsepower path <block>` for diagnostics.

## Packmakers

The full [Packmaker and Modder Guide](docs/PACKMAKERS.md) documents:

- `createhorsepower:worker_stats` and `createhorsepower:path_stats` NeoForge Data Maps.
- Worker, attachment-item, and leash tags.
- Server configuration and precedence rules.
- KubeJS startup registration and server lifecycle events.
- Migration behavior from CE 1.1.

Important precedence rule: KubeJS profiles override Data Maps, which override legacy tags/config lists. Built-in vanilla worker and path Data Maps therefore take priority over old tier and path tuning for the same entries.

## Migrating from CE 1.1

- Existing 1.1 server-config keys remain at the TOML root.
- Existing Horse Cranks without a saved redstone mode migrate to `IGNORE`; new cranks use `defaultRedstoneMode`.
- Built-in worker and path Data Maps override legacy config fallback values. Override the relevant Data Map or use KubeJS when customizing those built-in entries.
- Update any prerelease worker profile above a 6-block movement radius before loading it in 1.2; out-of-range Data Map or KubeJS values are rejected.
- Back up important worlds before changing mod versions.

See the [1.2.0 changelog](changelog/1.21.1-1.2.0-ce.1.md) for the complete release notes.

## Building

The repository is a multi-version workspace. Shared logic lives in `common/`
(single source of truth consumed by both platforms); `neoforge-1.21.1/` and
`forge-1.20.1/` contain loader-specific glue. No Architectury involved.

On Windows:

```powershell
.\gradlew.bat clean build          # build every platform
.\gradlew.bat :neoforge-1.21.1:build
.\gradlew.bat :forge-1.20.1:build
```

On Linux or macOS:

```bash
./gradlew clean build
./gradlew :neoforge-1.21.1:build
./gradlew :forge-1.20.1:build
```

Generated jars are written to `<platform>/build/libs` (e.g.
`neoforge-1.21.1/build/libs/createhorsepower-ce-1.21.1-<version>.jar`).
Report bugs through the [issue tracker](https://github.com/UpperMoon0/CreateHorsePower-CE/issues).

## Credits and License

The original mod and concept were created by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from the Create Horse Power community.

Licensed under the [MIT License](LICENSE).
