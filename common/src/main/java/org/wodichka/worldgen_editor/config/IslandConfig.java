package org.wodichka.worldgen_editor.config;

import java.util.List;

public record IslandConfig(boolean enabled, String outerOcean, List<IslandEntry> entries, PaintedMap paintedMap, String name) {
    public IslandConfig(boolean enabled,String outerOcean,List<IslandEntry> entries,PaintedMap paintedMap) { this(enabled,outerOcean,entries,paintedMap,""); }
    public IslandConfig(boolean enabled,String outerOcean,List<IslandEntry> entries) { this(enabled,outerOcean,entries,null); }
    public IslandConfig withEnabled(boolean value) { return new IslandConfig(value,outerOcean,entries,paintedMap,name); }
    public static final String DEFAULT_OUTER_OCEAN = "minecraft:deep_ocean";
    public static final IslandConfig EMPTY = new IslandConfig(false, DEFAULT_OUTER_OCEAN, List.of());

    public IslandConfig {
        if (outerOcean == null || outerOcean.isBlank()) {
            outerOcean = DEFAULT_OUTER_OCEAN;
        }
        entries = List.copyOf(entries);
        name = name == null ? "" : name;
    }
}
