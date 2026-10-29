# Optional Create: Bionics integration

Create Horse Power CE recognizes the **Oxhauler** robotic draft worker on supported **Forge** and **NeoForge** builds. Both mods remain optional relative to each other: CE does not link Bionics classes or require Bionics in normal installations. The specific artifact/dependency pins and acceptance status for a release are recorded in its [versioned Bionics test record](../changelog/), not in this evergreen contract.

## Integration boundary

Only the registry ID `createbionics:oxhauler` selects this integration. Other Bionics robots are not automatically draft workers. CHP reads the public robot `getFuel()`, `setFuel(int)`, and `getAssembly()` methods through an optional, cached adapter. The two loader implementations have different fuel APIs: Forge exposes a creative Blaze Cake flag, while NeoForge exposes `isFueled()` and its own infinite-fuel sentinel. CHP recognizes each supported API variant separately and rejects unknown/ineffective fuel-setter behavior rather than granting free kinetic power.

Bionics still controls robot inventory, assembly, fuel serialization, appearance, animations and multipart machinery. CHP handles crank output, worker assignment and safe recovery through its shared animal-power engine.

## In-game contract

1. Fully assemble the Oxhauler (**assembly value 3**), fuel it (or use the supported creative fuel), and keep it unoccupied and out of water.
2. Lead it to a valid Horse Crank path and attach it through CHP's standard lead/attachment interaction. Main-body clicks pass through the normal entity/player interaction pipeline. On NeoForge, lead clicks on installed plough or harvester `PartEntity` hitboxes are redirected to the parent entity's **full player interaction path**, preserving survival last-lead consumption, creative lead counts and item-destruction events. Other tools and unrelated mobs keep their usual Bionics behavior.
3. The built-in Oxhauler worker profile provides **4.5 RPM, 800 SU**, a **3-block** radius and a large-worker classification before overrides. Worker and path multipliers apply normally; robot output does not inherit animal breeding/health variation.
4. Finite fuel burns by **one unit for each active server tick** powering the crank. An idle, invalid, disabled, detached or unloaded worker does not burn crank fuel. Exhausted fuel stops power. If the real upstream setter fails or does not update the value, the crank stops generating immediately and fails closed.
5. Incomplete assembly, water and rider/passenger use block work. Livestock-oriented `requireTamedHorse` / `requiresTamed` settings do **not** prevent an assembled robot from working: the NeoForge robot inherits an untamed superclass, not a user-taming interaction. This exception changes only the livestock taming rule; attachment/ownership policy, valid track and robot state checks still apply.
6. Creative/infinite fuel does not deplete. Worker UUID, attachment ownership, AI restoration, deferred detach, leash recovery, and persistence follow CHP's normal lifecycle. Pack-authored `createhorsepower/worker_profiles` and machine overrides can change output, but cannot bypass the runtime assembly, fuel, mount and water restrictions.

## Reproducible verification methodology

Run the repository's **ordinary no-Bionics builds and GameTests** for both supported loaders, and then the separately isolated real-mod `-PbionicsCompatibilityTest=true` suite. The suite must:

- Load a **real** upstream Bionics mod jar and check its registry, mod identity/version and reflective public APIs after the Oxhauler has entered the level (its pre-spawn assembly getter can return an implementation sentinel).
- Spawn and attach an assembled/fueled real Oxhauler; measure **two complete real orbits**, sustained generated RPM and fuel debit. Assert no stalled/reversed movement; check incomplete assembly, empty/infinite fuel, rider, water, taming policy, redstone shutdown, invalid paths and detach/reattach.
- Assert that the **expected number of named GameTests ran and passed**. Loading the server or silently skipping tests must not pass the gate.
- Save and reopen an **isolated world in separate server JVMs**, with the real Bionics jar present, and verify assembly, remaining fuel, attachment/crank identity, subsequent movement and output.
- On NeoForge, exercise the **actual Bionics plough and harvester part entities** with survival and creative server interactions, including last-lead use, rather than fabricated test entities.

**Server-side multipart tests are not client acceptance.** Real client raycasting, target hitbox selection, end-to-end interaction packets, render/animation results, and survival/creative behavior in multiplayer require separate client + dedicated-server evidence. Do not report those as verified merely because the headless GameTests are green.

The current CI wiring lives in [the compatibility workflow](../.github/workflows/bionics-compatibility.yml), and the [main CI workflow](../.github/workflows/ci.yml) makes both loader lanes required. Dependency pins, source-versus-published-binary provenance, specific run outcomes and outstanding release acceptance belong in [release-scoped test evidence](../changelog/). Verify those records and the latest workflow run when preparing a release.
