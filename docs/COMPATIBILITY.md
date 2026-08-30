# Feature Compatibility Matrix

Enforced and reviewed with every release. "Full" means identical behavior to the rest of the mod on that version.

| Feature | NeoForge 1.21.1 | Forge 1.20.1 |
|---|---|---|
| Horse Crank block + block entity (attach/detach/wrench/comparator) | Full | Full |
| Worker resolution (tags + config fallback) | Full | Full |
| Path evaluation (WEIGHTED_AVERAGE / WORST_BLOCK / LEGACY) | Full | Full |
| Per-animal stat scaling | Full | Full |
| Redstone modes (`HIGH_STOPS`, `HIGH_RUNS`, `IGNORE`) | Full | Full |
| Worker / attachment / leash datapack tags | Full | Full |
| `/createhorsepower` diagnostics commands | Full | Full |
| Create Goggles tooltips | Full | Full |
| Shared behavioral tests (JUnit) | Full | Full |
| Worker/path Data Maps (`createhorsepower:worker_stats`, `path_stats`) | Full | Not available on Forge; use tags or config |
| Jade HUD integration | Full | Not yet ported |
| KubeJS startup profiles and lifecycle events | Full | Not yet ported (no Forge KubeJS script entry point; the shared profile registry cannot be populated from scripts) |
| Ponder scenes | Full | Not yet ported |
| Datagen (recipes, loot tables, tags, data maps) | Full | Not yet ported (JSONs are hand-maintained under `forge-1.20.1/src/main/resources`) |

Rule: a feature may only be advertised as "supported" on a version when this
matrix lists it as Full there. When porting a missing feature, move the row to
Full in the same change.
