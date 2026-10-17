package org.wodichka.worldgen_editor.neoforge;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.fml.ModList;
import org.wodichka.worldgen_editor.world.*;

/** Optional adapter; TerraBlender is never a required dependency. */
final class TerraBlenderCompatibility {
    static void initialize(MinecraftServer server) {
        if (!ModList.get().isLoaded("terrablender") || IslandWorldState.generationVersion() < 2) return;
        var stem = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator generator)
                || !(generator.getBiomeSource() instanceof IslandBiomeSource islands)) return;
        try {
            // Initialize the actual delegate and actual settings, through TB's own implementation.
            var delegateGenerator = new NoiseBasedChunkGenerator(islands.delegate(), generator.generatorSettings());
            Class.forName("terrablender.util.LevelUtils").getMethod("initializeBiomes", RegistryAccess.class,
                    Holder.class, ResourceKey.class, ChunkGenerator.class, long.class)
                    .invoke(null, server.registryAccess(), stem.type(), LevelStem.OVERWORLD,
                            delegateGenerator, server.getWorldData().worldGenOptions().seed());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not initialize TerraBlender for the WGE biome delegate", error);
        }
    }
}
