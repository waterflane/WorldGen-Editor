package org.wodichka.worldgen_editor.preview;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.Arrays;

/** Evaluate a vanilla interpolation cell once for nearby columns, rather than rebuilding it per pixel. */
final class SurfaceSampler extends NoiseChunk {
    private static final DensityFunctions.BeardifierOrMarker EMPTY_BEARDIFIER =
            (DensityFunctions.BeardifierOrMarker) DensityFunctions.BeardifierOrMarker.CODEC.codec().codec()
                    .parse(com.mojang.serialization.JsonOps.INSTANCE,new com.google.gson.JsonObject()).getOrThrow();
    private SurfaceSampler(RandomState random,int x,int z,NoiseGeneratorSettings settings) {
        super(1,random,x,z,settings.noiseSettings(),EMPTY_BEARDIFIER,settings,fluid(settings),Blender.empty());
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
}
