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
        helper.assertTrue(level.getSeed()==2&&context!=null&&context.generationVersion()==4,"Expected isolated seed-2 v4 world");
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        var request=new JsonObject();request.addProperty("seed","2");request.addProperty("generation_version",4);request.add("config",IslandConfigLoader.toJson(context.config()));
        String session=GenerationGameTests.post("session",request).get("session").getAsString();
        var points=new ArrayList<BlockPos>();
        for(int x:new int[]{-480,-400,-336,-304,-256,-208,-160,0,128,200,256,320,400,480,640})points.add(new BlockPos(x,0,x<0?-256:128));
        points.add(new BlockPos(0,0,0));points.add(new BlockPos(16000,0,0));
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
        var binding=new GenerationBinding();binding.set(new GenerationContext(2,snapshot.config(),4,context.biomes()));((IslandBiomeSource)restored.getBiomeSource()).bind(binding);
        restored.generatorSettings().value().noiseRouter().mapAll(function->{if(function instanceof IslandContinentsFunction islands)islands.bind(binding);if(function instanceof IslandCoastFunction coast)coast.bind(binding);return function;});
        var restoredNoise=RandomState.create(restored.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),2);
        var manager=new BiomeManager((qx,qy,qz)->restored.getBiomeSource().getNoiseBiome(qx,qy,qz,restoredNoise.sampler()),BiomeManager.obfuscateSeed(2));int index=0;
        for(int i=0;i<points.size();i++){var p=points.get(i);int y=heights.get(i);helper.assertTrue(restored.getBaseHeight(p.getX(),p.getZ(),Heightmap.Types.WORLD_SURFACE_WG,level,restoredNoise)-1==y,"Reopened coast terrain changed");
            for(int sampleY:new int[]{y,300,0,-32})helper.assertTrue(manager.getBiome(new BlockPos(p.getX(),sampleY,p.getZ())).unwrapKey().orElseThrow().location().toString().equals(biomeIds.get(index++)),"Reopened coast biome changed");}
        var result=new JsonObject();result.addProperty("seed",2);result.addProperty("generation_version",4);result.addProperty("coast_case",System.getProperty("wge.coastCase"));
        result.addProperty("biome_samples",biomeChecks);result.addProperty("base_height_samples",points.size());result.addProperty("fast_surface_samples",points.size());result.addProperty("mismatches",0);result.addProperty("pinned_snapshot_reload",true);
        result.add("points",new Gson().toJsonTree(points.stream().map(p->List.of(p.getX(),p.getZ())).toList()));result.add("heights",new Gson().toJsonTree(heights));
        Files.writeString(Path.of("coast-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));helper.succeed();
    }
}
