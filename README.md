# WorldGen Editor

A JSON-driven island world generator for **Minecraft 1.21.1 / NeoForge 21.1.230–21.1.x**, requiring Java 21 or later.
Fabric, Forge and Architectury API are not runtime dependencies or supported targets.

Create a world, open the **World** tab and cycle **World Type** to **WGE: <map name>**. WGE discovers JSON files in `config/worldgen_editor/presets` while the screen is open. Names come from the JSON's top-level `name` field, not a required filename. Ordinary world types keep their own generators.
Version 3 composes WGE with the effective loaded noise settings and surface rules. It controls continentalness and caps ocean/coast density while retaining the generator's other noises, caves, aquifers and biome delegate. The retained `island_final_density` codec is legacy compatibility code; current presets do not use it.

## Configuration and saved worlds

The initial selection comes from the WGE world type. Global defaults live in:

```text
config/worldgen_editor/worldgen_editor.json
config/worldgen_editor/presets/default.json
config/worldgen_editor/presets/archipelago.json
config/worldgen_editor/presets/small_island.json
config/worldgen_editor/presets/custom_map.json
```

Minimal main configuration:

```json
{ "enabled": true, "active_preset": "default" }
```

Minimal preset:

```json
{
  "name": "My Archipelago",
  "enabled": true,
  "generation_version": 3,
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
Existing saves without a version retain version 1. Bundled procedural files keep version 2 unless explicitly changed; named files select their declared `generation_version`. Painted presets and editor projects select version 3 automatically when omitted. Old worlds never upgrade to version 3 automatically.
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

`preset` selects and pins a file for the current WGE world. Its argument is the filename without the final `.json`; use quotes for spaces, for example `/worldgen_editor preset "My Map.wge"`. `reload` explicitly imports that preset again from global configuration. Failed changes leave the previous snapshot active.
Successful changes, including enable/disable and migration, are saved for the **next world restart**. The running generation context stays immutable so existing noise caches and background generation cannot mix configurations. Commands report this restart requirement.
`migrate` explicitly opts an old world into generation version 2; it does not opt into version 3. Changes affect future chunks after restarting and may create boundaries against existing chunks; existing chunks are never rewritten.
A disabled or legacy empty mask delegates to the underlying generator. A version 3 preset containing an empty `painted_map` means an all-ocean canvas. In version 3, explicit preset `enabled: false` is respected in addition to the global and per-world flags.

## Draw a new world

In the companion preview's **Map Editor** tab, start with **New Ocean** or import the preview preset. Set **Map name**, paint land/ocean, and export a preset or save an editor project. Add that JSON to `config/worldgen_editor/presets` with any safe filename. In Minecraft choose **WGE: <Map name>** and enter the numeric seed shown in the preview. A project/preset does not change Minecraft's seed automatically.

The directory is scanned on a background thread about once per second. Added, edited and removed files are reflected the next time the World Type list is cycled. Sorting uses map names; duplicate names get numbered suffixes. Legacy unnamed files use a fallback label until a `name` is supplied. New files do not replace bundled presets, and no filename renaming is needed. `name` is text of 1–120 characters; island entry names are separate.

Both exported presets and version 1 `.wge.json` editor projects are recognized. Project configuration is extracted before world generation; editor settings are ignored by Minecraft. Invalid/unsupported documents appear as invalid and are checked before the integrated server starts. The error screen includes the file and returns to world selection; it never silently generates another map. Once created, a world's pinned configuration remains independent of future directory changes or removal of the original preset.

**What happened to Custom Map?** It was the fixed version 3 world type tied to `custom_map.json`. Its registry entry remains readable for existing worlds, but the new creation UI replaces fixed WGE types with discovered map names. The bundled empty template appears as **WGE: Empty Ocean**. Every painted JSON is its own map; no separate Custom Map choice is required.

The self-contained preset combines procedural `entries` with optional `painted_map`: version 1, step 4 blocks, 64×64 samples per sparse tile. RLE triples are `[count, coverage, value, ...]`; coverage/value are unsigned 16-bit integers. Later strokes replace earlier strokes in either direction, and bilinear interpolation is shared by Java and JavaScript. Baked shore roughness does not move when changing the seed. Limits are 4096 tiles and the Minecraft coordinate bounds; there is no allocation for the whole world.

The `.wge.json` project additionally retains the seed, format version and editor/view settings. Minecraft can read its generation configuration directly; the smaller preset export contains only generation data. Undo history is not exported. This first editor is for new worlds; no biome, height, structure or river painting is included.

## Compatibility

Version 3 was tested with the locked NeoForge 1.21.1 versions below, individually and in every requested combination. See [the verification report](docs/compatibility.md) for dependencies, actual checks and their limits. Version 1/2 generation rules remain unchanged.

| Project | Tested version |
| --- | --- |
| TerraBlender | 4.1.0.8 |
| Biomes O' Plenty | 21.1.0.14 (GlitchCore 2.1.0.2 + TerraBlender) |
| Terralith | 2.6.2 |
| Tectonic | 3.0.28-neoforge-21.1 |
| Supporting libraries | Lithostitched 1.8.1, Apollib 1.2.0 |

The optional TerraBlender adapter initializes the actual delegate and surface settings. The standalone preview remains **Minecraft + WGE** and does not load these third-party generators. Worldgen optimizer mods and arbitrary modpacks are outside this verification.

## Build and test

```powershell
.\gradlew.bat build --no-daemon
```

The loader-ready artifact is `neoforge/build/libs/worldgen-editor-neoforge-0.4.0.jar`, also copied into `release/` after the checks pass.
Local updates deliberately retain version **0.4.0**. With Java 21+ configured, `tools/build-test.ps1` runs the checked build and deploys it to `C:\Users\water\AppData\Roaming\ModrinthApp\profiles\NeoForge 1.21.1\mods`. `tools/deploy-test-build.ps1` deploys an already checked release, backs up the previous WGE jar under `build/test-profile-backups`, and verifies SHA-256. Other mods and user saves are left alone. Local commit IDs and jar checksums distinguish updates sharing the same version.
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
`python tools/benchmark_zoom.py` compares a batch of 16 surface tiles against fixed-height tiles using independent cold caches. It reports a hash of the surface heights and biome IDs, allowing performance changes to be checked without accepting different results. The engine processes up to six tiles concurrently, reserving two logical processors where possible, within its existing 512 MiB heap limit.

`node tools/benchmark_viewport.cjs [config.json] [seed] [cache-nonce]` runs the complete browser worker/API pipeline for an 850x720 Balanced surface view. It includes session creation, both sampling passes, HTTP and composition; it reports first tile/frame, full-view time, cached redraws and an output checksum. An optional nonce forces a new world/session cache without changing generation. Use a fresh Java process to measure cold startup effects; the script begins after the service is ready. This CLI measurement does not include browser presentation; the browser HUD separately reports the full render request and first received frame. A quick individual tile is not a measurement of a complete map. See `tools/viewport-performance.json` for the preceding baseline and `tools/column-performance.json` for current measurements.

The preview now reuses per-worker compiled density graphs and samples interpolation corners lazily. Grid-aligned coordinates only require corners with nonzero interpolation weights; dense columns share the same cell. Minecraft aquifer logic and `WORLD_SURFACE_WG` results remain the reference. The mod's generation rules and saved world version are unchanged. `-Dwge.preview.referenceSampler=true` selects the earlier sampler for diagnosis; `/api/info` reports the selected engine. Performance comparisons against that path are recorded in `tools/column-performance.json`.

Surface tile requests may set `include_heights: false` (default: true for API compatibility). Only when all possible biome-boundary X/Z corners resolve to the same height-independent WGE ocean biome may its height be omitted, represented by `-32768` in the height array. Other columns are sampled normally. Relief requests all heights and uses a separate browser cache group. Fixed-Y slices always return their actual Y. Biome IDs are exact in both surface paths; an omitted height is never a predicted height.

Benchmark environment options: `WGE_BENCH_FORCE_HEIGHTS=1` requests all heights without changing colors; `WGE_BENCH_WIDTH`, `WGE_BENCH_HEIGHT`, `WGE_BENCH_QUALITY` and `WGE_BENCH_BPP` select the viewport, and `WGE_BENCH_URL` selects the loopback service. Compare biome checksums between optional/full heights, and the combined biome/height checksum when all heights are requested. Java process startup is separate from each viewport measurement.

The browser-only island-mask mode remains available by opening `index.html`, without Java. Its colors describe the mask, not final Minecraft terrain.

## License

CC BY-NC-SA 4.0; see [LICENSE.txt](LICENSE.txt).
