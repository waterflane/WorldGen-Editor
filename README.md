# WorldGen Editor

A JSON-driven island world generator for **Minecraft 1.21.1 / NeoForge 21.1.230–21.1.x**, requiring Java 21 or later.
Fabric, Forge and Architectury API are not runtime dependencies or supported targets.

Select **WGE: Islands**, **WGE: Archipelago** or **WGE: Small Island** when creating a world. Ordinary world types keep their own generators.
WGE preserves the Minecraft noise generator, modifies continentalness and selects island/ocean biomes. The retained `island_final_density` codec is legacy compatibility code; current presets do not use it.

## Configuration and saved worlds

The initial selection comes from the WGE world type. Global defaults live in:

```text
config/worldgen_editor/worldgen_editor.json
config/worldgen_editor/presets/default.json
config/worldgen_editor/presets/archipelago.json
config/worldgen_editor/presets/small_island.json
```

Minimal main configuration:

```json
{ "enabled": true, "active_preset": "default" }
```

Minimal preset:

```json
{
  "outer_ocean": "minecraft:deep_ocean",
  "entries": [
    { "name": "Spawn", "x": 0, "z": 0, "radius": 800, "temperature": "standard", "noise": { "seed": "spawn" } }
  ]
}
```

Entries support `island`, `ocean` and `archipelago`; radii/stretch, rotation, superellipse shape, roughness, shore width, climate and biome exclusions (IDs or `#tags`). Legacy coordinate/radius aliases remain supported.
Ordinary island and ocean masks depend on the world seed; archipelago placement and child outlines intentionally depend on `noise.seed` independently of the world seed.
Invalid numeric values, overflowing dimensions/frequency ladders and unsupported generation versions are rejected.

On first load, the effective preset is pinned in `<world>/worldgen_editor/worldgen_editor.json`, together with the per-world flag and `generation_version`.
Existing saves without a version retain version 1. New WGE world types select version 2, which fixes climate selection and preserves underground biomes.
A restart uses the pinned snapshot rather than a potentially edited global preset. For older worlds, the snapshot captures the configuration available at the first load after this update; past configurations cannot be reconstructed from a seed.

Operator commands:

```text
/worldgen_editor status
/worldgen_editor enable
/worldgen_editor disable
/worldgen_editor preset <name>
/worldgen_editor reload
/worldgen_editor migrate
```

`preset` selects and pins a preset for the current WGE world. `reload` explicitly imports that preset again from global configuration. Failed changes leave the previous snapshot active.
`migrate` explicitly opts an old world into generation version 2. These operations affect future generation and can create boundaries against existing chunks; existing chunks are never rewritten.
An empty/disabled mask delegates to the underlying generator, rather than predicting an all-ocean world.

## Compatibility

The optional TerraBlender adapter initializes the actual delegate and surface settings in version 2 worlds. It is tested with TerraBlender NeoForge 4.1.0.8 and a separately registered test biome. Version 1 retains old behavior until explicit migration.
After migrating a running version 1 world with TerraBlender installed, restart the world to initialize that optional adapter before exploring new areas.
The preview engine currently supports **Minecraft + WGE**, not the biome registrations of an arbitrary modpack.
Full integration with terrain replacements such as Terralith/Tectonic, and compatibility with worldgen optimizers, are separate future work.

## Build and test

```powershell
.\gradlew.bat build --no-daemon
```

The loader-ready artifact is `neoforge/build/libs/worldgen-editor-neoforge-0.3.0.jar`, also copied into `release/` after the checks pass.
The build runs config/mask golden tests, world snapshot persistence tests, generation-context tests and 576 legacy biome-selection golden cases.
Golden fixtures were generated from commit `c792da7`; the scripts in `tools/` document regeneration.

For a real NeoForge world vs preview comparison, first start the Java preview, then run:

```powershell
.\gradlew.bat :neoforge:runVerification --no-daemon
```

This uses a private GameTest world under `neoforge/build/verification-run`, and checks actual chunk biomes at surface/300/64/0/-32 plus base heights. Results are written to `parity-result.json` inside that directory.
Fixed seed-zero control points also assert an actual river, lush caves and samples on both sides of an island shore transition.
The test-only flat-preset override and test biome are excluded from the release jar.

Optional TerraBlender verification (the download script checks the published SHA-512):

```powershell
python tools/prepare_terrablender_test.py
.\gradlew.bat :neoforge:runVerification -PwithTerraBlender --no-daemon
```

The dependency is test-only and is not bundled with WGE. This separate run checks delegate initialization and generation of a custom TerraBlender biome; it does not compare a modpack against the vanilla-only preview.
Its independent world and `terrablender-result.json` are stored under `neoforge/build/terrablender-verification-run`.

## Exact preview

Keep the companion [WorldGen Editor Preview](https://github.com/waterflane/WorldGen-Editor-Preview) beside this repository.

Prepare the server-only Minecraft runtime once:

```powershell
.\gradlew.bat :preview-engine:preparePreview --no-daemon
```

Then start `start-preview.ps1` in the preview repository and open the loopback URL printed in the console. After code changes, prepare the runtime again (or use the script's `-Prepare` option).
The process uses the same Java generation core, real Minecraft noise/climate sampling and `BiomeManager` boundary refinement, with no game window, server ticks or saved chunk world. Its default heap limit is 512 MiB.

The surface layer uses the base noise generator's `WORLD_SURFACE_WG` height, before structures, carvers and decoration. Rivers are real biomes; feature-generated lakes, trees and buildings are not simulated. Fixed-height slices are exact at their displayed Y, and can differ from the surface.
Tiles at overview zoom sample a grid; zoom in to one-block sampling for detailed boundaries. Relief shading reuses the base heights.
World snapshots can be opened directly in the preview to retain their generation version. Enter that world's numeric `/seed` value as well.

With the preview running, `python tools/benchmark_preview.py` measures a coarse tile, a cached tile, a dense tile and cooperative HTTP cancellation. It also rejects invalid algorithm versions. `tools/verification-results.json` records the development-machine measurements and world comparisons; timings depend on hardware and cache state. Add `?profile=1` to the map URL to report UI tasks over 50 ms in the browser console.
`python tools/benchmark_zoom.py` compares a batch of 16 surface tiles against fixed-height tiles using independent cold caches. It reports a hash of the surface heights and biome IDs, allowing performance changes to be checked without accepting different results. The engine processes up to four tiles concurrently within its existing 512 MiB heap limit.

The browser-only island-mask mode remains available by opening `index.html`, without Java. Its colors describe the mask, not final Minecraft terrain.

## License

CC BY-NC-SA 4.0; see [LICENSE.txt](LICENSE.txt).
