# Create Horse Power — Community Edition

**Create Horse Power CE** brings animal-powered rotational generation to Create. Attach an eligible worker to a Horse Crank, build a valid circular path, and power early Create machines without advanced generators.

This is an independent, community-maintained fork of [SteamPunkNation's Create Horse Power](https://github.com/SteamPunkNation/CreateHorsePower). CE retains the original `createhorsepower` mod ID for world compatibility. **Do not install the original mod and CE together.**

## Installation and compatibility

CE offers **Forge** and **NeoForge** builds. Install the file matching your Minecraft version and mod loader, alongside the compatible version of Create. Required Java, loader, and Create versions vary by release; consult the selected CurseForge file's dependencies and [GitHub Releases](https://github.com/UpperMoon0/CreateHorsePower-CE/releases). Never mix Forge and NeoForge jars.

Core Horse Crank gameplay is available on both loaders. Optional integrations include:

- **TerraFirmaCraft:** built-in worker defaults for supported livestock and path defaults for common ground types; TFC is not required.
- **Icy's Better Horses:** supported breeds work as crank workers while respecting ownership, trusted-player, recall and transport rules. See the [Icy integration contract](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/icy-better-horses.md).
- **Create: Bionics:** an assembled, fueled **Oxhauler** can act as a draft worker, with fuel consumption and safety checks handled by the integration. Bionics is optional. See the [Bionics integration contract](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/create-bionics.md) and the applicable release notes for precise qualification status; headless server GameTests are **not** proof of graphical-client multiplayer behavior.
- **Jade:** optional HUD diagnostics on both loaders.
- **KubeJS, Data Maps and Ponder:** additional NeoForge-only features; shared datapack worker and attachment profiles work on both loaders.

See the [feature compatibility matrix](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/COMPATIBILITY.md) for loader-specific details.

## Using the Horse Crank

1. Place a Horse Crank and prepare a complete, valid walking path around it.
2. Attach an eligible worker with a supported attachment item (a vanilla lead works by default).
3. Connect the crank to your Create kinetic network. Output depends on the worker, terrain, configuration and active redstone mode.

Sneak-use a Create wrench to cycle the crank's `HIGH_STOPS`, `HIGH_RUNS` and `IGNORE` redstone modes. Engineer's Goggles, optional Jade integration and `/createhorsepower` diagnostics help explain worker and path eligibility, RPM and stress output. Visual walking speed stays bounded independently of mechanical RPM.

## Flexible animal-power framework

- Configure worker RPM, stress capacity, movement radius, attribute scaling, eligibility and machine-specific overrides.
- Evaluate paths with weighted-average, worst-block or legacy behavior and tune terrain multipliers.
- Use shared datapack JSON for worker and attachment profiles on both loaders; NeoForge also supports additional KubeJS scripting and Data Map overrides.
- Select profile-driven attachment items and supported leash, harness, yoke or virtual-tether backends. Non-vanilla items and profiles must be supplied by your pack or a companion mod.
- Integrate eligible modded workers through documented optional integrations without requiring their mods for ordinary gameplay.

See the [Packmaker and Modder Guide](https://github.com/UpperMoon0/CreateHorsePower-CE/blob/main/docs/PACKMAKERS.md) for schemas, examples, configuration and precedence.

## Persistence and reliability

CE tracks crank instances and worker ownership across chunk unloads and world reloads. Deferred detach and orphan-leash recovery avoid reclaiming a worker already assigned elsewhere. Workers recover their prior AI state when crank control ends. Movement, facing and kinetic output update through the server-authoritative system, with opt-in transition-based debug logs instead of per-tick spam.

## Updating and support

Back up worlds and configurations before upgrading. **Migration notes, exact dependency pins, and release-specific test evidence belong to the relevant [versioned changelog](https://github.com/UpperMoon0/CreateHorsePower-CE/tree/main/changelog/)** rather than this general description. Optional compatibility varies by the release you install.

Report issues and browse the source at [UpperMoon0/CreateHorsePower-CE](https://github.com/UpperMoon0/CreateHorsePower-CE).

## Credits

Original concept and mod by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from community developers.
