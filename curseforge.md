# Create Horse Power - CE

**Create Horse Power - Community Edition** brings reliable animal-powered rotational generation to Create. Harness a supported mob to a Horse Crank, build a proper walking path, and power early factories without wind, water, steam, electricity, or engines.

CE is an independent, community-maintained continuation of [Create Horse Power by SteamPunkNation](https://github.com/SteamPunkNation/CreateHorsePower). It intentionally retains the original `createhorsepower` mod ID for world compatibility. **Do not install CE and the original mod together.**

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.215 or newer
- Create 6.0.8 up to, but not including, 6.1.0
- Java 21

Jade and KubeJS are optional integrations.

## Animal Power Framework

The 1.2 update expands the Horse Crank into a data-driven framework while preserving its early-game Create role:

- Worker Data Maps define RPM, stress capacity, movement radius, attribute scaling, taming requirements, and baby-worker rules.
- Path Data Maps define speed and stress multipliers for any block.
- Individual movement speed and max health can scale a worker's live output.
- Weighted-average, worst-block, and legacy path evaluation modes support different pack designs.
- Redstone modes can stop, require, or ignore a signal and can be cycled with a Create wrench.
- Custom attachment items and worker tiers can be supplied through datapack tags.
- Engineer's Goggles, Jade, and `/createhorsepower` commands expose useful diagnostics.
- Optional KubeJS startup profiles and lifecycle events support scripted packs.

Movement radii are supported from 0.5 to 6.0 blocks. This cap keeps workers within normal Minecraft lead mechanics.

## Reliability Improvements

- Persistent worker identity and recovery across chunk reloads.
- Safe cleanup when a worker detaches or a crank is removed.
- Stable direction handling beside existing Create kinetic networks.
- Smooth server-authoritative worker movement.
- Live kinetic refresh when worker attributes change.
- Unloaded workers, invalid paths, redstone stops, ineligible workers, and KubeJS vetoes remain distinct states.

## For Modpacks

Create Horse Power CE supports primitive, medieval, historical, and staged technology packs. Packmakers can customize workers, output, paths, attachment items, redstone behavior, and scripts without editing Java code.

Read the complete [Packmaker and Modder Guide](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/PACKMAKERS.md) for schemas, examples, config keys, precedence, commands, and KubeJS events.

KubeJS profiles take priority over NeoForge Data Maps, which take priority over legacy tags and config lists. Built-in worker and path profiles therefore override old fallback values for the same mob or block.

## Updating from CE 1.1

- Legacy server-config keys remain at the TOML root.
- Existing cranks preserve the old redstone-ignored behavior; newly placed cranks use the configured default mode.
- Built-in vanilla worker and path Data Maps take precedence over old tier and path settings. Override those Data Maps or use KubeJS to retune them.
- Update any prerelease worker profile above a 6-block movement radius before loading it in 1.2; out-of-range Data Map or KubeJS values are rejected.
- Back up important worlds before updating.

Full release details are available in the [1.2.0 changelog](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/changelog/1.21.1-1.2.0-ce.1.md).

## Credits

The original project, concept, and foundation were created by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from the original and community contributors.

Source code and issue tracker: [UpperMoon0/CreateHorsePower-CE](https://github.com/UpperMoon0/CreateHorsePower-CE)
