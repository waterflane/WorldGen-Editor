package org.wodichka.worldgen_editor.verification;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.wodichka.worldgen_editor.world.*;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("worldgen_editor")
@PrefixGameTestTemplate(false)
public final class GenerationGameTests {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
    private static JsonObject post(String path,JsonObject value) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8765/api/"+path))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(value.toString())).build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new AssertionError(response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    @GameTest(template="empty",timeoutTicks=1200)
    public static void previewMatchesWorld(GameTestHelper helper) throws Exception {
        if(Boolean.getBoolean("wge.terrablenderVerification")) {
            Class.forName("org.wodichka.worldgen_editor.verification.TerraBlenderVerification")
                .getMethod("verify",GameTestHelper.class).invoke(null,helper);
            return;
        }
        var level=helper.getLevel();var context=IslandWorldState.context();
        helper.assertTrue(context!=null && context.generationVersion()==2,"WGE test world must be bound before generation");
        var request=new JsonObject();request.addProperty("seed",Long.toString(level.getSeed()));
        request.add("config",IslandConfigLoader.toJson(context.config()));request.addProperty("generation_version",2);
        String session=post("session",request).get("session").getAsString();
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();
        int compared=0;
        for(int x:new int[]{-2400,-1700,-640,0,640,1500,2400}) for(int z:new int[]{-640,0,720}) {
            int surface=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState())-1;
            for(int y:new int[]{surface,300,64,0,-32}) {
                var query=new JsonObject();query.addProperty("session",session);query.addProperty("request",100000);
                query.addProperty("x",x);query.addProperty("z",z);query.addProperty("size",1);query.addProperty("step",1);
                query.addProperty("height_mode","slice");query.addProperty("y",y);
                var tile=post("tile",query);String expected=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();
                String actual=level.getBiome(new BlockPos(x,y,z)).unwrapKey().orElseThrow().location().toString();
                helper.assertTrue(expected.equals(actual),"Biome mismatch at "+x+","+y+","+z+": "+actual+" != "+expected);compared++;
            }
            var query=new JsonObject();query.addProperty("session",session);query.addProperty("request",100000);
            query.addProperty("x",x);query.addProperty("z",z);query.addProperty("size",1);query.addProperty("step",1);query.addProperty("height_mode","surface");
            var tile=post("tile",query);
            helper.assertTrue(tile.getAsJsonArray("heights").get(0).getAsInt()==surface,"Base height mismatch");
        }
        Files.writeString(Path.of("parity-result.json"),"{\"biome_samples\":"+compared+",\"height_samples\":21,\"mismatches\":0}");
        helper.succeed();
    }
}
