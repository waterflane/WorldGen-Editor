# Explicit ocean temperature

WGE 0.4.0, Minecraft 1.21.1 / NeoForge 21.1.230; checked locally on 2026-10-25.

## Rules

Previously a `standard` coastline inherited the underlying Minecraft biome's climate. The cold candidate family included Frozen Ocean even when the painted land had no explicit cold setting. A failing regression reproduced this before the fix.

Coastal selection now uses root `ocean_temperature`, then the selected procedural element's explicit `temperature`, then ordinary Ocean / Deep Ocean at the existing mask-depth threshold. The root accepts only `temperate`, `cold`, `warm`; omit it to use element settings or ordinary ocean. `standard` remains valid for elements, meaning no explicit climate. Root temperature affects oceans only, including painted coasts. It does not change land, rivers, caves, island masks or base heights. The separately configured `outer_ocean` remains authoritative outside island influence.

The correction deliberately applies to versions 1–3 and existing pinned worlds after restarting. Existing chunks keep their biomes. Future chunks can differ at their boundary. Editing a preset's root setting still requires importing it with `/worldgen_editor reload` and restarting an existing world; changing global files alone does not replace its pinned snapshot.

The editor's **Coastal ocean temperature** selector updates the worker-owned draft, autosave and both export formats. Exact preview updates only on **Apply to Exact Preview**. **Default** removes the root field. Java advertises `ocean_temperature_v1`, the browser checks it, and the resource/session identity includes the corrected ocean policy revision so obsolete tiles cannot represent a new configuration.

## Verification

- Common configuration/project parsing and serialization reject malformed values without replacing saved state. Snapshot loading retains the setting after restart.
- 1,920 checks cover root/element precedence and cold, hot, land, river and cave delegates across generation versions 1–3. Masks and land selection remain identical when changing the root ocean setting.
- The 576 original biome fixtures remain unchanged on disk. Land and explicitly configured ocean outputs still match them. Unconfigured ocean assertions explicitly encode the approved correction. The 576 multi-preset base-height columns and legacy mask fixtures pass.
- All 41 preview Node tests pass, including draft/export round trips, invalid imports, runtime capability checks, new-session cache invalidation, stale responses and cached pan/zoom behavior.
- Real NeoForge worlds use seed **2** and a read-only copy of the user's `test-map.json`. Four fresh isolated worlds cover Default, Temperate, Cold and Warm. Each compares **76 actual block biome IDs**, **19 base heights** and **19 fast surface samples** with Java preview. All have **zero mismatches**; the height arrays also match across all four worlds. Samples include surface, Y=300, Y=0, Y=-32, painted coasts and external ocean.
- At the screenshot coordinate **(-545, -419)**, Default/Temperate select `minecraft:ocean`, explicit Warm selects `minecraft:warm_ocean`, and explicit Cold selects `minecraft:frozen_ocean` in all four sampled slices.
- Each run reads actual saved `level.dat` and the pinned snapshot and reconstructs matching heights and block-level biome boundaries. A second server process reopens the Default test world after its isolated source preset is replaced with invalid JSON; all comparisons still pass from the pinned snapshot.
- A browser check on a separate localhost port confirms the selector, unapplied/unsaved state and Apply output. The user's port-8765 recovery draft is untouched.
- The full release build passes. Test-only GameTest classes, seed mixin/config and resources are absent from the release jar. Machine-readable details and hashes are in `tools/ocean-temperature-verification.json`.

These are finite Minecraft + WGE samples, not an exhaustive modpack/seed proof. Third-party compatibility results remain separately dated in `compatibility.md`.

## Reproduce

Start the updated Java preview on port 8765. In the mod repository, with Java 21+ configured:

```powershell
.\gradlew.bat :common:check :preview-engine:check --offline --no-daemon
.\gradlew.bat :neoforge:runVerification -PoceanVerification=default -PoceanRun=coast-default-1 --offline --no-daemon
```

The default fixture is `common/src/test/resources/painted-islands.json`. To check another map, add a quoted `'-PoceanPreset=C:\path\to\map.json'` argument; the file is read, never edited. Repeat with `-PoceanVerification=temperate`, `cold`, `warm` and a distinct unused `-PoceanRun` name for each case. Each run stores only test data under `neoforge/build/ocean-verification/<name>`. Choose new names to avoid comparing old cached chunks to different settings.

For a restart check, rerun the same climate/name with `-PoceanReopen`; it requires an existing pinned test-world snapshot and skips rewriting the isolated source preset. Results are written to `ocean-result.json` in that run directory. No user saves are used. The test-only seed mixin activates exclusively in these runs and is excluded from production.

From the companion preview repository:

```powershell
node --test tests/*.test.cjs
```
