# Island editor update

Target: Minecraft 1.21.1 / NeoForge, generation versions 1 and 2 preserved.
Work is local on DEV; no publishing or pushes.

## Baseline

- 2026-10-23: 21 preview Node tests passed.
- `:common:check :preview-engine:check` passed, including snapshot persistence,
  576 legacy biome cases and 576 base-height columns.
- Prior browser/Java performance baseline: `tools/column-performance.json`.
- Invalid non-ocean `outer_ocean` IDs already fall back to deep ocean; do not
  broaden biome selection or change old worlds on the basis of this hypothesis.

## Remaining acceptance work

- Restart-safe generation changes and configuration audit.
- Version 3 composition with effective modded generation settings.
- Pinned real-world compatibility matrix (not just loader startup).
- Sparse painted masks shared by Java and JavaScript.
- Editor, project persistence, portable export, bounded undo and caches.
- Actual NeoForge/preview parity, browser interaction and performance checks.

The compatibility matrix is unverified until real-world results are recorded.
