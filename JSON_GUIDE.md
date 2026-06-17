# WorldGen Editor JSON Guide

The mod now works through a datapack-style world preset. For a new world, choose this world type:

```text
WGE: Islands
```

Inside this preset, Minecraft still uses the normal `minecraft:noise` generator, but with two modded worldgen resources:

- `worldgen_editor:island_biome_source` selects ocean and land biomes before chunk generation.
- `worldgen_editor:island_final_density` softly reshapes vanilla density so oceans form around configured islands.

This is much more stable than replacing generator internals after the vanilla generator has already been created.

## Config Location

Main config file:

```text
config/worldgen_editor/worldgen_editor.json
```

It selects a preset:

```json
{
  "enabled": true,
  "active_preset": "default"
}
```

Preset files live here:

```text
config/worldgen_editor/presets/default.json
config/worldgen_editor/presets/archipelago.json
config/worldgen_editor/presets/small_island.json
```

Bundled presets and matching world types:

- `WGE: Islands` / `default`: the current standard island set.
- `WGE: Archipelago` / `archipelago`: several islands and an archipelago cluster.
- `WGE: Small Island` / `small_island`: one small spawn island.

If the files do not exist, the mod creates defaults. New installs use `worldgen_editor.json` and `presets/*.json`; `continents.json` is only a legacy fallback for old configs where the new main config is not present.

Preset priority is:

```text
world type preset > config active_preset > legacy continents.json
```

For example, a world created with `WGE: Archipelago` uses `config/worldgen_editor/presets/archipelago.json` even if `worldgen_editor.json` says `"active_preset": "default"`. This keeps the Minecraft world type and the generated world stable. The config-selected preset is used when no WGE world type preset is stored.

The selected preset is read:

- once when the mod initializes, so a default file can be created early;
- again when a world/server starts, before the island mask is rebuilt for that world's seed;
- again when `/worldgen_editor reload` is used.

This means you can edit `worldgen_editor.json` or a preset JSON from the main menu and then create a new world without restarting the game.

After editing the config while a world is already running, use:

```text
/worldgen_editor reload
```

`reload` only affects newly generated chunks. Minecraft does not rebuild already generated chunks, biomes, structures, or blocks.

## Enabling

The main config has a top-level flag:

```json
{
  "enabled": true,
  "active_preset": "default"
}
```

- `enabled: false` means the island mask is not applied, even when the island world preset is selected.
- `enabled: true` allows island generation in worlds where the per-world flag is also enabled.
- `active_preset` chooses a fallback file from `config/worldgen_editor/presets/` when the world itself does not already store a WGE preset.

Each preset file has the usual island fields:

```json
{
  "outer_ocean": "minecraft:deep_ocean",
  "entries": []
}
```

