# Feature Compatibility Matrix

Enforced and reviewed with every release. "Full" means identical behavior to the rest of the mod on that version unless the row explicitly describes a loader-specific implementation.

| Feature | NeoForge 1.21.1 | Forge 1.20.1 |
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

Rule: a feature may only be advertised as "supported" on a version when this
matrix lists it as Full there. When porting a missing feature, move the row to
Full in the same change.
