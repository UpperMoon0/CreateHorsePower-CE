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

The core Horse Crank gameplay — attaching a worker, validating a path, redstone modes, goggles and command diagnostics, shared worker/attachment datapack profiles, and bundled defaults — is identical on both versions. Jade is optional on both loaders. KubeJS, NeoForge Data Maps, and Ponder scenes remain NeoForge-only; Forge packmakers use the shared worker/attachment JSON layer for exact profiles and machine overrides (see the [compatibility matrix](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/COMPATIBILITY.md)).

TerraFirmaCraft is optional on both loaders. When it is installed, CE supplies built-in worker defaults for supported TFC livestock and path defaults for common TFC soil/rock families without making TFC a required dependency.

## Animal Power Framework

The 1.2 update expands the Horse Crank into a data-driven framework while preserving its early-game Create role:

- Worker stats define RPM, stress capacity, movement radius, attribute scaling, taming/baby rules, and per-machine overrides. Exact worker profiles can be authored with shared datapack JSON on both loaders; NeoForge additionally supports Data Maps and KubeJS.
- Path stats define speed and stress multipliers at runtime on both loaders. NeoForge packmakers can author arbitrary per-block values with Data Maps/KubeJS; Forge packmakers use the legacy path lists/multipliers plus CE bundled fallbacks.
- Individual movement speed and max health can scale a worker's live mechanical output.
- Visible animal gait is independent from mechanical RPM and is bounded to believable movement speeds.
- Weighted-average, worst-block, and legacy path evaluation modes support different pack designs.
- Redstone modes can stop, require, or ignore a signal and can be cycled with a Create wrench.
- Attachment profiles can select exact items/tags and choose `vanilla_leash`, `harness`, `yoke`, or `virtual_tether` backends on both loaders. Vanilla lead is bundled; the other three modes are packmaker framework capabilities unless a pack supplies an item/profile.
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

Worker precedence on NeoForge is KubeJS → shared worker JSON → explicit NeoForge Data Map → bundled fallback/legacy classification. On Forge it is shared worker JSON → bundled fallback/legacy classification. CE no longer ships bundled worker/path Data Maps; Data Maps represent explicit NeoForge pack overrides only.

## Updating from CE 1.1

- Legacy server-config keys remain at the TOML root.
- Existing cranks preserve the old redstone-ignored behavior; newly placed cranks use the configured default mode.
- CE does not ship bundled worker/path Data Maps. Fresh 1.2+ configs leave legacy path lists empty so richer bundled path defaults apply; upgraded 1.1 configs keep their stored lists and therefore preserve their old explicit path tuning.
- Update any prerelease worker profile above a 6-block movement radius before loading it in 1.2; out-of-range Data Map or KubeJS values are rejected.
- Version 1.2.1 adds `workers.workerGroundSpeedScale`, `workers.minWorkerGroundSpeed`, `workers.maxWorkerGroundSpeed`, and `diagnostics.debugLogging`; their defaults are safe for existing worlds.
- Back up important worlds before updating.

Full release details: [unified 1.2.7 changelog](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/changelog/1.2.7.md).

## Credits

The original project, concept, and foundation were created by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from the original and community contributors.

Source code and issue tracker: [UpperMoon0/CreateHorsePower-CE](https://github.com/UpperMoon0/CreateHorsePower-CE)
