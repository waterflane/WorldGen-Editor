package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.wodichka.worldgen_editor.Worldgen_editor;

import java.util.stream.Stream;

public final class IslandBiomeSource extends BiomeSource {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Worldgen_editor.MOD_ID, "island_biome_source");
    public static final MapCodec<IslandBiomeSource> CODEC = BiomeSource.CODEC
            .fieldOf("delegate")
            .xmap(IslandBiomeSource::new, source -> source.delegate);

    private static boolean registered;

    private final BiomeSource delegate;

    private IslandBiomeSource(BiomeSource delegate) {
        this.delegate = delegate;
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        Registry.register(BuiltInRegistries.BIOME_SOURCE, ID, CODEC);
    }

    public static BiomeSource wrap(BiomeSource delegate) {
        if (delegate instanceof IslandBiomeSource) {
            return delegate;
        }
        return new IslandBiomeSource(delegate);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(
                        delegate.possibleBiomes().stream(),
                        IslandWorldState.oceanBiomes().stream(),
                        IslandWorldState.fallbackLandBiomes().stream()
                )
                .flatMap(stream -> stream)
                .distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        IslandMask mask = IslandWorldState.mask();
        if (mask.isEmpty()) {
            return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }

        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);
        double island = mask.sample(blockX, blockZ);
        if (island < IslandTerrainHooks.BEACH_START_MASK) {
            Holder<Biome> ocean = IslandWorldState.oceanBiome(island);
            if (ocean != null) {
                return ocean;
            }
        }

        return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }
}
