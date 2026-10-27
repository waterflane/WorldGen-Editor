package org.wodichka.worldgen_editor.verification;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/** Independent, fully loaded NeoForge world versus the long-lived profile runtime. */
public final class ModdedPreviewVerification {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
    private static JsonObject post(String path,JsonObject body)throws Exception {
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(System.getProperty("wge.moddedPreviewUrl")+"/api/"+path))
            .timeout(java.time.Duration.ofSeconds(90)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new AssertionError(response.body());return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static void verify(GameTestHelper helper)throws Exception {
        var level=helper.getLevel();var context=IslandWorldState.context();helper.assertTrue(context!=null&&context.generationVersion()==5,"Expected generation 5 native world");
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        String profile="supported-"+System.getProperty("wge.compatProfile");var request=new JsonObject();
        request.addProperty("profile",profile);request.addProperty("seed",Long.toString(level.getSeed()));request.addProperty("generation_version",5);request.addProperty("coast_revision",3);
        request.add("config",IslandConfigLoader.toJson(context.config()));var session=post("session",request);String id=session.get("session").getAsString();
        int biomes=0,heights=0,finished=0;var namespaces=new TreeSet<String>();
        for(int x:new int[]{-545,-257,-1,0,99,330,513,8000})for(int z:new int[]{-419,-168,-20,0,255}){
            int surface=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random)-1;
            for(int y:new int[]{surface,300,0,-32}){
                var q=new JsonObject();q.addProperty("profile",profile);q.addProperty("session",id);q.addProperty("request",100000);q.addProperty("x",x);q.addProperty("z",z);
                q.addProperty("size",1);q.addProperty("step",1);q.addProperty("height_mode","slice");q.addProperty("y",y);
                var tile=post("tile",q);String preview=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();
                var actual=level.getBiome(new BlockPos(x,y,z)).unwrapKey().orElseThrow().location();namespaces.add(actual.getNamespace());
                helper.assertTrue(actual.toString().equals(preview),"Biome mismatch at "+x+","+y+","+z+": "+actual+" != "+preview);biomes++;
                if(y==surface){q.addProperty("height_mode","surface");var top=post("tile",q);
                    helper.assertTrue(top.getAsJsonArray("heights").get(0).getAsInt()==surface,"Base height mismatch at "+x+","+z+": "+surface+" != "+top.get("heights"));heights++;
                    var expected=top.getAsJsonArray("palette").get(top.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();helper.assertTrue(expected.equals(actual.toString()),"Surface biome mismatch");
                }
            }
            if(Math.abs(x)<400&&Math.abs(z)<200){level.getChunk(x>>4,z>>4);finished++;}
        }
        // A new seed must not mutate the original session or require reloading the mods.
        var other=request.deepCopy();other.addProperty("seed",Long.toString(level.getSeed()^0x1234abcdL));var second=post("session",other);
        helper.assertTrue(!id.equals(second.get("session").getAsString()),"Seed change reused session");
        helper.assertTrue(session.get("runtime_instance").equals(second.get("runtime_instance")),"Seed change restarted NeoForge");
        var again=post("session",request);helper.assertTrue(id.equals(again.get("session").getAsString()),"Original context was lost");
        for(int y:new int[]{-32,0,300}){
            var q=new JsonObject();q.addProperty("profile",profile);q.addProperty("session",id);q.addProperty("request",100001);q.addProperty("x",-257);q.addProperty("z",255);q.addProperty("size",1);q.addProperty("step",1);q.addProperty("height_mode","slice");q.addProperty("y",y);
            var tile=post("tile",q);String expected=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();
            helper.assertTrue(expected.equals(level.getBiome(new BlockPos(-257,y,255)).unwrapKey().orElseThrow().location().toString()),"Seed change mutated the original biome source");
        }
        int categorySamples=0;var catalog=new BiomeClimateCatalog(context.biomes());
        for(var choice:IslandTemperature.values()){
            if(choice==IslandTemperature.STANDARD||catalog.candidates(choice).isEmpty())continue;
            for(boolean perIsland:new boolean[]{false,true}){
                var changed=IslandConfigLoader.toJson(context.config());
                if(perIsland){changed.addProperty("temperature","cold");for(var entry:changed.getAsJsonArray("entries"))entry.getAsJsonObject().addProperty("temperature",choice.id());}
                else changed.addProperty("temperature",choice.id());
                var nativeContext=new GenerationContext(level.getSeed(),IslandConfigLoader.parse(changed),5,context.biomes());var binding=new GenerationBinding();binding.set(nativeContext);
                var nativeSource=IslandBiomeSource.forPreset(((IslandBiomeSource)generator.getBiomeSource()).delegate(),"comparison",5,level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME));nativeSource.bind(binding);
                var manager=new net.minecraft.world.level.biome.BiomeManager((x,y,z)->nativeSource.getNoiseBiome(x,y,z,random.sampler()),net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(level.getSeed()));
                var modified=request.deepCopy();modified.add("config",changed);String selected=post("session",modified).get("session").getAsString();
                for(int x:new int[]{-257,0,99,330})for(int z:new int[]{-168,0})for(int y:new int[]{300,0}){
                    var query=new JsonObject();query.addProperty("profile",profile);query.addProperty("session",selected);query.addProperty("request",100000);query.addProperty("x",x);query.addProperty("z",z);query.addProperty("size",1);query.addProperty("step",1);query.addProperty("height_mode","slice");query.addProperty("y",y);
                    var tile=post("tile",query);String expected=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();String actual=manager.getBiome(new BlockPos(x,y,z)).unwrapKey().orElseThrow().location().toString();
                    helper.assertTrue(expected.equals(actual),"Category context mismatch "+choice.id()+" at "+x+","+y+","+z+": "+actual+" != "+expected);categorySamples++;
                    helper.assertTrue(nativeContext.mask().sample(x,z)==context.mask().sample(x,z),"Climate selection changed island geometry");
                }
            }
        }
        var snapshotPath=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("worldgen_editor/worldgen_editor.json");
        var snapshot=WorldSnapshotStore.loadOrCreate(snapshotPath,"default",5,name->{throw new AssertionError("Pinned config read global defaults");});
        helper.assertTrue(snapshot.config().equals(context.config()),"Pinned climate settings changed");
        level.getServer().saveEverything(false,true,true);
        var result=new JsonObject();result.addProperty("profile",profile);result.addProperty("resources",session.get("resources").getAsString());result.addProperty("seed",Long.toString(level.getSeed()));
        result.addProperty("generation_version",5);result.addProperty("world_preset","minecraft:normal");result.addProperty("biome_samples",biomes);result.addProperty("base_height_samples",heights);result.addProperty("category_context_samples",categorySamples);result.addProperty("finished_chunks",finished);result.addProperty("mismatches",0);
        result.addProperty("multiple_sessions_without_restart",true);result.addProperty("pinned_snapshot",true);result.add("biome_namespaces",new Gson().toJsonTree(namespaces));
        result.addProperty("reopened_world",Boolean.getBoolean("wge.moddedReopen"));
        Files.writeString(Path.of("modded-preview-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));helper.succeed();
    }
}
