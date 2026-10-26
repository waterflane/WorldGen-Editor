package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import java.util.*;

/** Adjusts only the pre-cave depth subtree; never fills or clips final-density columns. */
public final class IslandDepthFunction implements DensityFunction {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("worldgen_editor","island_depth");
    public static final MapCodec<IslandDepthFunction> MAP_CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(f->f.delegate),
        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("ridges").forGetter(f->f.ridges),
        Codec.INT.fieldOf("sea_level").forGetter(f->f.seaLevel)).apply(i,IslandDepthFunction::new));
    private final DensityFunction delegate,ridges;
    private final int seaLevel;
    private final GenerationBinding binding;
    private static final class Columns extends LinkedHashMap<Long,Double> {
        GenerationContext context;
        Columns(){super(128,.75f,true);}
        protected boolean removeEldestEntry(Map.Entry<Long,Double> entry){return size()>512;}
    }
    private final ThreadLocal<Columns> columns;
    public IslandDepthFunction(DensityFunction delegate,DensityFunction ridges,int seaLevel){this(delegate,ridges,seaLevel,new GenerationBinding(),ThreadLocal.withInitial(Columns::new));}
    private IslandDepthFunction(DensityFunction delegate,DensityFunction ridges,int seaLevel,GenerationBinding binding,ThreadLocal<Columns> columns){this.delegate=delegate;this.ridges=ridges;this.seaLevel=seaLevel;this.binding=binding;this.columns=columns;}
    public void bind(GenerationBinding value){binding.attach(value);}
    public IslandDepthFunction withBinding(GenerationBinding value){return new IslandDepthFunction(delegate,ridges,seaLevel,value,ThreadLocal.withInitial(Columns::new));}
    public double compute(FunctionContext point){
        double original=delegate.compute(point);var context=binding.get();
        if(!NaturalCoast.active(context))return original;
        var cache=columns.get();if(cache.context!=context){cache.clear();cache.context=context;}
        long key=((long)point.blockX()<<32)^(point.blockZ()&0xffffffffL);
        Double correction=cache.get(key);
        if(correction==null){
            var sample=context.mask().sampleInfo(point.blockX(),point.blockZ()).coast();
            if(sample==null||NaturalCoast.influence(sample)<=0)correction=0.0;
            else {
                var sea=new SinglePointContext(point.blockX(),seaLevel,point.blockZ());
                correction=NaturalCoast.depthCorrection(delegate.compute(sea),sample,ridges.compute(sea));
            }
            cache.put(key,correction);
        }
        return original+correction;
    }
    public void fillArray(double[] data,ContextProvider provider){provider.fillAllDirectly(data,this);}
    public DensityFunction mapAll(Visitor visitor){return visitor.apply(new IslandDepthFunction(delegate.mapAll(visitor),ridges.mapAll(visitor),seaLevel,binding,columns));}
    public double minValue(){return Double.NEGATIVE_INFINITY;}
    public double maxValue(){return Double.POSITIVE_INFINITY;}
    public KeyDispatchDataCodec<? extends DensityFunction> codec(){return KeyDispatchDataCodec.of(MAP_CODEC);}
}
