# Icy's Better Horses integration

## Contract and target evidence

The integration targets the official **2.1.7** release on both CHP platforms:

| CHP platform | Icy source branch / inspected commit | Released artifact | Modrinth version |
|---|---|---|---|
| Forge 1.20.1 / Create 6.0.8 | `1.20.1-forge` / `237180495699c37c139c2d45ffa8a77f88dd2bb4` | `icys-better-horses-2.1.7-forge-1.20.1.jar` | `kXpI9qsc` |
| NeoForge 1.21.1 / Create 6.0.8 | `1.21.1-neo` / `02ea00b842f4275f0c5a35679293ee9a3651bcd9` | `icys-better-horses-2.1.7-neo-1.21.1.jar` | `vfSQ99Gl` |

The [official distribution](https://modrinth.com/mod/icys-better-horses) links to
[the upstream repository](https://github.com/Icywhenhot/Icy-s-Better-Horses).
Its default `26.2-remodled-fab` branch is not a source baseline for these targets.
Both downloaded release jars were inspected for actual metadata and API signatures;
source inspection alone is not evidence that a runtime works.

Icy's mod ID is `icys_better_horses`; entity registry IDs use `icys-better-horses`.
All fifteen released breed entity IDs receive the same profile and legacy LARGE
balance tier as vanilla horses. They remain subject to pack profiles, machine
policies, taming/baby/undead configuration, and individual movement-speed and
max-health attribute scaling. Unknown IDs and the horse cart are not workers.

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

## Unsupported and unverified combinations

CHP has no Fabric build and no 1.21.11/26.x target, so Icy artifacts for those
combinations cannot be installed with these CHP artifacts. Do not mix Forge and
NeoForge jars. Older Icy releases and future API changes are not covered by the
2.1.7 acceptance contract. Unknown custom breed IDs require pack worker profiles.
Simultaneous transport controllers are deliberately excluded as described above.

## Reproducible verification

Default `./gradlew verify` builds/tests both CHP variants and runs the existing
no-Icy lifecycle, reload, orphan recovery and kinetic integration suites. The added
recognition scenario explicitly asserts that Icy is absent in that mode.

For each `forge-1.20.1` and `neoforge-1.21.1` project, run:

```sh
./gradlew :forge-1.20.1:build :forge-1.20.1:runGameTestServer -PicyCompatibilityTest=true --no-daemon
./gradlew :neoforge-1.21.1:build :neoforge-1.21.1:runGameTestServer -PicyCompatibilityTest=true --no-daemon
```

The opt-in property resolves the exact released Icy jars, GeckoLib, and NeoForge's
required Modonomicon. It runs a separate `createhorsepower_icy` test namespace in
isolated GameTest worlds, rather than assuming vanilla fixtures survive Icy's
first-tick entity replacement. Normal CI continues running the complete no-Icy suite.
The optional CI workflow uploads logs and JUnit evidence.

Acceptance scenarios exercise all fifteen real entity factories, owner rejection,
transport exclusion, custom body save/load with delayed leash restoration and crank
NBT reload, successful real teleport, and Icy's actual snapshot reconstruction path
with virtual and vanilla-leash attachments. Unit tests reject wrong namespace/cart/unknown IDs.
Profile checks run inside the real game to avoid bootstrapping Minecraft registries in plain JUnit.

These are automated dedicated-server scenarios. A real two-process world restart,
client animation/rendering, player-driven menus, trusted-player UX, and manual
in-game acceptance still require verification. Passing source checks or serialized
reload tests must not be described as those manual checks passing.

## Licensing

CHP is MIT. Icy 2.1.7 identifies `BML-1.0` in both released jars and includes its
license. This integration is independently authored; it copies no Icy implementation,
assets, or private reference source and redistributes no Icy jar. Test dependencies
are downloaded from their official distribution, not mirrored in this repository.
