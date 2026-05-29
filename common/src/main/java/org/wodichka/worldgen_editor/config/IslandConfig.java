package org.wodichka.worldgen_editor.config;

import java.util.List;

public record IslandConfig(boolean enabled, String outerOcean, List<IslandEntry> entries) {
    public static final String DEFAULT_OUTER_OCEAN = "minecraft:deep_ocean";
    public static final IslandConfig EMPTY = new IslandConfig(false, DEFAULT_OUTER_OCEAN, List.of());

    public IslandConfig {
        if (outerOcean == null || outerOcean.isBlank()) {
            outerOcean = DEFAULT_OUTER_OCEAN;
        }
        entries = List.copyOf(entries);
    }
}
