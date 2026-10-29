# Optional Create: Bionics integration

## Inspected sources and scope

Create Horse Power CE supports the **Oxhauler**, the robotic draft animal in Create: Bionics, on both supported loaders. The integration was written against these upstream source trees:

- Forge 1.20.1: [Dshbwlto/Create-Bionics-1.20.1](https://github.com/Dshbwlto/Create-Bionics-1.20.1) (mod 2.0.0; Create 6.0.8)
- NeoForge 1.21.1: [Dshbwlto/Create-Bionics](https://github.com/Dshbwlto/Create-Bionics) (mod 2.5.0; Create 6.0.9)

The 1.20.1 Oxhauler derives from `AbstractHorse`; 1.21.1 derives from `MultiPartRobot` / `AbstractRobot` / `TamableAnimal`. Neither is imported or linked by CHP. The exact registry ID `createbionics:oxhauler` selects the common integration. The other Bionics robots have different roles (combat, aquatic, stationary, etc.) and are not automatically treated as draft workers.

The robot's public `getFuel()`, `setFuel(int)`, and `getAssembly()` methods are read via a cached optional adapter. Forge 1.20.1 exposes `hasBlazeCake()` and **does not** accept negative fuel; NeoForge 1.21.1 also exposes `isFueled()` and uses `-1` as its creative-fuel sentinel. The adapter recognizes these API variants separately and fails closed on unknown variants. Finite fuel charges are verified by reading the value back after each setter invocation. An exception or ineffective/no-op setter permanently blocks that loaded entity from powering the crank, and the crank immediately stops and refreshes its kinetic output. This prevents an API mismatch from providing free power without crashing a Bionics-free installation.

## In-game contract

1. Finish assembling the Oxhauler (assembly **3**), give it fuel (or infinite/creative fuel), and ensure nobody is riding or carrying it.
2. Lead it to a valid Horse Crank track and attach it with the standard CHP lead interaction. Clicking the main robot body enters vanilla `Mob.interact()` and processes leads before `mobInteract()`. **NeoForge only:** clicking the Oxhauler's installed plough/harvester parts with a lead is rerouted through the **player's full `interactOn(parent, hand)` pipeline** via a narrowly scoped player-interact event; otherwise Bionics' `RobotPartEntity.interact()` would bypass vanilla lead processing and mount the player. The full player pipeline is necessary to restore creative-mode lead counts and emit NeoForge's `PlayerDestroyItemEvent` when the final survival lead is consumed. Other items and other robots retain their original multipart interactions. No Bionics class is directly referenced.
3. The Oxhauler has default output of **4.5 RPM, 800 SU**, a **3-block** movement radius, and is classified **LARGE** for legacy balance. CHP's path and global multipliers apply normally; fixed robot speed/health do not get animal breeding/stat variance.
4. While actively moving the crank, finite Bionics fuel decreases by **1 unit per server tick**, matching Bionics' own ridden fuel burn. Stopped cranks, invalid paths, missing workers, redstone stops, and unloaded workers do not burn fuel. When fuel reaches zero, power stops on the next tick. Failed fuel writes stop the crank immediately and prevent restart for that loaded Oxhauler entity.
5. Work also stops when the Oxhauler is incomplete, in water, or used as a mount/passenger. **Taming policy:** `requireTamedHorse` and `requiresTamed` apply to livestock, not to the assembled Oxhauler. Forge models the robot as tamed; NeoForge's `TamableAnimal` superclass defaults to untamed and the mod provides no taming method. Both robots therefore bypass only the animal-taming flag. Players still need a working assembly, fuel, an appropriate lead/attachment and permission from any configured ownership integrations (such as Icy) to use the crank. No new claim/owner permission is invented for Bionics. Existing attachment cleanup, worker UUID, NoAI restoration, chunk recovery, and crank persistence remain CHP's shared lifecycle.
6. Forge's creative Blaze Cake flag and NeoForge's `-1` infinite fuel never deplete. Bionics keeps ownership of fuel serialization, inventory, assembly, attachments, animations, and robot parts.

Both mods remain optional relative to each other. Pack-authored `createhorsepower/worker_profiles` and machine-specific overrides retain higher priority than the built-in output values. The robot's *operational* fuel/assembly/mount restrictions cannot be overridden with a datapack.

## Reproducible real-mod CI acceptance (1.2.10)

The opt-in `-PbionicsCompatibilityTest=true` lane is isolated from ordinary installations and standard **no-Bionics** builds. `createhorsepower_bionics` is its exclusive GameTest namespace. Every test verifies the actual Oxhauler registry, mod version, and assembly/fuel APIs **after entity insertion**; CI refuses startup-only or skipped-test success.

- **Forge 1.20.1**: upstream source declares Bionics **2.0.0**, but no officially published Forge binary has been verified on upstream GitHub releases or Modrinth. CI builds pinned commit `d597264a86c0e6d655e4bc9ffec23be292382882` into a real Bionics jar. This is **source-built compatibility evidence, not released-binary acceptance**. A published binary and digest are still required for the release-jar criterion.
- **NeoForge 1.21.1**: actual published Bionics **2.5.0** (Modrinth `fG2UIEqS`, Maven `maven.modrinth:lCwu0vBo:fG2UIEqS`). Requires **Create 6.0.9+**; the NeoForge project now uses Create 6.0.9-215 and its corresponding development versions.
- The real-mod GameTests exercise assembly/fuel eligibility, actual require-tamed policy, rider/water refusal, invalid-track stop, at least two complete revolutions, generated crank speed and finite fuel, redstone stop/no fuel loss, and detach/reattach. NeoForge also uses the **actual 2.5.0 plough and harvester PartEntity objects** for server-side survival-last-lead and creative lead refund/reattach interactions, not test stand-ins. The CI assertion requires every test to actually execute.
- A separate-process restart probe runs two **fresh JVMs** in one isolated world and checks the same entity UUID, crank UUID, assembly, fuel persistence, subsequent movement and output.
- Normal CI remains Bionics-free and must pass on both loaders. Icy's Better Horses compatibility tests remain separate.

### Remaining gaps before merge

Headless GameTests **do not prove real client multiplayer raycasts and interaction packets** targeting the NeoForge plough and harvester parts. Run a real NeoForge client against a dedicated server with released Bionics 2.5.0; verify lead attach and removal on both part hitboxes in creative and survival (including last lead and both hands), and that other controls still mount normally. Retain client/server logs and packet evidence. Keep the matrix entry provisional until that acceptance and a verifiable Forge released 2.0.0 binary are available.

## Verification

- `./gradlew testAllVersions verifySharedSources verifySharedResources` checks shared data/policy and JUnit regressions on both loader targets.
- `./gradlew buildAll` checks both production classpaths with **no Bionics compile dependency**.
- NeoForge GameTests cover creative offhand lead-count restoration, last-lead survival consumption with exactly one `PlayerDestroyItemEvent`, and ordinary survival stack decrements, using a vanilla leashable mob as the interaction parent. The actual optional multipart hitbox requires Bionics-present in-game acceptance.
- Manual in-game acceptance with released Bionics jars is still required for: `requireTamedHorse=true` on Forge and NeoForge; NeoForge lead clicks on **central body, plough, and harvester hitboxes** (including with both hands where applicable); full 360-degree motion (especially multipart 1.21.1); reload recovery, pack overrides, visual effects, and fuel-on-restart behavior. Do not mistake source-level verification for completed two-mod runtime testing.
