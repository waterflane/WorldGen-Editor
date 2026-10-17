package org.wodichka.worldgen_editor.world;

import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import org.wodichka.worldgen_editor.config.IslandConfig;

/** One immutable generation snapshot, shared by the game and the preview engine. */
public record GenerationContext(long seed, IslandConfig config, int generationVersion,
                                Registry<Biome> biomes, IslandMask mask, IslandBiomeRules biomeRules) {
    public static final int LEGACY_VERSION = 1;
    public static final int CURRENT_VERSION = 2;

    public GenerationContext(long seed, IslandConfig config, int version, Registry<Biome> biomes) {
        this(seed, config, validateVersion(version), biomes,
                new IslandMask(config.enabled() ? config : IslandConfig.EMPTY, seed),
                new IslandBiomeRules(biomes, config, seed, version));
    }

    public static int validateVersion(int version) {
        if (version < LEGACY_VERSION || version > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported generation_version: " + version);
        }
        return version;
    }
}
