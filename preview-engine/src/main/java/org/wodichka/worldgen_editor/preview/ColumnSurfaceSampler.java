package org.wodichka.worldgen_editor.preview;

import net.minecraft.world.level.levelgen.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standalone preview: reuse a compiled density graph and evaluate only queried columns.
 * Kept outside Minecraft packages so NeoForge can load it without split-package modules.
 */
public final class ColumnSurfaceSampler extends NoiseChunk {
    private static final DensityFunctions.BeardifierOrMarker NO_STRUCTURES=(DensityFunctions.BeardifierOrMarker)DensityFunctions.BeardifierOrMarker.CODEC.codec().codec().parse(com.mojang.serialization.JsonOps.INSTANCE,new com.google.gson.JsonObject()).getOrThrow();
    private final RandomState random;
    private final NoiseGeneratorSettings settings;
    private final DensityFunction density,preliminary;
    private final Map<Long,Integer> surfaceCache=new LinkedHashMap<>(128,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Long,Integer> entry){return size()>4096;}
    };
    private final Aquifer.FluidPicker fluids;
    private long aquiferChunk=Long.MIN_VALUE;
    private Aquifer columnAquifer;

    public ColumnSurfaceSampler(RandomState random,NoiseGeneratorSettings settings) {
        // Aquifer only needs this object's preliminarySurfaceLevel override. The inherited
        // chunk state is never advanced or used to evaluate the compiled column graph.
        super(1,random,0,0,settings.noiseSettings(),NO_STRUCTURES,settings,fluidPicker(settings),Blender.empty());
        this.random=random;this.settings=settings;this.fluids=fluidPicker(settings);
        var compiled=new HashMap<DensityFunction,DensityFunction>();
        DensityFunction.Visitor visitor=function->compiled.computeIfAbsent(function,key->{
            if(Markers.TYPE.isInstance(key))return new CachedMarker(key,settings.noiseSettings());
            if(key instanceof DensityFunctions.HolderHolder holder)return holder.function().value();
            return key;
        });
        density=random.router().finalDensity().mapAll(visitor);
        preliminary=random.router().initialDensityWithoutJaggedness().mapAll(visitor);
    }

    private static Aquifer.FluidPicker fluidPicker(NoiseGeneratorSettings settings){
        var lava=new Aquifer.FluidStatus(-54,Blocks.LAVA.defaultBlockState());
        var water=new Aquifer.FluidStatus(settings.seaLevel(),settings.defaultFluid());
        return (x,y,z)->y<Math.min(-54,settings.seaLevel())?lava:water;
    }

    @Override public int preliminarySurfaceLevel(int x,int z){
        int bx=QuartPos.toBlock(QuartPos.fromBlock(x)),bz=QuartPos.toBlock(QuartPos.fromBlock(z));
        long key=((long)bx<<32)^(bz&0xffffffffL);
        return surfaceCache.computeIfAbsent(key,ignored->{
            var noise=settings.noiseSettings();
            for(int y=noise.minY()+noise.height();y>=noise.minY();y-=noise.getCellHeight())
                if(preliminary.compute(new DensityFunction.SinglePointContext(bx,y,bz))>.390625)return y;
            return Integer.MAX_VALUE;
        });
    }

    public int height(int x,int z){
        selectAquifer(x,z);
        var noise=settings.noiseSettings();
        for(int y=noise.minY()+noise.height()-1;y>=noise.minY();y--)
            if(solidOrFluid(x,y,z))return y;
        return noise.minY()-1;
    }

    public int[] heights(int x,int z){
        selectAquifer(x,z);
        var noise=settings.noiseSettings();int width=noise.getCellWidth();
        int[] result=new int[width*width];java.util.Arrays.fill(result,Integer.MIN_VALUE);
        int remaining=result.length;
        for(int y=noise.minY()+noise.height()-1;y>=noise.minY();y--)
            for(int dx=0;dx<width;dx++)for(int dz=0;dz<width;dz++){
                int i=dz*width+dx;
                if(result[i]==Integer.MIN_VALUE && solidOrFluid(x+dx,y,z+dz)){
                    result[i]=y;if(--remaining==0)return result;
                }
            }
        for(int i=0;i<result.length;i++)if(result[i]==Integer.MIN_VALUE)result[i]=noise.minY()-1;
        return result;
    }

    private boolean solidOrFluid(int x,int y,int z){
        var point=new DensityFunction.SinglePointContext(x,y,z);
        BlockState state=columnAquifer.computeSubstance(point,density.compute(point));
        if(state==null)state=settings.defaultBlock();
        // Ore vein replacement changes the solid material, never whether it is air.
        return !state.isAir();
    }

    private void selectAquifer(int x,int z){
        long chunk=ChunkPos.asLong(Math.floorDiv(x,16),Math.floorDiv(z,16));
        if(columnAquifer==null || aquiferChunk!=chunk){
            var noise=settings.noiseSettings();
            columnAquifer=settings.isAquifersEnabled()
                ? Aquifer.create(this,new ChunkPos(chunk),random.router(),random.aquiferRandom(),noise.minY(),noise.height(),fluids)
                : Aquifer.createDisabled(fluids);
            aquiferChunk=chunk;
        }
    }

    private static final class Markers {
        static final Class<?> TYPE;
        static final java.lang.reflect.Method WRAPPED,KIND;
        static {try{TYPE=Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$Marker");WRAPPED=TYPE.getDeclaredMethod("wrapped");KIND=TYPE.getDeclaredMethod("type");WRAPPED.setAccessible(true);KIND.setAccessible(true);}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
        static Object call(java.lang.reflect.Method method,DensityFunction function){try{return method.invoke(function);}catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported Minecraft density markers",error);}}
    }
    private static final class CachedMarker implements DensityFunction {
        private final DensityFunction original;
        private final String type;
        private final DensityFunction input;
        private final int width,height;
        private final long[] keys=new long[64];
        private final double[] values=new double[64];
        private final boolean[] valid=new boolean[64];

        CachedMarker(DensityFunction marker,NoiseSettings noise){
            original=marker;input=(DensityFunction)Markers.call(Markers.WRAPPED,marker);type=((Enum<?>)Markers.call(Markers.KIND,marker)).name();width=noise.getCellWidth();height=noise.getCellHeight();
        }
        private double cached(int x,int y,int z){
            long key=BlockPos.asLong(x,y,z);
            int slot=(int)((key^(key>>>32))*0x9e3779b97f4a7c15L>>>58);
            if(valid[slot]&&keys[slot]==key)return values[slot];
            double value=input.compute(new DensityFunction.SinglePointContext(x,y,z));
            keys[slot]=key;values[slot]=value;valid[slot]=true;return value;
        }
        @Override public double compute(FunctionContext point){
            int x=point.blockX(),y=point.blockY(),z=point.blockZ();
            return switch(type){
                case "FlatCache" -> cached(QuartPos.toBlock(QuartPos.fromBlock(x)),0,QuartPos.toBlock(QuartPos.fromBlock(z)));
                case "Cache2D" -> cached(x,0,z);
                case "CacheOnce" -> cached(x,y,z);
                case "CacheAllInCell" -> input.compute(point);
                case "Interpolated" -> {
                    int bx=Math.floorDiv(x,width)*width,by=Math.floorDiv(y,height)*height,bz=Math.floorDiv(z,width)*width;
                    double fx=(double)(x-bx)/width,fy=(double)(y-by)/height,fz=(double)(z-bz)/width;
                    // Grid-aligned overview samples lie on an interpolation edge. Zero
                    // weights cannot contribute, so only that edge's two corners are needed.
                    if(fx==0&&fz==0)yield Mth.lerp(fy,cached(bx,by,bz),cached(bx,by+height,bz))+0.0;
                    if(fx==0)yield Mth.lerp(fz,Mth.lerp(fy,cached(bx,by,bz),cached(bx,by+height,bz)),
                        Mth.lerp(fy,cached(bx,by,bz+width),cached(bx,by+height,bz+width)))+0.0;
                    if(fz==0)yield Mth.lerp2(fx,fy,cached(bx,by,bz),cached(bx+width,by,bz),
                        cached(bx,by+height,bz),cached(bx+width,by+height,bz))+0.0;
                    yield Mth.lerp3(fx,fy,fz,cached(bx,by,bz),cached(bx+width,by,bz),
                        cached(bx,by+height,bz),cached(bx+width,by+height,bz),
                        cached(bx,by,bz+width),cached(bx+width,by,bz+width),
                        cached(bx,by+height,bz+width),cached(bx+width,by+height,bz+width));
                }
                default -> throw new IllegalStateException("Unsupported density marker: "+type);
            };
        }
        @Override public void fillArray(double[] array,ContextProvider context){context.fillAllDirectly(array,this);}
        @Override public DensityFunction mapAll(Visitor visitor){return original.mapAll(visitor);}
        @Override public double minValue(){return original.minValue();}
        @Override public double maxValue(){return original.maxValue();}
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return original.codec();}
    }
}
