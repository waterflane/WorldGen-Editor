package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.*;

/** Save registry references, not the expanded runtime graph (which contains non-codec markers). */
public final class IslandGeneratorCodec {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("worldgen_editor","island_noise");
    public static final MapCodec<NoiseBasedChunkGenerator> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(NoiseBasedChunkGenerator::getBiomeSource),
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator->
            (Object)generator instanceof OriginalNoiseSettings original?original.wge$originalSettings():generator.generatorSettings())
    ).apply(i,NoiseBasedChunkGenerator::new));
    private IslandGeneratorCodec(){}
}
