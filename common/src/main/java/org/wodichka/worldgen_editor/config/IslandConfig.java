package org.wodichka.worldgen_editor.config;

import java.util.List;

public record IslandConfig(boolean enabled, String outerOcean, List<IslandEntry> entries, PaintedMap paintedMap, String name,
                           IslandTemperature oceanTemperature) {
    public IslandConfig(boolean enabled,String outerOcean,List<IslandEntry> entries,PaintedMap paintedMap,String name) { this(enabled,outerOcean,entries,paintedMap,name,null); }
    public IslandConfig(boolean enabled,String outerOcean,List<IslandEntry> entries,PaintedMap paintedMap) { this(enabled,outerOcean,entries,paintedMap,""); }
    public IslandConfig(boolean enabled,String outerOcean,List<IslandEntry> entries) { this(enabled,outerOcean,entries,null); }
    public IslandConfig withEnabled(boolean value) { return new IslandConfig(value,outerOcean,entries,paintedMap,name,oceanTemperature); }
    public boolean hasOutlines() { return entries.stream().anyMatch(entry->entry.outline()!=null); }
    public static final String DEFAULT_OUTER_OCEAN = "minecraft:deep_ocean";
    public static final IslandConfig EMPTY = new IslandConfig(false, DEFAULT_OUTER_OCEAN, List.of());

    public IslandConfig {
        if (outerOcean == null || outerOcean.isBlank()) {
            outerOcean = DEFAULT_OUTER_OCEAN;
        }
        entries = List.copyOf(entries);
        name = name == null ? "" : name;
        if (oceanTemperature == IslandTemperature.STANDARD) {
            throw new IllegalArgumentException("ocean_temperature must be temperate, cold or warm; omit it for default behavior");
        }
    }
}
