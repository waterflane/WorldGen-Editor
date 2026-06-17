# WorldGen Editor

WorldGen Editor is a JSON-driven Minecraft world generation mod for Minecraft `1.21.1`.

It adds datapack-style world presets named `WGE: Islands`, `WGE: Archipelago`, and `WGE: Small Island`. The presets keep vanilla noise generation as the base, then use a custom island biome source and density function to turn the Overworld into configurable islands.

The project is built with Architectury Loom and targets:

- Fabric
- Forge
- NeoForge

## Features

- Configurable island presets from `config/worldgen_editor/worldgen_editor.json`.
- Three bundled presets: `default`, `archipelago`, and `small_island`.
- Simple island fields such as `x`, `z`, `radius`, `rotation`, `shape_power`, `roughness`, and `shore_width`.
- JSON entry types for normal islands, ocean-carved cuts, and generated archipelagos.
- Per-entry biome exclusions with `exclude_biomes`, including biome ids and tags.
- Climate-aware ocean and land fallback biome selection.
- Backward-compatible support for older field names such as `center_x`, `center_z`, `x_divisor`, and `z_divisor`.
- Built-in world presets: `WGE: Islands`, `WGE: Archipelago`, and `WGE: Small Island`.
- Runtime commands for reload and per-world enable state.
- Separate preview tool for developers: [WorldGen Editor Preview](https://github.com/waterflane/WorldGen-Editor-Preview).

## Requirements

- Java 21
- Minecraft `1.21.1`
- Gradle wrapper included in this repository

Loader dependency notes:

- Fabric requires Fabric Loader, Fabric API, and Architectury API.
- NeoForge requires NeoForge and Architectury API.
- Forge uses the native Forge API path for `1.21.1`.

## Building

From the project root:

```powershell
.\gradlew.bat build --no-daemon
```

On Linux or macOS:

```bash
./gradlew build --no-daemon
```

The production jars are created here:

```text
fabric/build/libs/worldgen-editor-fabric-0.3.0.jar
forge/build/libs/worldgen-editor-forge-0.3.0.jar
neoforge/build/libs/worldgen-editor-neoforge-0.3.0.jar
```

For convenience, the root `build` task also copies the three loader-ready jars into:

```text
release/
```

You can run the copy task directly:

```powershell
.\gradlew.bat releaseJars --no-daemon
```

The version is controlled by `mod_version` in `gradle.properties`.

## Project Layout

```text
common/    Shared config, island mask, commands, codecs, and datapack-style worldgen resources
fabric/    Fabric entrypoint and metadata
forge/     Forge entrypoint and metadata
neoforge/  NeoForge entrypoint and metadata
```

Important shared resources:

```text
common/src/main/resources/data/worldgen_editor/worldgen/world_preset/islands.json
common/src/main/resources/data/worldgen_editor/worldgen/noise_settings/island_overworld.json
common/src/main/resources/assets/worldgen_editor/default_worldgen_editor.json
common/src/main/resources/assets/worldgen_editor/presets/
```

## Using The Mod

Install the jar for your loader, then create a new world and select:

```text
WGE: Islands
```

The mod reads:

```text
config/worldgen_editor/worldgen_editor.json
```

That file selects one preset from:

```text
config/worldgen_editor/presets/
```

Bundled presets and world types:

- `WGE: Islands` / `default`: the current standard island set.
- `WGE: Archipelago` / `archipelago`: several islands and an archipelago cluster.
- `WGE: Small Island` / `small_island`: one small spawn island.

If the files do not exist, the mod creates defaults. New installs use `worldgen_editor.json` and `presets/*.json`; `continents.json` is only a legacy fallback for old configs where the new main config is not present.

Changing the config does not rebuild old chunks. Use this command after editing the file:

```text
/worldgen_editor reload
```

Reload affects newly generated chunks only.

## Commands

```text
/worldgen_editor enable
/worldgen_editor disable
/worldgen_editor status
/worldgen_editor preset <name>
/worldgen_editor reload
```

Generation is enabled only when both the global config and the per-world state are enabled:

```text
worldgen_editor.json enabled && <world>/worldgen_editor/worldgen_editor.json enabled
```

Preset priority is:

```text
world type preset > config active_preset > legacy continents.json
```

You can change the config-selected fallback preset with:

```text
/worldgen_editor preset archipelago
```

The command affects newly generated chunks only when the current world does not already store a WGE world type preset.

## Config Guide

See [JSON_GUIDE.md](JSON_GUIDE.md) for the full preset and island JSON format, examples, compatibility fields, and troubleshooting notes.

Minimal example:

```json
{
  "enabled": true,
  "active_preset": "default"
}
```

## Preview Tool

The companion preview tool is located outside this repository folder: 
[WorldGen Editor Preview](https://github.com/waterflane/WorldGen-Editor-Preview)

Open `index.html` in a browser to preview island masks without generating Minecraft worlds.

## License

WorldGen Editor is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.

See [LICENSE.txt](LICENSE.txt).