- `outer_ocean` is optional. It controls the biome used outside every island/archipelago mask. If omitted, the mod uses `minecraft:deep_ocean`. If the configured biome is missing or not ocean-like, the mod logs a warning and falls back to `minecraft:deep_ocean`.

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
generation enabled = config/worldgen_editor/worldgen_editor.json enabled && <world>/worldgen_editor/worldgen_editor.json enabled
```

Commands:

```text
/worldgen_editor enable
/worldgen_editor disable
/worldgen_editor status
/worldgen_editor preset <name>
/worldgen_editor reload
```

You can choose a preset in the world type screen when creating a world, or change the config fallback with:

```text
/worldgen_editor preset small_island
```

## Smallest Valid Island

At minimum, an island needs a center and a radius:

```json
{
  "enabled": true,
  "outer_ocean": "minecraft:deep_ocean",
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
      "type": "island",
      "name": "Spawn Island",
      "x": 0,
      "z": 0,
      "radius": 950,
      "shape_power": 2.2,
      "roughness": 0.16,
      "shore_width": 0.18,
      "temperature": "temperate",
      "biome_patch_size": 768,
      "noise": {
        "seed": "spawn"
      }
    },
    {
      "type": "ocean",
      "name": "Central Strait",
      "x": 360,
      "z": 80,
      "radius_x": 230,
      "radius_z": 980,
      "rotation": 24,
      "shape_power": 1.35,
      "roughness": 0.18,
      "shore_width": 0.22,
      "temperature": "standard",
      "biome_patch_size": 1024,
      "noise": {
        "seed": "central_strait"
      }
    },
    {
      "type": "archipelago",
      "name": "Western Archipelago",
      "x": -1700,
      "z": 720,
      "radius": 1100,
      "count": 16,
      "min_radius": 90,
      "max_radius": 250,
      "spread": 0.88,
      "spacing": 1.15,
      "min_stretch": 0.7,
      "max_stretch": 1.65,
      "min_shape_power": 1.2,
      "max_shape_power": 3.7,
      "roughness": 0.22,
      "shore_width": 0.17,
      "temperature": "warm",
      "biome_patch_size": 1024,
      "noise": {
        "seed": "western_archipelago"
      }
    },
    {
      "type": "island",
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

- `type` is optional. Supported values are `island`, `ocean`, and `archipelago`. If omitted, the entry is a normal `island`.
- `name` is an optional display name.
- `x` is the island center on the X axis.
- `z` is the island center on the Z axis.
- `radius` is the base island size.
- `radius_x` and `radius_z` define separate radii for more precise stretched shapes.
- `stretch_x` and `stretch_z` multiply the base radius.
- `rotation` rotates the island in degrees.
- `shape_power` changes the base shape before coastline noise is applied. `2.0` is the old circle/ellipse behavior, lower values are sharper and more diamond-like, higher values are broader and more rounded-square.
- `roughness` controls coastline noise strength from `0.0` to `1.0`.
- `shore_width` controls the soft transition width between land and ocean.
- `overlap` controls whether this island can add its mask on top of other islands.
- `temperature` controls climate-biome selection for this entry. Supported values are `standard`, `cold`, `temperate`, and `warm`. The aliases `auto`, `vanilla`, `standart`, and the old field name `climate` also work.
- `biome_patch_size` controls how large fallback-biome patches are, in blocks. Default is `512`. Smaller values create visible biome mosaic patches; larger values make islands and archipelagos more consistent.
- `exclude_biomes` prevents specific vanilla/modded biomes from being selected for this entry. Use biome ids like `minecraft:desert` or tags like `#minecraft:is_badlands`.

Most of the time, you only need `type`, `x`, `z`, `radius`, `stretch_x`, `stretch_z`, `rotation`, `shape_power`, and `roughness`.

## Entry Types

### `type: "island"`

This is the normal land-producing entry. Old entries without `type` still use this behavior.

```json
{
  "type": "island",
  "name": "Shaped Island",
  "x": 0,
  "z": 0,
  "radius": 850,
  "stretch_x": 1.4,
  "stretch_z": 0.8,
  "rotation": 18,
  "shape_power": 2.6,
  "roughness": 0.18
}
```

### `type: "ocean"`

Ocean entries carve water out of land entries. They are useful for straits, bays, inner seas, and unusual coast cuts. Order does not matter: ocean masks are applied after all land masks.

```json
{
  "type": "ocean",
  "name": "Cut Strait",
  "x": 240,
  "z": 0,
  "radius_x": 180,
  "radius_z": 900,
  "rotation": 30,
  "shape_power": 1.3,
  "roughness": 0.2,
  "shore_width": 0.22
}
```

### `type: "archipelago"`

Archipelago entries do not create one giant island. Instead, they deterministically place many smaller child islands inside the configured cluster radius.

```json
{
  "type": "archipelago",
  "name": "Outer Islands",
  "x": -1800,
  "z": 700,
  "radius": 1100,
  "count": 18,
  "min_radius": 90,
  "max_radius": 260,
  "spread": 0.9,
  "spacing": 1.2,
  "min_stretch": 0.65,
  "max_stretch": 1.6,
  "min_shape_power": 1.2,
  "max_shape_power": 3.8,
  "roughness": 0.22,
  "shore_width": 0.17,
  "noise": {
    "seed": "outer_islands"
  }
}
```

- `count` is the target number of child islands.
- `min_radius` and `max_radius` control child island sizes.
- `spread` controls how much of the cluster radius can be used.
- `spacing` controls how strongly child islands try to avoid each other.
- `min_stretch` and `max_stretch` randomize child island elongation.
- `min_shape_power` and `max_shape_power` randomize child island base shapes.
- If the requested count cannot fit with the chosen spacing, the mod logs a warning and uses the islands it could place.
- Child island placement, size, stretch, rotation, shape, and coastline noise depend only on this entry's `noise.seed`. Changing the Minecraft world seed no longer moves the individual archipelago islands.

## Biome Exclusions

Biome selection is automatic. The mod keeps vanilla land biomes when they are allowed, picks climate-aware ocean variants for ocean zones, and uses safe fallback land biomes when a chosen biome is excluded.

You can override the automatic climate per entry with `temperature`:

```json
{
  "type": "archipelago",
  "name": "Warm Islands",
  "x": -1200,
  "z": 400,
  "radius": 900,
  "temperature": "warm"
}
```

Supported values:

- `standard` follows Minecraft's own delegate biome choice as much as possible. This is the default when `temperature` is omitted and it does not filter climates.
- `cold` allows only cold land/ocean fallbacks, such as snowy plains, taiga, frozen ocean, and cold ocean variants.
- `temperate` allows moderate land/ocean fallbacks, such as plains, forests, meadow, normal ocean, and deep ocean. It avoids desert, savanna, jungle, snowy, and frozen fallbacks.
- `warm` allows warm land fallbacks, such as savanna, jungle, and desert. For oceans it allows warm/lukewarm/normal non-cold ocean variants and blocks cold/frozen/deep-cold oceans.

`temperature` affects only biome selection performed by WorldGen Editor. With `standard`, vanilla/modded delegate biomes are preserved unless excluded. With `warm`, `cold`, or `temperate`, an incompatible delegate biome is replaced with a matching fallback. Unknown modded biomes are kept under `standard`; under manual temperatures they are replaced unless their tags clearly match the requested climate.

Compatibility aliases:

- `auto` = `standard`
- `vanilla` = `standard`
- `standart` = `standard`
- `climate` can be used as an old field name instead of `temperature`

Use `biome_patch_size` to control how patchy replacement biomes are:

```json
{
  "type": "archipelago",
  "name": "Large Biome Zones",
  "x": -1800,
  "z": 700,
  "radius": 1100,
  "biome_patch_size": 1024
}
```

Tuning:

- `32` to `128` creates small, visible biome patches.
- `256` to `512` is a balanced range.
- `1024` to `2048` makes archipelagos feel more unified.
- Valid range is `4` to `4096`.

This is useful for archipelagos: small patches can look varied, but large patches are usually better when you want one island chain to feel like a single climate region.

Use `exclude_biomes` on any entry:

```json
{
  "type": "island",
  "name": "No Deserts",
  "x": 0,
  "z": 0,
  "radius": 850,
  "exclude_biomes": [
    "minecraft:desert",
    "minecraft:savanna",
    "#minecraft:is_badlands"
  ]
}
```

Notes:

- Exact ids exclude one biome.
- Tags exclude every biome in that tag.
- Archipelago children inherit exclusions from their parent archipelago entry.
- If every preferred fallback is excluded, the mod logs a warning and uses a safe vanilla fallback.

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
- `noise.first_octave` controls the starting octave, which mostly means the size of the largest coastline waves.
- `noise.amplitudes` controls how much each octave contributes to the final coastline shape.

### `noise.first_octave`

You can usually leave this at `-1`.

The mod builds coastline noise from several layers, also called octaves. Each next octave is twice as detailed as the previous one. `first_octave` decides where that stack starts:

- `-2` starts with larger, slower coastline bends.
- `-1` is the default and works well for most islands.
- `0` starts with smaller, faster coastline variation.
- `1` and higher can make the edge busy and noisy, especially on small islands.

Practical tuning:

```json
"first_octave": -2
```

Use this when a large island should have broad bays and capes instead of small jagged edges.

```json
"first_octave": -1
```

Use this as the normal default.

```json
"first_octave": 0
```

Use this only when you want more small-scale coastline wobble.

### `noise.amplitudes`

`amplitudes` is a list of octave strengths. The first number affects the first octave, the second number affects the next octave, and so on.

Default:

```json
"amplitudes": [1.0, 0.78, 0.55, 0.34]
```

This means:

- `1.0`: strongest large coastline shape.
- `0.78`: medium details still matter.
- `0.55`: smaller details are visible.
- `0.34`: fine details are present, but weaker.

Smoother, simpler coast:

```json
"amplitudes": [1.0, 0.45, 0.18]
```

More detailed coast:

```json
"amplitudes": [1.0, 0.85, 0.65, 0.45, 0.25]
```

Very smooth, almost ellipse-like coast:

```json
"amplitudes": [1.0]
```

Rules of thumb:

- Keep the first value near `1.0`.
- Let later values get smaller.
- Avoid many strong values like `[1.0, 1.0, 1.0, 1.0]` unless you intentionally want a chaotic coast.
- If coastlines look noisy, reduce later amplitudes first.
- If coastlines look too round, increase `roughness` before making amplitudes extreme.

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

- Select the `WGE: Islands`, `WGE: Archipelago`, or `WGE: Small Island` world preset when creating an island world.
- Changing the config after world creation does not rebuild old chunks.
- For a completely clean result, create a new world or deliberately delete old region files.
- Islands use vanilla land biomes inside the island mask. The mod does not manually draw rivers or swamps.
- Ocean around islands is produced through noise settings and biome source, not by late block replacement.

## Common Issues

- The island did not appear: check that the world preset is one of the `WGE: ...` presets and that `/worldgen_editor status` reports the expected `world_preset`.
- The world looks vanilla: check `enabled` in `config/worldgen_editor/worldgen_editor.json` and `/worldgen_editor status`.
- JSON changed but the nearby terrain did not: you are looking at already generated chunks.
- Reload failed: check commas, quotes, and required fields `x`, `z`, and `radius`.
- Shores are too sharp: lower `roughness` or increase `shore_width`.
- The island is too low or too small: increase `radius`; very small islands are more affected by vanilla lowland or river terrain.
