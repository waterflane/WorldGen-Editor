# Version 3 compatibility verification

Local NeoForge 21.1.230 / Minecraft 1.21.1, 2026-10-23. Generation versions 1 and 2 keep their previous algorithms; these composition checks target **new version 3 worlds**.

## Locked artifacts

`tools/compatibility-lock.json` records author-distributed Modrinth artifact URLs, version IDs and SHA-512 hashes. These are test dependencies and are not bundled in WGE.

| Project | Version | Supporting libraries in this profile |
| --- | --- | --- |
| TerraBlender | 4.1.0.8 | — |
| Biomes O' Plenty | 21.1.0.14 | GlitchCore 2.1.0.2, TerraBlender 4.1.0.8 |
| Terralith | 2.6.2 | Lithostitched 1.8.1, Apollib 1.2.0 |
| Tectonic | 3.0.28-neoforge-21.1 | Lithostitched 1.8.1, Apollib 1.2.0 |

The locked Tectonic resources reference Lithostitched functions; this profile therefore includes Lithostitched even though Tectonic's mod metadata alone does not list it. Apollib includes JSON5-java 3.0.0. Loom strips nested dependencies in development: the preparation script verifies that embedded jar's hash and derives a test-only GAMELIBRARY manifest for its unchanged classes. Do not distribute that development workaround with WGE.

## Matrix

Machine-readable results: [`tools/compatibility-results.json`](../tools/compatibility-results.json). Each row requires a successful GameTest and a newly written result, not just successful mod startup.

| Profile | Status |
| --- | --- |
| Minecraft + WGE | Passed |
| Painted map + WGE | Passed |
| Disabled version 3 preset | Passed |
| TerraBlender | Passed |
| Biomes O' Plenty | Passed |
| Terralith | Passed |
| Tectonic | Passed |
| Terralith + Tectonic | Passed |
| Biomes O' Plenty + Terralith | Passed |
| Biomes O' Plenty + Tectonic | Passed |
| All four projects | Passed |

## What is checked

- The actual generator contains WGE's version 3 density composition. Tectonic's noise namespace must occur in the active RandomState graph.
- WGE land produces solid terrain above sea level. Two outside-island columns are real ocean chunks with an ocean floor at least five blocks below sea level, checked independently against base generation.
- Installed biome mods contribute actual chunk biomes. Their surface rules survive composition; the report also lists sampled blocks from their generated surface chunks.
- Delegate cave/deep-dark biomes survive surface climate rules, and a generated underground chunk contains cave air.
- Saving writes the original registry noise-settings reference. Reading the actual compressed `level.dat` and pinned snapshot reconstructs the generator and matches three base-height columns. The expanded runtime graph is never serialized into saves.
- Minecraft-only, painted and disabled profiles each compare 84 actual block biome IDs and 21 base heights with the standalone Java preview: **zero mismatches**. They cover negative coordinates, tile seams, surface, Y=300, Y=0, Y=-32 and distant ocean. Version 2's separate verification includes a known river, lush cave and shore transition.

These finite samples establish the tested profiles, not every possible seed, mod version or datapack order. Surface-rule preservation and sampled blocks do not prove every mod feature appears everywhere. Structures and feature lakes are not simulated by the standalone preview. The saved-data check reconstructs from disk; it is not an automated interactive client restart. Optimizer mods are outside this matrix. Exact preview remains **Minecraft + WGE** and is not a modpack preview.

## Reproduce

Start the prepared Java preview first, then from the mod repository with Java 21+:

```powershell
python tools/prepare_compatibility.py
python tools/run_compatibility.py
# Or one profile:
python tools/run_compatibility.py combined
```

Each profile uses `neoforge/build/compatibility-runs/<profile>-v3/`, with independent logs under `build/compat-<profile>.log`. The runner terminates its own process tree after 180 seconds and records failures. User saves are not used. Profile dependencies require the preparation script; subsequent Gradle runs are offline. Verification sources, test resources and third-party libraries are excluded from the release jar.

For an optional client smoke test, install the locked mods in a disposable NeoForge 1.21.1 instance, give a map a JSON `name`, add its export or editor project to `config/worldgen_editor/presets`, and select **WGE: <map name>** with its displayed seed. Inspect painted land and outside ocean, save, close and reopen; the coastline must remain in place. `/worldgen_editor disable` must report that a restart is required and must not change generation while the world is running.
