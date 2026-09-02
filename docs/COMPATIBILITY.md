# Feature Compatibility Matrix

Enforced and reviewed with every release. "Full" means identical behavior to the rest of the mod on that version unless the row explicitly describes a loader-specific implementation.

| Feature | NeoForge 1.21.1 | Forge 1.20.1 |
|---|---|---|
| Horse Crank block + block entity (attach/detach/wrench/comparator) | Full | Full |
| Worker resolution (Data Maps/built-ins + tags/config fallback) | Full | Full |
| Path evaluation (WEIGHTED_AVERAGE / WORST_BLOCK / LEGACY) | Full | Full |
| Per-animal stat scaling | Full | Full |
| Believable visual gait decoupled from mechanical RPM | Full | Full |
| Redstone modes (`HIGH_STOPS`, `HIGH_RUNS`, `IGNORE`) | Full | Full |
| Worker / attachment / leash datapack tags | Full | Full |
| Durable unloaded-worker detach + orphan leash recovery | Full | Full |
| Optional TerraFirmaCraft worker + terrain defaults | Full (conditional Data Maps + shared registry-ID/path fallback) | Full (shared registry-ID/path fallback) |
| `/createhorsepower` diagnostics commands | Full | Full |
| Transition-based `diagnostics.debugLogging` | Full | Full |
| Create Goggles tooltips | Full | Full |
| Shared behavioral tests (JUnit) | Full | Full |
| Real-game lifecycle coverage (GameTest) | Full | Full |
| Worker/path Data Maps (`createhorsepower:worker_stats`, `path_stats`) | Full | Not available on Forge; use tags or config |
| Jade HUD integration | Full | Full |
| KubeJS startup profiles and lifecycle events | Full | Not yet ported (no Forge KubeJS script entry point; the shared profile registry cannot be populated from scripts) |
| Ponder scenes | Full | Not yet ported |
| Datagen (recipes, loot tables, tags, data maps) | Full | Not yet ported (JSONs are hand-maintained under `forge-1.20.1/src/main/resources`) |

TerraFirmaCraft is never a required runtime dependency. CE only activates its built-in TFC defaults for registry IDs that actually exist; NeoForge's exact TFC species Data Map entries are additionally guarded by `neoforge:mod_loaded`.

Rule: a feature may only be advertised as "supported" on a version when this
matrix lists it as Full there. When porting a missing feature, move the row to
Full in the same change.
