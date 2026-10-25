# Named presets and integrated-server startup

## File workflow

1. Give the map a **Map name** in the preview, or add a top-level `"name": "My Painted Coast"` to its JSON.
2. Put an exported preset or version 1 editor project (`.wge.json`) in `config/worldgen_editor/presets`.
3. Open Minecraft's **World** tab and cycle **World Type** to **WGE: My Painted Coast**. The name is independent of the filename. Files may contain spaces, uppercase letters or Unicode; path traversal and reserved filenames are rejected.
4. Enter the world's numeric seed separately. The project retains a seed, but WGE does not override Minecraft's seed input.

A background scan updates metadata about once per second; cycling the button obtains the latest list without restarting Minecraft. Invalid entries remain identifiable and cannot silently select a different map. The display uses the JSON name, with numbered suffixes for duplicates. Unnamed legacy files retain fallback labels until named.

`Custom Map (v3)` was a fixed registry world type pointing at `custom_map.json`. It remains available to decode old saves, but new world creation uses discovered files instead of four fixed WGE choices. **Empty Ocean** is simply the bundled blank version 3 template. Every painted file automatically selects version 3 when it does not explicitly declare a version.

## Reported freeze

The reported NeoForge 21.1.249 log showed an editor project installed as `small_island.json`. The old loader expected flat `entries` at the root, raised `root.entries is required`, and stopped the integrated server before its levels existed. Minecraft then hit a second shutdown exception and remained on the loading screen.

The loader now decodes the project envelope before reading its mask, and the creation list selects its generation version automatically. Before starting the integrated server, the client validates and pins the selected map on a worker thread. Errors close the prepared world resources and return to a usable world-selection screen with the filename and reason. They do not replace the map with defaults.

Existing worlds continue to use their saved snapshots, even if the original file disappears. A failed earlier creation may still contain an old version 1/2 generator in `level.dat` and no valid snapshot. It is not silently converted to generation 3; create a fresh world using the discovered painted entry. User saves are not rewritten by this update.

## Verification

- The missing-root-entries failure was reproduced as a failing regression, then fixed.
- Common checks read the actual reported project: 14 painted tiles, generation 3. Golden generation v1/v2 checks remain unchanged.
- A separate NeoForge 21.1.230 client displayed a JSON name from an unrelated filename containing spaces and multiple extensions.
- A file added while the creation screen was already open appeared in the World Type cycle without reopening the screen.
- A malformed preset showed a working error screen and returned to world creation.
- The reported painted mask created a real world, completed spawn preparation in 2329 ms, and allowed the player to join.
- The world saved and reopened after its source preset was disabled. Its pinned painted layer matches the original project exactly.

`tools/preset-update-verification.json` records these checks and the input hash. This is a finite client regression test, not a claim that every Minecraft startup issue has been solved. The test client and world are under `neoforge/build/preset-client-run`; user saves are not used.

## Local build deployment

Keep version **0.4.0** for subsequent local updates. Run `tools/build-test.ps1` with Java 21+ configured, or run the checked Gradle build followed by `tools/deploy-test-build.ps1`. The latter copies only the WGE jar into the configured Modrinth test profile, backs up the previous jar under `build/test-profile-backups`, and compares SHA-256. Other mods, presets and saves are not overwritten.
