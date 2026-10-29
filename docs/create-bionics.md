# Optional Create: Bionics integration

## Inspected sources and scope

Create Horse Power CE supports the **Oxhauler**, the robotic draft animal in Create: Bionics, on both supported loaders. The integration was written against these upstream source trees:

- Forge 1.20.1: [Dshbwlto/Create-Bionics-1.20.1](https://github.com/Dshbwlto/Create-Bionics-1.20.1) (mod 2.0.0; Create 6.0.8)
- NeoForge 1.21.1: [Dshbwlto/Create-Bionics](https://github.com/Dshbwlto/Create-Bionics) (mod 2.5.0; Create 6.0.9)

The 1.20.1 Oxhauler derives from `AbstractHorse`; 1.21.1 derives from `MultiPartRobot` / `AbstractRobot` / `TamableAnimal`. Neither is imported or linked by CHP. The exact registry ID `createbionics:oxhauler` selects the common integration. The other Bionics robots have different roles (combat, aquatic, stationary, etc.) and are not automatically treated as draft workers.

The robot's public `getFuel()`, `setFuel(int)`, and `getAssembly()` methods are read via a cached optional adapter. Forge 1.20.1 exposes `hasBlazeCake()` and **does not** accept negative fuel; NeoForge 1.21.1 also exposes `isFueled()` and uses `-1` as its creative-fuel sentinel. The adapter recognizes these API variants separately and fails closed on unknown variants. Finite fuel charges are verified by reading the value back after each setter invocation. An exception or ineffective/no-op setter permanently blocks that loaded entity from powering the crank, and the crank immediately stops and refreshes its kinetic output. This prevents an API mismatch from providing free power without crashing a Bionics-free installation.

## In-game contract

1. Finish assembling the Oxhauler (assembly **3**), give it fuel (or infinite/creative fuel), and ensure nobody is riding or carrying it.
2. Lead it to a valid Horse Crank track and attach it with the standard CHP lead interaction. Clicking the main robot body enters vanilla `Mob.interact()` and processes leads before `mobInteract()`. **NeoForge only:** clicking the Oxhauler's installed plough/harvester parts with a lead is routed to the parent mob's normal interaction via a narrowly scoped player-interact event; otherwise Bionics' `RobotPartEntity.interact()` would bypass vanilla lead processing and mount the player. Other items and other robots retain their original multipart interactions. No Bionics class is directly referenced.
3. The Oxhauler has default output of **4.5 RPM, 800 SU**, a **3-block** movement radius, and is classified **LARGE** for legacy balance. CHP's path and global multipliers apply normally; fixed robot speed/health do not get animal breeding/stat variance.
4. While actively moving the crank, finite Bionics fuel decreases by **1 unit per server tick**, matching Bionics' own ridden fuel burn. Stopped cranks, invalid paths, missing workers, redstone stops, and unloaded workers do not burn fuel. When fuel reaches zero, power stops on the next tick. Failed fuel writes stop the crank immediately and prevent restart for that loaded Oxhauler entity.
5. Work also stops when the Oxhauler is incomplete, in water, or used as a mount/passenger. **Taming policy:** `requireTamedHorse` and `requiresTamed` apply to livestock, not to the assembled Oxhauler. Forge models the robot as tamed; NeoForge's `TamableAnimal` superclass defaults to untamed and the mod provides no taming method. Both robots therefore bypass only the animal-taming flag. Players still need a working assembly, fuel, an appropriate lead/attachment and permission from any configured ownership integrations (such as Icy) to use the crank. No new claim/owner permission is invented for Bionics. Existing attachment cleanup, worker UUID, NoAI restoration, chunk recovery, and crank persistence remain CHP's shared lifecycle.
6. Forge's creative Blaze Cake flag and NeoForge's `-1` infinite fuel never deplete. Bionics keeps ownership of fuel serialization, inventory, assembly, attachments, animations, and robot parts.

Both mods remain optional relative to each other. Pack-authored `createhorsepower/worker_profiles` and machine-specific overrides retain higher priority than the built-in output values. The robot's *operational* fuel/assembly/mount restrictions cannot be overridden with a datapack.

## Verification

- `./gradlew testAllVersions verifySharedSources verifySharedResources` checks shared data/policy and JUnit regressions on both loader targets.
- `./gradlew buildAll` checks both production classpaths with **no Bionics compile dependency**.
- Manual in-game acceptance with released Bionics jars is still required for: `requireTamedHorse=true` on Forge and NeoForge; NeoForge lead clicks on **central body, plough, and harvester hitboxes** (including with both hands where applicable); full 360-degree motion (especially multipart 1.21.1); reload recovery, pack overrides, visual effects, and fuel-on-restart behavior. Do not mistake source-level verification for completed two-mod runtime testing.
