package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;
import java.nio.charset.StandardCharsets;

final class OutlineContextTest {
    static void run(Registry<Biome> biomes)throws Exception {
        JsonArray cases;
        try(var stream=OutlineContextTest.class.getResourceAsStream("/island-outlines.json")) {
            // Fixtures are part of the common test source set, not production resources.
            if(stream!=null)cases=JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();
            else cases=JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("../../common/src/test/resources/island-outlines.json"))).getAsJsonArray();
        }
        var original=cases.get(0).getAsJsonObject().getAsJsonObject("config");var converted=cases.get(1).getAsJsonObject().getAsJsonObject("config");
        var before=new PreviewWorld(original,2,3);var after=new PreviewWorld(converted,2,3);int columns=0;
        for(int x:new int[]{-420,-337,-257,-180,-96})for(int z:new int[]{-420,-337,-257,-180,-96}){
            int y=before.surfaceY(x,z);GenerationContextTest.check(y==after.surfaceY(x,z),"Conversion changed base height");
            for(int sampleY:new int[]{y,300,0,-32})GenerationContextTest.check(before.biomeId(x,sampleY,z).equals(after.biomeId(x,sampleY,z)),"Conversion changed default biomes");columns++;
        }
        for(String climate:new String[]{"cold","warm","temperate"}){
            var config=converted.deepCopy();config.getAsJsonArray("entries").get(1).getAsJsonObject().addProperty("temperature",climate);
            var context=new GenerationContext(2,IslandConfigLoader.parse(config),3,biomes);var source=context.mask().sampleInfo(-337,-257).landSource();
            GenerationContextTest.check(source.name().startsWith("Painted Island"),"Outline lost ownership of its climate");
            GenerationContextTest.check(source.temperature()==IslandTemperature.valueOf(climate.toUpperCase(java.util.Locale.ROOT)),"Outline temperature was not applied");
            var cave=biomes.getHolderOrThrow(Biomes.LUSH_CAVES);
            GenerationContextTest.check(context.biomeRules().landBiome(.9,source,cave,-337,-257).equals(cave),"Outline climate changed cave biome");
            GenerationContextTest.check(context.biomeRules().landBiome(.9,source,biomes.getHolderOrThrow(Biomes.RIVER),-337,-257).is(climate.equals("cold")?Biomes.FROZEN_RIVER:Biomes.RIVER),"Outline river temperature differs from ordinary islands");
        }
        var config=IslandConfigLoader.parse(converted);
        try{new WorldSnapshotStore.Snapshot(true,"outline",2,config);throw new AssertionError("Old snapshot accepted an outline");}catch(IllegalArgumentException expected){}
        var path=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."),"outline-reload-").resolve("worldgen_editor.json");
        var saved=new WorldSnapshotStore.Snapshot(true,"outline",3,config);WorldSnapshotStore.save(path,saved);
        GenerationContextTest.check(WorldSnapshotStore.loadOrCreate(path,"default",3,name->{throw new AssertionError("Reload read global presets");}).equals(saved),"Outline snapshot changed after restart");
        System.out.println("Outline conversion preserved "+columns+" base heights and "+columns*4+" block biomes; climate and pinned reload passed");
    }
}
