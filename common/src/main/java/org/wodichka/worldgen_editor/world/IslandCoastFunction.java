package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Version 3 ocean ceiling: retains the original caves/density below the coastal envelope. */
public final class IslandCoastFunction implements DensityFunction {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("worldgen_editor","island_coast");
    public static final MapCodec<IslandCoastFunction> MAP_CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(f->f.delegate),
        Codec.INT.fieldOf("sea_level").forGetter(f->f.seaLevel)).apply(i,IslandCoastFunction::new));
    private final DensityFunction delegate;
    private final int seaLevel;
    private final GenerationBinding binding;
    public IslandCoastFunction(DensityFunction delegate,int seaLevel){this(delegate,seaLevel,new GenerationBinding());}
    private IslandCoastFunction(DensityFunction delegate,int seaLevel,GenerationBinding binding){this.delegate=delegate;this.seaLevel=seaLevel;this.binding=binding;}
    public void bind(GenerationBinding value){binding.attach(value);}
    public IslandCoastFunction withBinding(GenerationBinding value){return new IslandCoastFunction(delegate,seaLevel,value);}
    public double compute(FunctionContext point){
        double original=delegate.compute(point);var context=binding.get();
        if(context==null||context.generationVersion()<3||context.mask().isEmpty())return original;
        var sample=context.mask().sampleInfo(point.blockX(),point.blockZ());double mask=sample.value();
        if(context.generationVersion()>=4&&sample.coast()!=null){
            var envelope=CoastTerrain.envelope(sample.coast(),seaLevel);
            if(envelope!=null)return CoastTerrain.shape(original,point.blockY(),envelope);
            if(sample.coast().distance()>=0)return original;
            return Math.min(original,(seaLevel-43.2-point.blockY())/8);
        }
        if(mask>=.72)return original;
        double ceiling=(seaLevel+(mask-.54)*80-point.blockY())/8;
        double weight=Math.max(0,Math.min(1,(.72-mask)/.14));
        double shaped=original+(Math.min(original,ceiling)-original)*weight;
        return shaped;
    }
    public void fillArray(double[] data,ContextProvider provider){provider.fillAllDirectly(data,this);}
    public DensityFunction mapAll(Visitor visitor){return visitor.apply(new IslandCoastFunction(delegate.mapAll(visitor),seaLevel,binding));}
    public double minValue(){return Double.NEGATIVE_INFINITY;}
    public double maxValue(){return Math.max(2.0D,delegate.maxValue());}
    public KeyDispatchDataCodec<? extends DensityFunction> codec(){return KeyDispatchDataCodec.of(MAP_CODEC);}
}
