# Create Horse Power - CE

**Create Horse Power - Community Edition** brings reliable animal-powered rotational generation to Create. Harness a supported mob to a Horse Crank, build a proper walking path, and power early factories without wind, water, steam, electricity, or engines.

CE is an independent, community-maintained fork of [Create Horse Power by SteamPunkNation](https://github.com/SteamPunkNation/CreateHorsePower). It intentionally retains the original `createhorsepower` mod ID for world compatibility. **Do not install CE and the original mod together.**

## Requirements

| Component       | NeoForge build          | Forge build        |
|-----------------|-------------------------|--------------------|
| Minecraft       | 1.21.1                  | 1.20.1             |
| Mod loader      | NeoForge 21.1.215+      | Forge 47.x         |
| Java            | 21                      | 17                 |
| Create          | >= 6.0.8, < 6.1.0       | >= 6.0.8, < 6.1.0  |

The core Horse Crank gameplay — attaching a worker, validating a path, redstone modes, goggles and command diagnostics, data-driven worker and path defaults — is identical on both versions. Jade is an optional integration on both loaders. KubeJS, NeoForge Data Maps, and Ponder scenes are NeoForge-only and are not yet available on Forge 1.20.1 (see the [compatibility matrix](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/COMPATIBILITY.md) for the full breakdown).

TerraFirmaCraft is optional on both loaders. When it is installed, CE supplies built-in worker defaults for supported TFC livestock and path defaults for common TFC soil/rock families without making TFC a required dependency.

## Animal Power Framework

The 1.2 update expands the Horse Crank into a data-driven framework while preserving its early-game Create role:

- Worker stats define RPM, stress capacity, movement radius, attribute scaling, taming requirements, and baby-worker rules (via NeoForge Data Maps on NeoForge, and via the platform-emulated canonical defaults and supported tags/config on Forge 1.20.1).
- Path stats define speed and stress multipliers for any block (same approach per loader as worker stats).
- Individual movement speed and max health can scale a worker's live mechanical output.
- Visible animal gait is independent from mechanical RPM and is bounded to believable movement speeds.
- Weighted-average, worst-block, and legacy path evaluation modes support different pack designs.
- Redstone modes can stop, require, or ignore a signal and can be cycled with a Create wrench.
- Custom attachment items and worker tiers can be supplied through datapack tags (both loaders).
- Engineer's Goggles, Jade (both loaders), and `/createhorsepower` commands expose useful diagnostics.
- Optional transition-based debug logging can be enabled with `diagnostics.debugLogging`; normal movement is not logged every tick.
- Optional KubeJS startup profiles and lifecycle events support scripted packs (NeoForge 1.21.1 only).

Movement radii are supported from 0.5 to 6.0 blocks. This cap keeps workers within normal Minecraft lead mechanics.

## Reliability Improvements

- Persistent worker identity and durable attachment ownership across chunk reloads.
- Each crank carries a persistent, real, position-independent instance UUID, so a replacement crank at the same coordinates never inherits a previous crank's worker ownership.
- Unloaded detach intent is persisted at level scope, including `detachWorker(false)` no-drop semantics, so breaking/replacing the old crank cannot orphan the policy.
- Recovery waits until vanilla has restored delayed leash data, then removes only the stale leash owned by the old crank. It does not force-load the old crank chunk and preserves a worker already leashed elsewhere.
- Recovery timeout age survives worker unload/save/reload instead of restarting on chunk churn.
- Workers recover their original AI state if crank control is orphaned, including workers that already had `NoAI=true` before attachment.
- Stable direction handling beside existing Create kinetic networks.
- Smooth server-authoritative worker movement with correct yaw tracking.
- Live kinetic refresh when worker attributes change.
- Unloaded workers, invalid paths, redstone stops, ineligible workers, and KubeJS vetoes remain distinct states.

## For Modpacks

Create Horse Power CE supports primitive, medieval, historical, and staged technology packs. Packmakers can customize workers, output, paths, attachment items, redstone behavior, and scripts (KubeJS on NeoForge 1.21.1) without editing Java code.

Read the complete [Packmaker and Modder Guide](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/PACKMAKERS.md) for schemas, examples, config keys, TFC defaults, precedence, diagnostics, commands, and KubeJS events.

On NeoForge 1.21.1, KubeJS profiles take priority over NeoForge Data Maps, which take priority over built-in registry-ID fallbacks and then legacy tags/config lists. Exact TFC species Data Map entries are guarded by `mod_loaded("tfc")` and remain overridable/removable by later datapacks.

## Updating from CE 1.1

- Legacy server-config keys remain at the TOML root.
- Existing cranks preserve the old redstone-ignored behavior; newly placed cranks use the configured default mode.
- Built-in vanilla worker and path Data Maps (NeoForge) take precedence over old tier and path settings. Override those Data Maps or use KubeJS to retune them.
- Update any prerelease worker profile above a 6-block movement radius before loading it in 1.2; out-of-range Data Map or KubeJS values are rejected.
- Version 1.2.1 adds `workers.workerGroundSpeedScale`, `workers.minWorkerGroundSpeed`, `workers.maxWorkerGroundSpeed`, and `diagnostics.debugLogging`; their defaults are safe for existing worlds.
- Back up important worlds before updating.

Full release details:

- NeoForge 1.21.1 — [1.2.1 changelog](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/changelog/1.21.1-1.2.1.md)
- Forge 1.20.1 — [1.2.1 changelog](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/changelog/1.20.1-1.2.1.md)

## Credits

The original project, concept, and foundation were created by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from the original and community contributors.

Source code and issue tracker: [UpperMoon0/CreateHorsePower-CE](https://github.com/UpperMoon0/CreateHorsePower-CE)
