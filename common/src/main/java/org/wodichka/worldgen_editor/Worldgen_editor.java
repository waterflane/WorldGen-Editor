package org.wodichka.worldgen_editor;

import org.wodichka.worldgen_editor.world.IslandWorldState;
import org.wodichka.worldgen_editor.world.IslandBiomeSource;
import org.wodichka.worldgen_editor.world.IslandDensityFunction;

public final class Worldgen_editor {
    public static final String MOD_ID = "worldgen_editor";

    private static boolean initialized;

    private Worldgen_editor() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        IslandBiomeSource.register();
        IslandDensityFunction.register();
        IslandWorldState.loadGlobalConfig();
    }
}
