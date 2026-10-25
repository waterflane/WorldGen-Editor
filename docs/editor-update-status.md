# Island editor update

Target: Minecraft 1.21.1 / NeoForge, generation versions 1 and 2 preserved.
Work is local on DEV; no publishing or pushes.

## Ocean temperature correction (2026-10-25)

The earlier preservation statements below have one approved exception: unconfigured oceans now select Ocean / Deep Ocean instead of inheriting a cold/hot delegate, in generation versions 1–3. Explicit root `ocean_temperature` overrides element ocean temperatures; land, rivers, caves, masks and base heights retain their previous algorithms. See [ocean-temperature.md](ocean-temperature.md) for the seed-2 painted-world, restart and preview checks. The preview suite has 41 passing tests; the release remains 0.4.0.

## Named preset follow-up (2026-10-25)

Named JSON presets and editor projects are discovered directly in Minecraft's World Type cycle. The preview exports a root `name` and named files. A pre-server client check prevents malformed project/preset files from leaving the integrated server half-started. The actual reported 14-tile project was imported, created, entered, saved and reopened in an isolated client, including reopening without its source file. See [preset-loading.md](preset-loading.md) for reproduction, validation and deployment details. The version remains 0.4.0; the preview suite now has 36 tests.

## Baseline

- 2026-10-23: 21 preview Node tests passed.
- `:common:check :preview-engine:check` passed, including snapshot persistence,
  576 legacy biome cases and 576 base-height columns.
- Prior browser/Java performance baseline: `tools/column-performance.json`.
- Invalid non-ocean `outer_ocean` IDs already fall back to deep ocean; do not
  broaden biome selection or change old worlds on the basis of this hypothesis.

## Implemented and checked

- Version 3 composition retains the effective generator graph and surface rules; separate codecs save original registry settings instead of non-serializable runtime markers.
- Immutable running generation contexts; reload/preset/enable/migrate validate and save the next snapshot, requiring a world restart. Failed changes preserve the old snapshot.
- Explicit version 3 preset disable now survives global defaults. Version 1/2 precedence and golden generation results stay unchanged.
- Painted snapshots are validated before saving and compare contents deeply. Shared Java/JS masks cover interpolation, negative coordinates, tile boundaries and malformed RLE.
- The editor has independent draft/applied revisions, worker brushes, continuous strokes, both replacement directions, bounded undo/redo, file project/preset export and IndexedDB recovery. Large paint payloads are parsed outside the main thread.
- 33 Node tests; common and preview Gradle checks, including 576 legacy biome cases and 576 multi-preset base-height columns; full release build passed. The final version 2 NeoForge test compared 113 biomes and 24 heights with zero mismatches, including river, cave and shore controls.
- Eleven isolated real-world profiles, including all requested combinations: see [compatibility.md](compatibility.md) and the machine-readable results. Minecraft-only / painted / disabled profiles have zero biome and base-height mismatches in tested points.
- Browser checks covered painting, ocean cutouts, undo/redo, apply, project save and draft recovery. No console warnings/errors occurred in the final interaction sample.

## Confirmed audit fixes

1. Live context replacement could mix old NoiseChunk caches with new rules. Changes now take effect after restarting the world.
2. Expanded version 3 noise settings contained HolderHolder markers without codecs; saving a composed Tectonic world failed. The generator saves original registry settings and recomposes on load.
3. Global enabled=true overrode explicit preset enabled=false. Version 3 now respects both flags; a real disabled-world regression test compares with preview.
4. Reference-based painted-tile equality and missing snapshot-version validation could misclassify a snapshot or save incompatible masks. Validation and content equality are now explicit.
5. Large painted JSON could block the browser during apply. Validation/decoding/serialization are worker-owned; the main JSON editor shows small metadata while downloads retain the full applied layer.
6. Brush settings changed without a stroke were not included in the next project/draft. They now persist independently; browser draft recovery retained both the contour and the changed radius.
7. An unknown project format could be interpreted as a world snapshot. The failing regression now rejects unsupported formats before replacing a document.

The audit did not confirm a missing outer-ocean fallback: invalid non-ocean IDs already correctly fall back to deep ocean. A regression test records that behavior.

## Performance and limits

`tools/editor-preview-performance.json` separates startup, first frame, complete worker/API view, cached view and checksums. The same painted view took 6179 ms before column-mask memoization, 1484 ms in the updated fresh JVM and 1186 ms in a subsequent new session. The before JVM was already warm; this is not a controlled cold-to-cold experiment. Biome/height checksums match. Cached redraws made zero Java requests.

The browser's 512-block-radius brush produced its first actual canvas draw in 27 ms. `tools/editor-model-performance.json` separately measures a large painted area and 100 far-apart islands; it excludes worker transport/browser presentation. These development-machine samples are not worst-case latency guarantees. The initial heap remains 512 MiB; runtime/classpath size is measured separately without bundling a JRE. A full exact uncached map still takes longer than its first tile.

Limits: 4096 sparse tiles, 32 MiB file import/export, 100 undo actions / 32 MiB history. Finite compatibility sampling and the saved-data reconstruction test do not replace exhaustive feature/seed testing or a full interactive client lifecycle. The release does not claim exact previews for third-party generators or optimizer compatibility.
