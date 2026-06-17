package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
    public static final String DEFAULT_PRESET = "default";
    public static final MapCodec<IslandBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("delegate").forGetter(source -> source.delegate),
            Codec.STRING.optionalFieldOf("preset", DEFAULT_PRESET).forGetter(source -> source.presetName)
    ).apply(instance, IslandBiomeSource::new));

    private static boolean registered;

    private final BiomeSource delegate;
    private final String presetName;

    private IslandBiomeSource(BiomeSource delegate, String presetName) {
        this.delegate = delegate;
        this.presetName = presetName;
    }

    public String presetName() {
        return presetName;
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
        return new IslandBiomeSource(delegate, DEFAULT_PRESET);
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
        IslandMask.SampleInfo sample = mask.sampleInfo(blockX, blockZ);
        Holder<Biome> delegateBiome = delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        if (sample.value() < IslandTerrainHooks.FULL_OCEAN_MASK) {
            if (sample.oceanSource() == null && sample.archipelagoSource() == null && sample.landSource() == null) {
                Holder<Biome> outerOcean = IslandWorldState.outerOceanBiome();
                if (outerOcean != null) {
                    return outerOcean;
                }
            }
            IslandMask.SourceInfo source = sample.oceanSource() != null
                    ? sample.oceanSource()
                    : sample.archipelagoSource() != null ? sample.archipelagoSource() : sample.landSource();
            Holder<Biome> ocean = IslandWorldState.oceanBiome(sample.value(), source, delegateBiome, blockX, blockZ);
            if (ocean != null) {
                return ocean;
            }
        }

        IslandMask.SourceInfo source = sample.landSource() != null ? sample.landSource() : sample.archipelagoSource();
        return IslandWorldState.landBiome(sample.value(), source, delegateBiome, blockX, blockZ);
    }
}
