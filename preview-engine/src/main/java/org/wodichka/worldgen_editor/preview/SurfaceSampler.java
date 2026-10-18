package org.wodichka.worldgen_editor.preview;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;

/** Evaluate a vanilla interpolation cell once for nearby columns, rather than rebuilding it per pixel. */
final class SurfaceSampler extends NoiseChunk {
    private final NoiseGeneratorSettings original;
    private final DensityFunction preliminaryDensity;
    private final boolean upperBand;
    private final Map<Long,Integer> surfaceLevels=new HashMap<>();
    private static final DensityFunctions.BeardifierOrMarker EMPTY_BEARDIFIER =
            (DensityFunctions.BeardifierOrMarker) DensityFunctions.BeardifierOrMarker.CODEC.codec().codec()
                    .parse(com.mojang.serialization.JsonOps.INSTANCE,new com.google.gson.JsonObject()).getOrThrow();
    private SurfaceSampler(RandomState random,int x,int z,NoiseGeneratorSettings settings) {
        this(random,x,z,settings,settings.noiseSettings());
    }
    private SurfaceSampler(RandomState random,int x,int z,NoiseGeneratorSettings settings,NoiseSettings band) {
        super(1,random,x,z,band,EMPTY_BEARDIFIER,settings,fluid(settings),Blender.empty());
        this.original=settings;
        this.upperBand=band.minY()!=settings.noiseSettings().minY();
        this.preliminaryDensity=upperBand ? random.router().initialDensityWithoutJaggedness().mapAll(this::wrap) : null;
    }
    @Override public int preliminarySurfaceLevel(int x,int z) {
        if(!upperBand)return super.preliminarySurfaceLevel(x,z);
        // Aquifers must retain the full-world preliminary height even when the density
        // interpolation only prepares an upper band. Otherwise deep oceans can change.
        int bx=QuartPos.toBlock(QuartPos.fromBlock(x)),bz=QuartPos.toBlock(QuartPos.fromBlock(z));
        long key=((long)bx<<32)^(bz&0xffffffffL);
        return surfaceLevels.computeIfAbsent(key,ignored->{
            var noise=original.noiseSettings();
            for(int y=noise.minY()+noise.height();y>=noise.minY();y-=noise.getCellHeight())
                if(preliminaryDensity.compute(new DensityFunction.SinglePointContext(bx,y,bz))>.390625)return y;
            return Integer.MAX_VALUE;
        });
    }
    private static Aquifer.FluidPicker fluid(NoiseGeneratorSettings settings) {
        var lava=new Aquifer.FluidStatus(-54,Blocks.LAVA.defaultBlockState());
        var water=new Aquifer.FluidStatus(settings.seaLevel(),settings.defaultFluid());
        return (x,y,z)->y<Math.min(-54,settings.seaLevel())?lava:water;
    }
    static int[] heights(RandomState random,int x,int z,NoiseGeneratorSettings settings) {
        int width=settings.noiseSettings().getCellWidth(),cellHeight=settings.noiseSettings().getCellHeight();
        var sampler=new SurfaceSampler(random,x,z,settings);int[] result=new int[width*width];boolean[] found=new boolean[result.length];
        Arrays.fill(result,settings.noiseSettings().minY()-1);int remaining=result.length;
        sampler.initializeForFirstCellX();sampler.advanceCellX(0);
        try {
            outer:for(int cell=settings.noiseSettings().height()/cellHeight-1;cell>=0;cell--) {
                sampler.selectCellYZ(cell,0);
                for(int dy=cellHeight-1;dy>=0;dy--){
                    int y=settings.noiseSettings().minY()+cell*cellHeight+dy;
                    sampler.updateForY(y,(double)dy/cellHeight);
                    for(int dx=0;dx<width;dx++){
                        sampler.updateForX(x+dx,(double)dx/width);
                        for(int dz=0;dz<width;dz++){
                            int index=dz*width+dx;if(found[index])continue;
                            sampler.updateForZ(z+dz,(double)dz/width);
                            BlockState state=sampler.getInterpolatedState();
                            if(state==null)state=settings.defaultBlock();
                            if(!state.isAir()){result[index]=y;found[index]=true;if(--remaining==0)break outer;}
                        }
                    }
                }
            }
        } finally {sampler.stopInterpolation();}
        return result;
    }

    static int height(RandomState random,int x,int z,NoiseGeneratorSettings settings) {
        var noise=settings.noiseSettings();
        int minY=Math.max(noise.minY(),48);
        var band=noise.clampToHeightAccessor(LevelHeightAccessor.create(minY,noise.minY()+noise.height()-minY));
        int width=noise.getCellWidth(),cellHeight=noise.getCellHeight();
        int cellX=Math.floorDiv(x,width)*width,cellZ=Math.floorDiv(z,width)*width;
        var sampler=new SurfaceSampler(random,cellX,cellZ,settings,band);
        sampler.initializeForFirstCellX();sampler.advanceCellX(0);
        try {
            for(int cell=band.height()/cellHeight-1;cell>=0;cell--) {
                sampler.selectCellYZ(cell,0);
                for(int dy=cellHeight-1;dy>=0;dy--){
                    int y=band.minY()+cell*cellHeight+dy;
                    sampler.updateForY(y,(double)dy/cellHeight);
                    sampler.updateForX(x,(double)(x-cellX)/width);
                    sampler.updateForZ(z,(double)(z-cellZ)/width);
                    BlockState state=sampler.getInterpolatedState();
                    if(state==null)state=settings.defaultBlock();
                    if(!state.isAir())return y;
                }
            }
            return Integer.MIN_VALUE;
        } finally {sampler.stopInterpolation();}
    }
}
