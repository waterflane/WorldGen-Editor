package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
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
            Codec.STRING.optionalFieldOf("preset", DEFAULT_PRESET).forGetter(source -> source.presetName),
            Codec.intRange(1, 5).optionalFieldOf("generation_version", 1).forGetter(source -> source.generationVersion),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance, IslandBiomeSource::new));

    private static boolean registered;

    private final BiomeSource delegate;
    private final String presetName;
    private final int generationVersion;
    private final HolderGetter<Biome> biomeGetter;
    private final GenerationBinding binding = new GenerationBinding();
    private int seaLevel=63;

    private IslandBiomeSource(BiomeSource delegate, String presetName, int generationVersion, HolderGetter<Biome> biomeGetter) {
        this.delegate = delegate;
        this.presetName = presetName;
        this.generationVersion = generationVersion;
        this.biomeGetter = biomeGetter;
    }

    public String presetName() {
        return presetName;
    }

    public static IslandBiomeSource forPreset(BiomeSource delegate,String file,int version,HolderGetter<Biome> biomes){
        return new IslandBiomeSource(delegate,file,version,biomes);
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        Registry.register(BuiltInRegistries.BIOME_SOURCE, ID, CODEC);
    }

    public BiomeSource delegate() { return delegate; }
    public int generationVersion() { return generationVersion; }
    public GenerationBinding binding() { return binding; }
    public void bind(GenerationBinding value) { binding.attach(value); if(binding.get()!=null)binding.get().biomeRules().bindDelegate(delegate); }
    public void seaLevel(int value){seaLevel=value;}

    /** Returns a biome only if this column's WGE rule is independent of Y and the delegate. */
    public Holder<Biome> heightIndependentBiome(int quartX,int quartZ) {
        GenerationContext context=binding.get();
        if(context==null || context.mask().isEmpty())return null;
        if(NaturalCoast.active(context))return null;
        int x=QuartPos.toBlock(quartX),z=QuartPos.toBlock(quartZ);
        var sample=context.mask().sampleInfo(x,z);
        if(sample.coast()!=null) {
            if(sample.coast().distance()<0)return context.biomeRules().customCoastBiome(sample.coast(),null,x,z,coastClimateSource(sample));
            return null;
        }
        if(sample.value()>=IslandTerrainHooks.FULL_OCEAN_MASK)return null;
        if(sample.oceanSource()==null && sample.archipelagoSource()==null && sample.landSource()==null)
            return context.biomeRules().outerOceanBiome();
        var source=sample.oceanSource()!=null ? sample.oceanSource()
                : sample.archipelagoSource()!=null ? sample.archipelagoSource() : sample.landSource();
        // All WGE ocean choices now depend on explicit config and mask, never the delegate or Y.
        return context.biomeRules().oceanBiome(sample.value(),source,null,x,z);
    }

    private static IslandMask.SourceInfo coastClimateSource(IslandMask.SampleInfo sample) {
        if(sample.coast().distance()<0&&sample.oceanSource()!=null)return sample.oceanSource();
        if(sample.coast().distance()<0&&sample.archipelagoSource()!=null)return sample.archipelagoSource();
        return sample.landSource()!=null?sample.landSource():sample.archipelagoSource();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        GenerationContext context = binding.get();
        Stream<Holder<Biome>> extra = context == null ? Stream.empty()
                : Stream.concat(context.biomes().holders().filter(context.biomeRules()::isOceanBiome), context.biomeRules().fallbackLandBiomes().stream());
        return Stream.concat(delegate.possibleBiomes().stream(), extra).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        GenerationContext context = binding.get();
        if (context == null) return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        IslandMask mask = context.mask();
        if (mask.isEmpty()) {
            return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }

        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);
        IslandMask.SampleInfo sample = mask.sampleInfo(blockX, blockZ);
        Holder<Biome> delegateBiome = delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        if(NaturalCoast.active(context)&&sample.coast()!=null){
            double surface=NaturalCoast.approximateHeight(sampler.depth().compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(blockX,seaLevel,blockZ)),seaLevel);
            var selected=context.biomeRules().naturalCoastBiome(sample.coast(),delegateBiome,blockX,blockZ,coastClimateSource(sample),surface,seaLevel);
            if(selected!=null)return selected;
            var source=sample.landSource()!=null?sample.landSource():sample.archipelagoSource();
            return context.biomeRules().landBiome(Math.max(IslandTerrainHooks.LAND_MASK,mask.geometry(blockX,blockZ)),source,delegateBiome,blockX,blockZ,context.generationVersion()>=5?sampler.sample(quartX,quartY,quartZ):null);
        }
        if(sample.coast()!=null) {
            var coast=context.biomeRules().customCoastBiome(sample.coast(),delegateBiome,blockX,blockZ,coastClimateSource(sample));
            if(coast!=null)return coast;
        }
        if (sample.value() < IslandTerrainHooks.FULL_OCEAN_MASK) {
            if (sample.oceanSource() == null && sample.archipelagoSource() == null && sample.landSource() == null) {
                Holder<Biome> outerOcean = context.biomeRules().outerOceanBiome();
                if (outerOcean != null) {
                    return outerOcean;
                }
            }
            IslandMask.SourceInfo source = sample.oceanSource() != null
                    ? sample.oceanSource()
                    : sample.archipelagoSource() != null ? sample.archipelagoSource() : sample.landSource();
            Holder<Biome> ocean = context.biomeRules().oceanBiome(sample.value(), source, delegateBiome, blockX, blockZ);
            if (ocean != null) {
                return ocean;
            }
        }

        IslandMask.SourceInfo source = sample.landSource() != null ? sample.landSource() : sample.archipelagoSource();
        return context.biomeRules().landBiome(sample.value(), source, delegateBiome, blockX, blockZ,context.generationVersion()>=5?sampler.sample(quartX,quartY,quartZ):null);
    }
}
