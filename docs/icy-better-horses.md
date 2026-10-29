# Icy's Better Horses integration

Icy's Better Horses remains **optional** on both supported Forge and NeoForge loader families. The current matched binary versions, source commits, Modrinth artifacts, licensing observations and CI evidence belong in the [versioned Icy acceptance record](../changelog/1.2.9-icy-acceptance.md), not in this stable API/behavior guide. Use the selected CE release metadata when installing optional dependencies.

## Worker contract

Icy's mod ID is `icys_better_horses`; entity registry IDs use `icys-better-horses`. The supported Icy breeds use the horse worker profile and LARGE legacy worker tier, subject to pack-defined overrides, machine rules, taming/baby policy and individual movement-speed/max-health scaling. Unknown breed IDs and the horse cart are not eligible workers by default.

Inherited movement speed contributes to mechanical RPM, and max health to stress capacity when individual-stat scaling is enabled. Icy's own bond modifier is included only once through the normal movement-speed attribute. Jump strength is not a power multiplier. Explicit pack profiles and legacy overrides preserve their documented precedence.

## Behavior

- Icy remains optional. No Icy classes appear in CHP method signatures or bundled
  dependencies. A cached reflection adapter reads the released public ownership,
  trust, management, and cart API. Optional pseudo mixins target only Icy classes.
- Attachment honors `bh_mayHandle(UUID)`, including Icy trusted players. CHP does
  not change ownership, taming, bond, commands, gear, breed, attributes, or identity.
- Horses carrying passengers, riding vehicles, equipped with a cart, or marked as
  wagon-hitched cannot work. The gate runs each crank tick, so competing movement
  control does not wait for the normal stat refresh. Detach from the crank before
  changing transport assignments; simultaneous cart/wagon/crank use is unsupported.
- Follow/stay/wander goals are suppressed through the existing saved NoAI baseline.
  Normal orbit movement and vanilla leash synchronization remain authoritative.
  Breed models, animation based on measured position deltas, and networking stay
  with Icy/Minecraft. Passive Icy equipment effects remain enabled.
- Successful teleport/send-home, nearby summon, and successful snapshot recall
  release the exact crank's attachment and original AI baseline. Failed teleports
  do not detach. Both vanilla leash and virtual attachment backends use existing
  cleanup. Unloaded crank chunks are never force-loaded just for cleanup.
- Icy's vanilla-to-breed replacement preserves UUID and NBT. CHP continues resolving
  only the live server-indexed instance. It never reconstructs horses from saved
  worker data. Icy's own snapshot recall/revival can create bodies; CHP only releases
  stale control after Icy successfully registers its returned body.
- Death/removal and chunk/world recovery retain CHP's existing composite crank
  identity, durable detach policies, missing-worker grace period and recovery queue.
  No block-breaking or live-server behavior is introduced.

## Loader and compatibility limits

CE provides supported **Forge and NeoForge builds**, not an arbitrary cross-loader jar. Do not mix their mod artifacts. Breed IDs added or changed upstream may require a pack-authored worker profile and a new compatibility check. Concurrent cart, wagon and crank controllers are intentionally excluded. Consult the [per-release test evidence](../changelog/1.2.9-icy-acceptance.md) and [current Actions history](https://github.com/UpperMoon0/CreateHorsePower-CE/actions) before claiming a particular third-party binary has been verified.

## Reproducible verification

Run `./gradlew verify` for the ordinary (Icy-absent) loaders first. For the supported loader projects, opt in to `-PicyCompatibilityTest=true` on the corresponding `build` and `runGameTestServer` Gradle tasks; the loader-project task names and dependency pins are maintained in the current build configuration. The opt-in mode loads real Icy entities and required third-party dependencies into isolated worlds, using a dedicated `createhorsepower_icy` test namespace. The normal no-Icy tests remain a separate requirement.

The acceptance suite covers real breed factories, ownership/trusted-player rejection, competing transport controls, bond boundaries, attribute and offspring inheritance, jump exclusion, repeat reload without stacked modifiers, live RPM/stress refresh, successful/failed teleports, snapshot reconstruction, vanilla-leash and virtual attachments, crank persistence and no-Icy regression. Separate JVM restart probes load the same isolated world, checking actual entity ticking, position changes, live power, UUID uniqueness and restoration of original AI baselines. See the [coverage matrix](icy-coverage-matrix.md) for the full scenario inventory.

**Headless game tests cannot establish real client visuals, menu clicks, manual multiplayer packet/raycast behavior or disconnect handling.** Packaging and binary-provenance checks must also be evaluated independently. Never report these as passed solely because server-side GameTests succeed.

## Licensing

CHP is MIT. Icy's distribution has its own upstream license, which must be checked in the exact version being packaged or tested. This integration is independently authored: it copies no Icy implementation, assets or private reference source and redistributes no Icy jar. Optional test dependencies are fetched from their upstream distribution, not mirrored in this repository.
