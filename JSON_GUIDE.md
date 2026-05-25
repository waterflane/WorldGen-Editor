# WorldGen Editor: `continents.json` Guide

The mod now works through a datapack-style world preset. For a new world, choose this world type:

```text
WorldGen Editor: Islands
```

Inside this preset, Minecraft still uses the normal `minecraft:noise` generator, but with two modded worldgen resources:

- `worldgen_editor:island_biome_source` selects ocean and land biomes before chunk generation.
- `worldgen_editor:island_final_density` softly reshapes vanilla density so oceans form around configured islands.

This is much more stable than replacing generator internals after the vanilla generator has already been created.

## Config Location

Main config file:

```text
config/worldgen_editor/continents.json
```

If the file does not exist, the mod creates a default archipelago example.

After editing the config while a world is already running, use:

```text
/worldgen_editor reload
```

`reload` only affects newly generated chunks. Minecraft does not rebuild already generated chunks, biomes, structures, or blocks.

## Enabling

The normal config has a top-level flag:

```json
{
  "enabled": true,
  "entries": []
}
```

- `enabled: false` means the island mask is not applied, even when the island world preset is selected.
- `enabled: true` allows island generation in worlds where the per-world flag is also enabled.

For each world, the mod stores an extra file:

```text
<world_folder>/worldgen_editor/worldgen_editor.json
```

New world-state files are created as:

```json
{
  "enabled": true
}
```

Final logic:

```text
generation enabled = continents.json enabled && worldgen_editor.json enabled
```

Commands:

```text
/worldgen_editor enable
/worldgen_editor disable
/worldgen_editor status
/worldgen_editor reload
```

## Smallest Valid Island

At minimum, an island needs a center and a radius:

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

This creates one roughly round island around coordinates `0, 0`.

## Recommended Example

```json
{
  "enabled": true,
  "entries": [
    {
      "name": "Spawn Island",
      "x": 0,
      "z": 0,
      "radius": 950,
      "roughness": 0.16,
      "shore_width": 0.18,
      "noise": {
        "seed": "spawn"
      }
    },
    {
      "name": "Long Island",
      "x": 1350,
      "z": -650,
      "radius": 520,
      "stretch_x": 1.7,
      "stretch_z": 0.75,
      "rotation": 25,
      "roughness": 0.14,
      "noise": {
        "seed": "long_island"
      }
    }
  ]
}
```

## Island Fields

- `name` is an optional display name.
- `x` is the island center on the X axis.
- `z` is the island center on the Z axis.
- `radius` is the base island size.
- `radius_x` and `radius_z` define separate radii for more precise stretched shapes.
- `stretch_x` and `stretch_z` multiply the base radius.
- `rotation` rotates the island in degrees.
- `roughness` controls coastline noise strength from `0.0` to `1.0`.
- `shore_width` controls the soft transition width between land and ocean.
- `overlap` controls whether this island can add its mask on top of other islands.

Most of the time, you only need `x`, `z`, `radius`, `stretch_x`, `stretch_z`, `rotation`, and `roughness`.

## Coast Noise

```json
{
  "x": 0,
  "z": 0,
  "radius": 700,
  "roughness": 0.22,
  "noise": {
    "seed": "coast_1",
    "scale": 3.5,
    "first_octave": -1,
    "amplitudes": [1.0, 0.78, 0.55, 0.34]
  }
}
```

- `noise.seed` changes the coastline shape without changing island center or size.
- `noise.scale` increases noise frequency.
- `noise.first_octave` can usually stay at `-1`.
- `noise.amplitudes` controls octave weights.

Smoother coastline:

```json
"roughness": 0.08
```

Rougher coastline:

```json
"roughness": 0.25
```

## Old Format Compatibility

The mod still understands older field names:

- `center_x` = `x`
- `center_z` = `z`
- `x_divisor` = `radius_x`
- `z_divisor` = `radius_z`
- `rotation_degrees` = `rotation`
- `multiplier` = `size_multiplier`

The new format is shorter, but older datapack-style files should continue to work.

## Important Limits

- Select the `WorldGen Editor: Islands` world preset when creating an island world.
- Changing the config after world creation does not rebuild old chunks.
- For a completely clean result, create a new world or deliberately delete old region files.
- Islands use vanilla land biomes inside the island mask. The mod does not manually draw rivers or swamps.
- Ocean around islands is produced through noise settings and biome source, not by late block replacement.

## Common Issues

- The island did not appear: check that the world preset is `WorldGen Editor: Islands`.
- The world looks vanilla: check `enabled` in `continents.json` and `/worldgen_editor status`.
- JSON changed but the nearby terrain did not: you are looking at already generated chunks.
- Reload failed: check commas, quotes, and required fields `x`, `z`, and `radius`.
- Shores are too sharp: lower `roughness` or increase `shore_width`.
- The island is too low or too small: increase `radius`; very small islands are more affected by vanilla lowland or river terrain.
