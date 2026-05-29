package org.wodichka.worldgen_editor.config;

import java.util.List;

public record IslandConfig(boolean enabled, List<IslandEntry> entries) {
    public static final IslandConfig EMPTY = new IslandConfig(false, List.of());

    public IslandConfig {
        entries = List.copyOf(entries);
    }
}
