# WorldGen Editor

WorldGen Editor is a JSON-driven Minecraft world generation mod for Minecraft `1.21.1`.

It adds a datapack-style world preset named `WorldGen Editor: Islands`. The preset keeps vanilla noise generation as the base, then uses a custom island biome source and density function to turn the Overworld into a configurable archipelago.

The project is built with Architectury Loom and targets:

- Fabric
- Forge
- NeoForge

## Features

- Configurable islands from `config/worldgen_editor/continents.json`.
- Simple island fields such as `x`, `z`, `radius`, `rotation`, `shape_power`, `roughness`, and `shore_width`.
- JSON entry types for normal islands, ocean-carved cuts, and generated archipelagos.
- Per-entry biome exclusions with `exclude_biomes`, including biome ids and tags.
- Climate-aware ocean and land fallback biome selection.
- Backward-compatible support for older field names such as `center_x`, `center_z`, `x_divisor`, and `z_divisor`.
- Built-in world preset: `WorldGen Editor: Islands`.
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
fabric/build/libs/worldgen-editor-fabric-0.2 beta.jar
forge/build/libs/worldgen-editor-forge-0.2 beta.jar
neoforge/build/libs/worldgen-editor-neoforge-0.2 beta.jar
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
common/src/main/resources/assets/worldgen_editor/default_continents.json
```

## Using The Mod

Install the jar for your loader, then create a new world and select:

```text
WorldGen Editor: Islands
```

The mod reads:

```text
config/worldgen_editor/continents.json
```

If the file does not exist, the mod creates a default config.

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
/worldgen_editor reload
```

Generation is enabled only when both the global config and the per-world state are enabled:

```text
continents.json enabled && <world>/worldgen_editor/worldgen_editor.json enabled
```

## Config Guide

See [JSON_GUIDE.md](JSON_GUIDE.md) for the full `continents.json` format, examples, compatibility fields, and troubleshooting notes.

Minimal example:

```json
{
  "enabled": true,
  "entries": [
    {
      "x": 0,
      "z": 0,
      "radius": 850
    }
  ]
}
```

## Preview Tool

The companion preview tool is located outside this repository folder: 
[WorldGen Editor Preview](https://github.com/waterflane/WorldGen-Editor-Preview)

Open `index.html` in a browser to preview island masks without generating Minecraft worlds.

## License

WorldGen Editor is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.

See [LICENSE.txt](LICENSE.txt).
