package org.wodichka.worldgen_editor.verification;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.LevelResource;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;
import java.nio.file.*;
import java.util.*;

/** Actual v4 chunks, original generator base heights and the standalone preview. */
final class CoastVerification {
    static void verify(GameTestHelper helper)throws Exception {
        var level=helper.getLevel();var context=IslandWorldState.context();
        boolean natural=Boolean.getBoolean("wge.naturalCoastVerification");long seed=natural?3:2;
        helper.assertTrue(level.getSeed()==seed&&context!=null&&context.generationVersion()==4,"Expected isolated v4 world with the requested seed");
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        var request=new JsonObject();request.addProperty("seed",Long.toString(seed));request.addProperty("generation_version",4);request.add("config",IslandConfigLoader.toJson(context.config()));
        String session=GenerationGameTests.post("session",request).get("session").getAsString();
        var points=new ArrayList<BlockPos>();
        for(int x:new int[]{-480,-400,-336,-304,-256,-208,-160,0,128,200,256,320,400,480,640})points.add(new BlockPos(x,0,x<0?-256:128));
        points.add(new BlockPos(0,0,0));points.add(new BlockPos(16000,0,0));
        if(natural){points.add(new BlockPos(99,0,-20));points.add(new BlockPos(330,0,-168));points.add(new BlockPos(96,0,-24));points.add(new BlockPos(328,0,-168));}
        // Include both sides of real contours, rather than treating a successful launch as parity.
        int beaches=0,shelves=0;
        for(int z=-512;z<=512&&(beaches<3||shelves<3);z+=16)for(int x=-512;x<=640&&(beaches<3||shelves<3);x+=16){
            var coast=context.mask().sampleInfo(x,z).coast();if(coast==null)continue;double d=coast.distance();
            if((d>=4&&d<=coast.profile().beachWidthBlocks()&&beaches<3)||(d<=-8&&-d<=coast.profile().shallowOceanWidthBlocks()&&shelves<3)){
                points.add(new BlockPos(x,0,z));if(d>=0)beaches++;else shelves++;
            }
        }
        helper.assertTrue(beaches>0&&shelves>0,"Fixture must exercise inland beach and offshore shelf");
        var heights=new ArrayList<Integer>();var biomeIds=new ArrayList<String>();int biomeChecks=0;
        for(var p:points){int x=p.getX(),z=p.getZ(),y=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random)-1;
            var tile=GenerationGameTests.post("tile",GenerationGameTests.query(session,x,0,z,"surface"));
            helper.assertTrue(tile.getAsJsonArray("heights").get(0).getAsInt()==y,"Coast base height differs from preview at "+x+","+z);
            GenerationGameTests.compareFastSurface(helper,session,x,y,z);
            for(int sampleY:new int[]{y,300,0,-32}){biomeIds.add(GenerationGameTests.compareBiome(helper,session,x,sampleY,z));biomeChecks++;}heights.add(y);
        }
        level.getServer().saveEverything(false,true,true);Path world=level.getServer().getWorldPath(LevelResource.ROOT);
        var snapshot=WorldSnapshotStore.loadOrCreate(world.resolve("worldgen_editor/worldgen_editor.json"),"default",3,name->{throw new AssertionError("Reopen read a global preset");});
        helper.assertTrue(snapshot.generationVersion()==4&&snapshot.config().equals(context.config()),"Pinned coast profile changed on reload");
        var saved=net.minecraft.nbt.NbtIo.readCompressed(world.resolve("level.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var restored=(NoiseBasedChunkGenerator)net.minecraft.world.level.chunk.ChunkGenerator.CODEC.parse(RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,level.registryAccess()),
            saved.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld").getCompound("generator")).getOrThrow();
        var binding=new GenerationBinding();binding.set(new GenerationContext(seed,snapshot.config(),4,context.biomes()));((IslandBiomeSource)restored.getBiomeSource()).bind(binding);
        restored.generatorSettings().value().noiseRouter().mapAll(function->{if(function instanceof IslandContinentsFunction islands)islands.bind(binding);if(function instanceof IslandCoastFunction coast)coast.bind(binding);if(function instanceof IslandDepthFunction depth)depth.bind(binding);return function;});
        var restoredNoise=RandomState.create(restored.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
        var manager=new BiomeManager((qx,qy,qz)->restored.getBiomeSource().getNoiseBiome(qx,qy,qz,restoredNoise.sampler()),BiomeManager.obfuscateSeed(seed));int index=0;
        for(int i=0;i<points.size();i++){var p=points.get(i);int y=heights.get(i);helper.assertTrue(restored.getBaseHeight(p.getX(),p.getZ(),Heightmap.Types.WORLD_SURFACE_WG,level,restoredNoise)-1==y,"Reopened coast terrain changed");
            for(int sampleY:new int[]{y,300,0,-32})helper.assertTrue(manager.getBiome(new BlockPos(p.getX(),sampleY,p.getZ())).unwrapKey().orElseThrow().location().toString().equals(biomeIds.get(index++)),"Reopened coast biome changed");}
        var solidProfiles=new JsonArray();int maxSolidStep=0;
        for(int z:new int[]{-256,128}){int previousFloor=0;boolean first=true;var profile=new JsonArray();
            for(int x=-512;x<=768;x+=4){var column=generator.getBaseColumn(x,z,level,random);int floor=level.getMinBuildHeight();
                for(int y=level.getMaxBuildHeight()-1;y>=level.getMinBuildHeight();y--){var state=column.getBlock(y);if(!state.isAir()&&state.getFluidState().isEmpty()){floor=y;break;}}
                if(!first)maxSolidStep=Math.max(maxSolidStep,Math.abs(floor-previousFloor));first=false;previousFloor=floor;
                var row=new JsonArray();row.add(x);row.add(z);row.add(floor);profile.add(row);
            }solidProfiles.add(profile);
        }
        var result=new JsonObject();result.addProperty("seed",seed);result.addProperty("generation_version",4);result.addProperty("coast_case",System.getProperty("wge.coastCase"));
        result.addProperty("biome_samples",biomeChecks);result.addProperty("base_height_samples",points.size());result.addProperty("fast_surface_samples",points.size());result.addProperty("mismatches",0);result.addProperty("pinned_snapshot_reload",true);
        result.add("solid_profiles",solidProfiles);result.addProperty("solid_profile_step_blocks",4);result.addProperty("solid_profile_max_height_step",maxSolidStep);
        result.add("points",new Gson().toJsonTree(points.stream().map(p->List.of(p.getX(),p.getZ())).toList()));result.add("heights",new Gson().toJsonTree(heights));
        if(natural){
            var regions=new JsonArray();int lateBeach=0,finishedColumns=0;
            for(var center:new int[][]{{99,-20},{330,-168}}){
                var rows=new JsonArray();
                for(int z=center[1]-32;z<=center[1]+32;z+=4)for(int x=center[0]-32;x<=center[0]+32;x+=4){
                    level.getChunk(x>>4,z>>4);int ground=ground(level,x,z),top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
                    var pos=new BlockPos(x,ground+1,z);var biome=level.getBiome(pos);
                    var sample=context.mask().sampleInfo(x,z);var coast=sample.coast();
                    if(context.biomeRules().isBeach(biome)&&coast!=null&&coast.distance()>coast.profile().beachWidthBlocks()*1.15+12)lateBeach++;
                    var row=new JsonArray();row.add(x);row.add(z);row.add(ground);row.add(top);row.add(biome.unwrapKey().orElseThrow().location().toString());
                    row.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x,ground,z)).getBlock()).toString());
                    row.add(generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random)-1);
                    row.add(NaturalCoast.approximateHeight(random.router().depth().compute(new DensityFunction.SinglePointContext(x,generator.getSeaLevel(),z)),generator.getSeaLevel()));
                    row.add(context.mask().geometry(x,z));rows.add(row);finishedColumns++;
                }
                regions.add(rows);
            }
            int riverFloor=ground(level,96,-24),jungleFloor=ground(level,328,-168);
            helper.assertTrue(riverFloor<62,"Finished river channel was filled to sea level: "+riverFloor);
            helper.assertTrue(jungleFloor>=63,"Finished jungle land was flooded by the coastal profile: "+jungleFloor);
            helper.assertTrue(lateBeach==0,"Original beach leaked beyond the configured coastal zone: "+lateBeach);
            result.add("finished_regions",regions);result.addProperty("finished_columns",finishedColumns);result.addProperty("extra_inland_beaches",lateBeach);
            result.addProperty("reported_river_floor",riverFloor);result.addProperty("reported_jungle_floor",jungleFloor);
        }
        Files.writeString(Path.of("coast-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));helper.succeed();
    }
    private static int ground(net.minecraft.server.level.ServerLevel level,int x,int z){
        for(int y=Math.min(level.getMaxBuildHeight()-1,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));y>=level.getMinBuildHeight();y--){
            var state=level.getBlockState(new BlockPos(x,y,z));
            if(state.blocksMotion()&&state.getFluidState().isEmpty()&&!state.is(net.minecraft.tags.BlockTags.LEAVES)&&!state.is(net.minecraft.tags.BlockTags.LOGS))return y;
        }
        return level.getMinBuildHeight();
    }
}
