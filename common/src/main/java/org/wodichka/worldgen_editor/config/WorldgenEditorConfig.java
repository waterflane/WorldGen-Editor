package org.wodichka.worldgen_editor.config;

public record WorldgenEditorConfig(boolean enabled, String activePreset) {
    public static final String DEFAULT_PRESET = "default";
    public static final WorldgenEditorConfig DEFAULT = new WorldgenEditorConfig(true, DEFAULT_PRESET);

    public WorldgenEditorConfig {
        if (activePreset == null || activePreset.isBlank()) {
            activePreset = DEFAULT_PRESET;
        }
    }
}
