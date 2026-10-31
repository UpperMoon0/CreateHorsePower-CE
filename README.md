# Create Horse Power — Community Edition

Create Horse Power CE adds animal-powered rotational generation to Create. Attach an eligible worker to a Horse Crank, prepare a valid circular track, and feed the resulting RPM and stress capacity into a Create kinetic network.

This is an independent, community-maintained fork of [SteamPunkNation/CreateHorsePower](https://github.com/SteamPunkNation/CreateHorsePower). It retains the `createhorsepower` mod ID for saved-world compatibility; **do not install CE and the original mod together**.

## Compatibility and installation

CE maintains **Forge** and **NeoForge** builds. Create and the matching Minecraft loader are required. Select a jar built for your loader/Minecraft combination, install its compatible Create dependency, and place CE in `mods/`. The matching Minecraft, loader, Create, Java and CE release requirements are defined by the project metadata and listed in [GitHub Releases](https://github.com/UpperMoon0/CreateHorsePower-CE/releases). Do not mix loader artifacts. Runtime metadata declares minimum supported mod and loader versions without arbitrary upper caps; newer dependency versions are allowed to load but remain subject to compatibility testing. Each jar still targets a specific Minecraft version.

The core behavior—worker profiles, path evaluation, redstone modes, commands and goggles tooltips—is shared. Some integrations differ by loader; refer to the [feature compatibility matrix](docs/COMPATIBILITY.md).

Optional integrations include **Jade** on both loaders, **KubeJS** scripting and Data Maps on NeoForge, and **TerraFirmaCraft** worker/path defaults. **Icy's Better Horses** supplies additional breeds while preserving its ownership and transport rules; see the [Icy integration contract](docs/icy-better-horses.md). **Create: Bionics** adds the fully assembled and fueled Oxhauler as an eligible draft worker without making Bionics mandatory; see the [Bionics integration contract](docs/create-bionics.md). These optional integrations do not ship bundled third-party mod jars.

## Gameplay

Place a Horse Crank, build a complete valid path around it, and attach a worker using an item in `#createhorsepower:attachment_items` (a vanilla lead by default). The worker drives the crank while its eligibility, track and redstone requirements are satisfied.

- Sneak-use a Create wrench on the crank to cycle `HIGH_STOPS`, `HIGH_RUNS` and `IGNORE` modes.
- Wear Engineer's Goggles or use Jade to inspect worker, path and output state.
- Use `/createhorsepower inspect`, `/createhorsepower worker <entity_type>` and `/createhorsepower path <block>` for diagnostics.
- The visual walking pace is independent of mechanical RPM; large output changes do not force workers to sprint.
- Worker movement radii are supported within the documented range (the upper bound preserves standard lead behavior).

Cranks persist assigned workers and recover durable ownership/leash state safely across unload/reload, without stealing another machine's worker. Transition diagnostics are opt-in through `diagnostics.debugLogging`.

## Packmakers and modders

The [Packmaker and Modder Guide](docs/PACKMAKERS.md) describes the shared datapack worker/path/profile APIs, machine-specific output, attachment modes and selection tags, configuration, precedence, and loader-specific KubeJS and Data Map behavior. Vanilla leads work without a pack. `harness`, `yoke` and `virtual_tether` are supported framework backends; packs or companion mods provide the corresponding items/profiles.

The default worker and path definitions are bundled fallbacks, not forced pack overrides. Explicit pack profiles take precedence as described in the guide. TerraFirmaCraft compatibility also uses opt-in registry-aware fallbacks rather than a hard runtime dependency.

For migrations from older releases, read the relevant [versioned changelogs](changelog/) and back up your world and configuration before upgrading. Avoid treating the current source README as a historical migration checklist.

## Building and testing

This is a multi-platform Gradle workspace. Shared logic is in `common/`; loader project directories hold bootstrap code and API adapters. It does not use Architectury.

```sh
./gradlew buildAll
./gradlew testAllVersions
./gradlew verifySharedSources
./gradlew verifySharedResources
./gradlew runtimeSmokeAll
./gradlew verify
```

On Windows use `gradlew.bat` in place of `./gradlew`. Jars are generated in the appropriate loader project's `build/libs/`; the mod version is defined in the root `gradle.properties` and supported dependency versions are maintained in loader project metadata.

Ordinary smoke tests exercise both loaders without optional mods. Optional integrations have dedicated test workflows and conditional runtime dependencies. Tests with a real optional server mod do **not** constitute real-client multiplayer, packet/raycast or rendering acceptance. Consult the [Actions runs](https://github.com/UpperMoon0/CreateHorsePower-CE/actions) and the appropriate release-specific acceptance notes for actual verification results.

## Credits, license and support

Original concept and mod by SteamPunkNation. Community Edition is maintained by UpperMoon0 with contributions from the community.

Licensed under the [MIT License](LICENSE). Report bugs in the [issue tracker](https://github.com/UpperMoon0/CreateHorsePower-CE/issues).
