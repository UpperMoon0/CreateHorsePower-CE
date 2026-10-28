# Icy compatibility coverage matrix

This matrix distinguishes implemented checks from execution evidence. A row is
verified only when its named CI job passes on the PR's exact final head. Source
review and ordinary JUnit are not substitutes for the released-mod runtime.

## Exact artifacts

| Runtime | Icy optional dependency | Required extra dependencies |
|---|---|---|
| Forge 1.20.1, CHP's pinned Forge/Create versions | Modrinth `kXpI9qsc`, `icys-better-horses-2.1.7-forge-1.20.1.jar` | GeckoLib `aC5KMoNg` |
| NeoForge 1.21.1, CHP's pinned NeoForge/Create versions | Modrinth `vfSQ99Gl`, `icys-better-horses-2.1.7-neo-1.21.1.jar` | GeckoLib `TSeTaM0B`, Modonomicon `jH6bsw4F` |
| Each of the above without Icy | No Icy dependency | Existing CHP development dependencies retained |

The compatibility workflow's `icy-runtime` jobs exercise the first two rows.
Normal CI exercises complete no-Icy builds and GameTests. `restart-runtime` runs
all four combinations in separate dedicated-server processes.

## Behavior-to-evidence mapping

| Behavior | Automated evidence | Evidence type / remaining limit |
|---|---|---|
| Exact breed ID allowlist, unknown/cart/wrong namespace exclusion | `IcyProfilesTest` | Plain JUnit; no entity runtime |
| All fifteen released entity factories and profiles | `recognitionAndPolicy` | Actual optional-mod GameTest |
| Owner/stranger authorization, cart and wagon eligibility | `recognitionAndPolicy` | Actual public Icy API; direct policy calls, not full player interaction |
| Body NBT and crank state reload, delayed leash restoration | `replacementAndSavedWorker` | Serialized reload in one JVM; not a process restart |
| Loaded teleport success/failure and exact release hook | `successfulRecallReleasesControl` | Actual Icy method and mixin in development runtime |
| Icy snapshot reconstruction, virtual and vanilla-leash cleanup | `snapshotRecallReleasesVirtualWorker`, `snapshotRecallReleasesLeashedWorker` | Actual upstream reconstruction path; private invocation bypasses public-command authorization |
| Real vanilla-to-breed replacement retains exact worker identity | `vanillaSwapRetainsWorker` | Actual first-tick Icy replacement and later ticks |
| Actual chunk unload/reload, persisted worker/crank identity and resumed orbit | Write phase of `IcyRestartProbe` | Test tickets are removed; loaded chunk and UUID index absence plus `UNLOADED_TO_CHUNK` and new instances are required |
| Real process restart, same UUID/body and crank identity, resumed orbit/output | `IcyRestartProbe`, Python runner, four restart jobs | Two separate JVM PIDs and same saved world; read phase never creates workers |
| Both original NoAI baselines and attachment release after restart | Two restart fixtures, `NoAI=false` and `NoAI=true` before attachment | Actual loaded entities after fresh process; marker/leash cleanup asserted |
| Existing vanilla/TFC/script and generic lifecycle regressions | Standard full CI | Complete existing no-Icy suites; not proof of Icy-specific behavior |
| Trusted-player attach/detach, unauthorized operations and unloaded owner reservation | `permissionInteractionsAndTrust`, `unauthorizedDetachCannotHandleOwnedOrUnavailableHorse`, permission-hint JUnit | Actual interaction methods + real Icy trust store; mock actors |
| Active wagon handoff and cart install/remove | `tickingOrbitAndWagonHandoff`, `activeCartEquipmentPausesAndResumesAfterTicks` | Live ticking/output; real Icy gear/handoff API, third-party wagon represented by a test entity |
| Public loaded/snapshot whistle/send-home success and authorization/bond/unsafe failure | Five `IcyRecallScenarios` methods | Public management API, real snapshots and registered replacement bodies |
| Successful/failed public revival | `revivalRespectsFailureAndReleases` | Actual death record and public revive command; no fabricated CHP reconstruction |
| Crank block removal and both AI baselines | `crankBreakRestoresActiveAi`, `crankBreakPreservesOriginalNoAi` | Actual block lifecycle and ticking entities |
| Worker death and no resurrection | `deadWorkerDoesNotResurrectOrKeepGeneration` | Real damage/removal and later server ticks |
| Duplicate UUID and foreign-crank rejection | `duplicateUuidAndForeignClaimCannotTakeControl` | Actual server index rejects duplicate body; second engine cannot steal claim |
| Owner disappearance after knot transfer | `ownerDisconnectDoesNotOrphanKnotAssignment` | Mock player removal; not a real socket disconnect |
| Same-owner no-drop preservation through old crank reload, direct leash cleanup and block removal | Three `noDropTombstone` lifecycle scenarios and `DeferredDetachDropPolicyTest` | Actual delayed bodies/item counts plus pure policy unit tests |
| Stale-cache item disposition, legacy activity-only and foreign-marker recovery | `unloadedCachedBodyCannotReturnAttachmentTwice`, `activityOnlyDepartureAndForeignRecovery` | Actual saved bodies/markers and later entity ticks |
| Production release-jar optional mixin packaging and linkage | `verify_compat_jar.py` in both optional CI jobs | Checks actual jar config, metadata, hook/plugin classes and any declared refmap; execution remains development-loader runtime |
| Client animation/rendering and player-driven UI | Manual acceptance still required | No automated dedicated-server visual evidence |

## Separate-process probe contract

Run `python3 scripts/run_restart_probe.py --project forge-1.20.1 --icy true`
(or `neoforge-1.21.1`, and `--icy false`). The runner creates a fresh directory
under `<project>/build/restart-evidence/`; it cannot select or delete an existing
world. The dedicated server binds localhost on an ephemeral port and creates a
flat `probe-world`. Test-only chunk tickets let real workers tick without players.
No live server or TFG world is used.

The write phase creates two distant fixtures outside spawn tickets and observes
at least twenty real worker ticks, position displacement, and nonzero generated RPM.
It saves, removes only its test chunk tickets, and waits until the loaded-chunk
queries and authoritative entity indices are both empty. It requires the old bodies
to report `UNLOADED_TO_CHUNK`, then re-forces the test chunks and requires new loaded
entity/block-entity instances with the same persisted identities and fresh orbit ticks.
It saves again and stops normally. A retained external ticket causes a timeout,
not a simulated unload or a skipped assertion.
The read phase starts a new JVM against exactly that world, resolves the saved
worker UUIDs through the server entity index, checks unique nearby bodies and
unchanged crank identities, observes resumed ticking/orbit/output, and releases
both fixtures. Original AI baselines, leash state, and ownership markers are checked.

Each phase writes explicit PASS/FAIL properties with JVM PID, worker type/UUID,
crank UUID, tick delta, displacement, and live RPM. The runner requires both PASS
files, distinct PIDs and equal persisted identities; absent evidence, timeouts, or
an early clean server exit all fail. CI uploads phase logs and machine-readable
`restart-summary.json`. These checks count actual ticking, not saved `Speed` NBT.

The opt-in GameTest job also compares the number of required scenarios declared
in its wrapper against started/passed counts in the actual server log. A namespace
mistake or skipped suite cannot be described as a compatibility pass.
