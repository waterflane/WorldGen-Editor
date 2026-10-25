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
    static JsonObject post(String path,JsonObject value) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8765/api/"+path))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(value.toString())).build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new AssertionError(response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static JsonObject query(String session,int x,int y,int z,String mode) {
        var query=new JsonObject();query.addProperty("session",session);query.addProperty("request",100000);
        query.addProperty("x",x);query.addProperty("z",z);query.addProperty("size",1);query.addProperty("step",1);
        query.addProperty("height_mode",mode);query.addProperty("y",y);return query;
    }
    static String compareBiome(GameTestHelper helper,String session,int x,int y,int z)throws Exception {
        var tile=post("tile",query(session,x,y,z,"slice"));
        String expected=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();
        String actual=helper.getLevel().getBiome(new BlockPos(x,y,z)).unwrapKey().orElseThrow().location().toString();
        helper.assertTrue(expected.equals(actual),"Biome mismatch at "+x+","+y+","+z+": "+actual+" != "+expected);
        return actual;
    }
    static void compareFastSurface(GameTestHelper helper,String session,int x,int surface,int z)throws Exception {
        var request=query(session,x,0,z,"surface");request.addProperty("include_heights",false);
        var tile=post("tile",request);
        String expected=tile.getAsJsonArray("palette").get(tile.getAsJsonArray("biomes").get(0).getAsInt()).getAsString();
        String actual=helper.getLevel().getBiome(new BlockPos(x,surface,z)).unwrapKey().orElseThrow().location().toString();
        helper.assertTrue(expected.equals(actual),"Fast surface biome mismatch at "+x+","+surface+","+z);
        int height=tile.getAsJsonArray("heights").get(0).getAsInt();
        helper.assertTrue(height==Short.MIN_VALUE || height==surface,"Fast surface height must be exact or explicitly absent");
    }
    @GameTest(template="empty",timeoutTicks=1200)
    public static void previewMatchesWorld(GameTestHelper helper) throws Exception {
        if(Boolean.getBoolean("wge.oceanVerification")) { OceanVerification.verify(helper);return; }
        if(Boolean.getBoolean("wge.compatibilityVerification")){
            Class.forName("org.wodichka.worldgen_editor.verification.CompatibilityVerification")
                .getMethod("verify",GameTestHelper.class).invoke(null,helper);
            return;
        }
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
        int compared=0,heightChecks=0;
        for(int x:new int[]{-2400,-1700,-640,0,640,1500,2400}) for(int z:new int[]{-640,0,720}) {
            int surface=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState())-1;
            for(int y:new int[]{surface,300,64,0,-32}) {
                compareBiome(helper,session,x,y,z);compared++;
            }
            var tile=post("tile",query(session,x,0,z,"surface"));
            helper.assertTrue(tile.getAsJsonArray("heights").get(0).getAsInt()==surface,"Base height mismatch");
            compareFastSurface(helper,session,x,surface,z);
            heightChecks++;
        }
        // Fixed seed-zero oracle locations ensure these categories are actually present in the test.
        String river=compareBiome(helper,session,832,300,-960);compared++;
        helper.assertTrue(river.equals("minecraft:river"),"River control point no longer contains a river");
        String cave=compareBiome(helper,session,128,0,-704);compared++;
        helper.assertTrue(cave.equals("minecraft:lush_caves"),"Underground control point no longer contains lush caves");
        double edge=context.mask().sample(-1267,0);
        helper.assertTrue(edge>.49 && edge<.51,"Shore control point must lie on the island transition");
        for(int x:new int[]{-1271,-1267,-1263}) {
            int surface=generator.getBaseHeight(x,0,Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState())-1;
            var tile=post("tile",query(session,x,0,0,"surface"));
            helper.assertTrue(tile.getAsJsonArray("heights").get(0).getAsInt()==surface,"Shore base height mismatch");heightChecks++;
            compareFastSurface(helper,session,x,surface,0);
            compareBiome(helper,session,x,surface,0);compareBiome(helper,session,x,300,0);compared+=2;
        }
        var result=new JsonObject();result.addProperty("biome_samples",compared);result.addProperty("height_samples",heightChecks);
        result.addProperty("mismatches",0);result.addProperty("river",river);result.addProperty("underground",cave);result.addProperty("shore_mask",edge);
        result.addProperty("fast_surface_samples",heightChecks);
        Files.writeString(Path.of("parity-result.json"),result.toString());
        helper.succeed();
    }
}
