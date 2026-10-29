# Feature Compatibility Matrix

This matrix describes supported loader **families**, not a promise that every upstream dependency release is compatible. "Full" means the documented behavior is implemented for that loader, subject to the dependencies selected for each CE release. See the [release history](../changelog/) and [current CI](../.github/workflows/ci.yml) for exact supported artifacts and verified builds.

| Feature | NeoForge | Forge |
|---|---|---|
| Horse Crank block + block entity (attach/detach/wrench/comparator) | Full | Full |
| Worker resolution (shared JSON + built-ins/tags/config; optional Data Maps on NeoForge) | Full | Full |
| Shared worker-profile JSON (`createhorsepower/worker_profiles`) | Full | Full |
| Path evaluation (WEIGHTED_AVERAGE / WORST_BLOCK / LEGACY) | Full | Full |
| Per-animal stat scaling | Full | Full |
| Believable visual gait decoupled from mechanical RPM | Full | Full |
| Redstone modes (`HIGH_STOPS`, `HIGH_RUNS`, `IGNORE`) | Full | Full |
| Worker / attachment / leash datapack tags | Full | Full |
| Data-driven attachment/harness profiles and backend-safe recovery | Full | Full |
| Machine-specific worker profile overrides | Full | Full |
| Reusable animal-power engine / bounded worker assignments | Full | Full |
| Durable unloaded-worker detach + orphan leash recovery | Full | Full |
| Optional TerraFirmaCraft worker + terrain defaults | Full (shared registry-ID/path fallback; explicit Data Maps/KubeJS can override) | Full (shared registry-ID/path fallback; legacy config can override) |
| Optional Icy's Better Horses integration | Full within the [integration contract](icy-better-horses.md) | Full within the [integration contract](icy-better-horses.md) |
| Optional Create: Bionics Oxhauler integration | [Server behavior and real multipart part interactions tested; client raycast/packet acceptance is separate](create-bionics.md) | [Server Oxhauler behavior exercised by a real-mod test suite; binary provenance tracked per release](create-bionics.md) |
| `/createhorsepower` diagnostics commands | Full | Full |
| Transition-based `diagnostics.debugLogging` | Full | Full |
| Create Goggles tooltips | Full | Full |
| Shared behavioral tests (JUnit) | Full | Full |
| Real-game lifecycle coverage (GameTest) | Full | Full |
| Worker/path Data Maps (`createhorsepower:worker_stats`, `path_stats`) | Full (explicit pack overrides; CE bundled defaults are not shipped as Data Maps) | Not available on Forge; shared worker JSON provides exact worker/machine authoring, while paths use tags/config/bundled fallback |
| Jade HUD integration | Full | Full |
| KubeJS startup profiles and lifecycle events | Full | Not yet ported (no Forge KubeJS script entry point; the shared profile registry cannot be populated from scripts) |
| Ponder scenes | Full | Not yet ported |
| Datagen (recipes, loot tables, tags) | Full | Full |
| NeoForge Data Map datagen | Full | Not applicable (Forge has no Data Map API) |

TerraFirmaCraft is never a required runtime dependency. CE only activates its built-in TFC defaults for registry IDs that actually exist. Those defaults are shared bundled fallbacks rather than NeoForge Data Map entries; shared worker JSON can override them on both loaders, with KubeJS/Data Maps providing additional NeoForge-only layers according to documented precedence.

Rule: advertise a feature as supported for a loader only when the implementation and relevant tests justify it. Track release-specific verification, pinned dependencies and limitations in the matching [versioned release notes](../changelog/). Real server-side checks do not imply successful graphical-client multiplayer interaction or visual QA.
