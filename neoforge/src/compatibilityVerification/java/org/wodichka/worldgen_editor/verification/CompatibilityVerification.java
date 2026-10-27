package org.wodichka.worldgen_editor.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.*;
import org.wodichka.worldgen_editor.world.*;
import org.wodichka.worldgen_editor.config.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

public final class CompatibilityVerification {
    private static JsonObject post(String path,JsonObject input)throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8765/api/"+path))
            .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(input.toString())).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new AssertionError(response.body());return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static void verify(GameTestHelper helper)throws Exception {
        if(System.getProperty("wge.moddedPreviewUrl")!=null){ModdedPreviewVerification.verify(helper);return;}
        var level=helper.getLevel();var context=IslandWorldState.context();
        helper.assertTrue(context!=null&&context.generationVersion()==3,"Expected version 3 world");
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();
        helper.assertTrue(generator.generatorSettings().value().noiseRouter().finalDensity() instanceof IslandCoastFunction,"No WGE coast composition in actual generator");
        String profile=System.getProperty("wge.compatProfile");boolean disabled=profile.equals("disabled"),vanilla=profile.equals("vanilla")||profile.equals("painted")||disabled;
        if(disabled)helper.assertTrue(!context.config().enabled(),"Version 3 preset enabled:false was overridden by global enabled:true");
        var source=(IslandBiomeSource)generator.getBiomeSource();var random=level.getChunkSource().randomState();
        var ops=net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        var encoded=net.minecraft.world.level.chunk.ChunkGenerator.CODEC.encodeStart(ops,generator).getOrThrow();
        helper.assertTrue(encoded.getAsJsonObject().get("settings").getAsString().equals("minecraft:overworld"),"Expanded runtime graph leaked into world save");
        var decoded=(NoiseBasedChunkGenerator)net.minecraft.world.level.chunk.ChunkGenerator.CODEC.parse(ops,encoded).getOrThrow();
        helper.assertTrue(decoded.generatorSettings().value().noiseRouter().finalDensity() instanceof IslandCoastFunction,"Reloaded generator lost WGE composition");
        var noiseNamespaces=new TreeSet<String>();
        random.router().mapAll(new DensityFunction.Visitor(){
            public DensityFunction apply(DensityFunction function){return function;}
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise){
                noise.noiseData().unwrapKey().ifPresent(key->noiseNamespaces.add(key.location().getNamespace()));return noise;
            }
        });
        boolean tectonic=profile.contains("tectonic")||profile.equals("terratonic")||profile.equals("combined");
        if(tectonic)helper.assertTrue(noiseNamespaces.contains("tectonic"),"Tectonic is loaded but its noises are absent from the actual terrain graph");
        String surface=SurfaceRules.RuleSource.CODEC.encodeStart(ops,generator.generatorSettings().value().surfaceRule()).getOrThrow().toString();
        if(profile.contains("bop")||profile.equals("combined"))helper.assertTrue(surface.contains("biomesoplenty"),"Biomes O' Plenty surface rules were lost");
        if(profile.contains("terralith")||profile.equals("terratonic")||profile.equals("combined"))helper.assertTrue(surface.contains("terralith"),"Terralith surface rules were lost");
        var namespaces=new HashMap<String,BlockPos>();int scanCount=0;
        for(int x=-5500;x<=5500;x+=256)for(int z=-5500;z<=5500;z+=256){
            if(context.mask().sample(x,z)<.60)continue;
            var biome=source.getNoiseBiome(Math.floorDiv(x,4),20,Math.floorDiv(z,4),random.sampler());
            var id=biome.unwrapKey().orElseThrow().location();namespaces.putIfAbsent(id.getNamespace(),new BlockPos(x,80,z));scanCount++;
        }
        if(profile.contains("bop")||profile.equals("combined"))helper.assertTrue(namespaces.containsKey("biomesoplenty"),"No Biomes O' Plenty biome reached WGE land");
        if(profile.contains("terralith")||profile.equals("terratonic")||profile.equals("combined"))helper.assertTrue(namespaces.containsKey("terralith"),"No Terralith biome reached WGE land");
        var surfaceBlocks=new TreeSet<String>();
        for(var entry:namespaces.entrySet())if(!entry.getKey().equals("minecraft")){
            var p=entry.getValue();level.getChunk(p.getX()>>4,p.getZ()>>4);
            helper.assertTrue(level.getBiome(p).unwrapKey().orElseThrow().location().getNamespace().equals(entry.getKey()),"Mod biome missing from actual chunk");
            for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
                int x=(p.getX()>>4)*16+dx,z=(p.getZ()>>4)*16+dz;
                int y=level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-1;
                surfaceBlocks.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x,y,z)).getBlock()).toString());
            }
        }
        int underground=0;
        for(int x=-2048;x<=2048;x+=128)for(int z=-2048;z<=2048;z+=128)for(int y:new int[]{-32,0,32}){
            if(context.mask().sample(x,z)<.72)continue;
            var original=source.delegate().getNoiseBiome(x/4,y/4,z/4,random.sampler());
            var id=original.unwrapKey().orElseThrow().location().getPath();
            if(id.contains("cave")||id.equals("deep_dark")){
                helper.assertTrue(source.getNoiseBiome(x/4,y/4,z/4,random.sampler()).equals(original),"Underground biome was replaced by surface climate");underground++;
            }
        }
        if(!profile.equals("painted")&&!disabled)helper.assertTrue(underground>0,"No underground biome control samples were found");
        int solidLandColumns=0,caveAirBlocks=0;
        if(!disabled){
            for(int x=-512;x<=512;x+=128)for(int z=-512;z<=512;z+=128){
                if(context.mask().sample(x,z)<.9)continue;
                if(generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random)>generator.getSeaLevel())solidLandColumns++;
            }
            helper.assertTrue(solidLandColumns>0,"WGE land mask did not produce solid land above sea level");
            var chunk=level.getChunk(0,0);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-48;y<-4;y++)
                if(chunk.getBlockState(new BlockPos(x,y,z)).isAir())caveAirBlocks++;
            helper.assertTrue(caveAirBlocks>0,"Generated underground chunk contains no cave air");
        }
        if(!disabled)for(int x:new int[]{-16000,16000}){
            int y=generator.getBaseHeight(x,0,Heightmap.Types.WORLD_SURFACE_WG,level,random)-1;
            helper.assertTrue(context.mask().sample(x,0)<.08,"Ocean control point is not outside islands");
            helper.assertTrue(y<generator.getSeaLevel(),"External terrain escaped the WGE ocean envelope at "+x);
            helper.assertTrue(level.getBiome(new BlockPos(x,80,0)).is(net.minecraft.tags.BiomeTags.IS_OCEAN),"Actual ocean chunk selected a land biome");
            helper.assertTrue(generator.getBaseHeight(x,0,Heightmap.Types.OCEAN_FLOOR_WG,level,random)<generator.getSeaLevel()-5,"Base ocean floor is too high");
            helper.assertTrue(level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,0)<generator.getSeaLevel()-5,"Actual ocean floor escaped the WGE envelope");
        }
        int parity=0;
        if(vanilla){
            var request=new JsonObject();request.addProperty("seed",Long.toString(level.getSeed()));request.addProperty("generation_version",3);
            request.add("config",IslandConfigLoader.toJson(context.config()));String session=post("session",request).get("session").getAsString();
            for(int x:new int[]{-260,-1,0,128,255,513,8000})for(int z:new int[]{-257,0,255}){
                int y=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random)-1;
                for(int sampleY:new int[]{y,300,0,-32}){
                    var q=new JsonObject();q.addProperty("session",session);q.addProperty("request",100000);q.addProperty("x",x);q.addProperty("z",z);q.addProperty("size",1);q.addProperty("step",1);q.addProperty("height_mode","slice");q.addProperty("y",sampleY);
                    var tile=post("tile",q);String expected=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();
                    helper.assertTrue(level.getBiome(new BlockPos(x,sampleY,z)).unwrapKey().orElseThrow().location().toString().equals(expected),"v3 preview biome mismatch");parity++;
                    if(sampleY==y){q.addProperty("height_mode","surface");helper.assertTrue(post("tile",q).getAsJsonArray("heights").get(0).getAsInt()==y,"v3 preview base height mismatch");}
                }
            }
        }
        // Read the real compressed level.dat, not just an in-memory codec roundtrip.
        level.getServer().saveEverything(false,true,true);
        var worldRoot=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        var saved=net.minecraft.nbt.NbtIo.readCompressed(worldRoot.resolve("level.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var savedGenerator=saved.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld").getCompound("generator");
        var restored=(NoiseBasedChunkGenerator)net.minecraft.world.level.chunk.ChunkGenerator.CODEC.parse(
            net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,level.registryAccess()),savedGenerator).getOrThrow();
        var snapshot=WorldSnapshotStore.loadOrCreate(worldRoot.resolve("worldgen_editor/worldgen_editor.json"),"default",3,name->{throw new AssertionError("Reload read global defaults");});
        var reloadedContext=new GenerationContext(level.getSeed(),snapshot.config().withEnabled(snapshot.enabled()&&snapshot.config().enabled()),snapshot.generationVersion(),level.registryAccess().registryOrThrow(Registries.BIOME));
        var binding=new GenerationBinding();binding.set(reloadedContext);((IslandBiomeSource)restored.getBiomeSource()).bind(binding);
        restored.generatorSettings().value().noiseRouter().mapAll(function->{
            if(function instanceof IslandContinentsFunction islands)islands.bind(binding);
            if(function instanceof IslandCoastFunction coast)coast.bind(binding);return function;
        });
        var reloadedNoise=RandomState.create(restored.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),level.getSeed());
        for(int x:new int[]{-257,0,16000})helper.assertTrue(
            restored.getBaseHeight(x,0,Heightmap.Types.WORLD_SURFACE_WG,level,reloadedNoise)==generator.getBaseHeight(x,0,Heightmap.Types.WORLD_SURFACE_WG,level,random),"Terrain changed after reading level.dat and pinned snapshot");
        var result=new JsonObject();result.addProperty("profile",profile);result.addProperty("generation_version",3);result.addProperty("climate_samples",scanCount);
        result.add("biome_namespaces",new Gson().toJsonTree(namespaces.keySet()));result.addProperty("ocean_columns",disabled?0:2);result.addProperty("preview_biome_samples",parity);result.addProperty("mismatches",0);
        result.addProperty("generator_codec_roundtrip",true);
        result.addProperty("saved_settings_reload",true);result.addProperty("underground_samples",underground);result.add("noise_namespaces",new Gson().toJsonTree(noiseNamespaces));
        result.addProperty("surface_rules_preserved",true);
        result.addProperty("solid_land_columns",solidLandColumns);result.addProperty("cave_air_blocks",caveAirBlocks);
        result.add("actual_surface_blocks",new Gson().toJsonTree(surfaceBlocks));
        Files.writeString(Path.of("compatibility-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));helper.succeed();
    }
}
