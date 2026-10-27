package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;

final class ClimateSelectionTest {
    static void run(Registry<Biome> biomes)throws Exception {
        var json=JsonParser.parseString("{\"enabled\":true,\"generation_version\":5,\"temperature\":\"minecraft:mushroom\",\"entries\":[{\"x\":0,\"z\":0,\"radius\":500}]}").getAsJsonObject();
        var config=PresetDocument.parse(json).config();
        GenerationContextTest.check(config.equals(IslandConfigLoader.parse(IslandConfigLoader.toJson(config))),"Climate settings roundtrip");
        var ctx=new GenerationContext(2,config,5,biomes);var rules=ctx.biomeRules();var source=ctx.mask().sampleInfo(0,0).landSource();
        var plains=biomes.getHolderOrThrow(Biomes.PLAINS);
        GenerationContextTest.check(rules.landBiome(1,source,plains,0,0).is(Biomes.MUSHROOM_FIELDS),"Root category not inherited");
        GenerationContextTest.check(rules.landBiome(1,source,biomes.getHolderOrThrow(Biomes.RIVER),0,0).is(Biomes.RIVER),"Category replaced river");
        GenerationContextTest.check(rules.landBiome(1,source,biomes.getHolderOrThrow(Biomes.LUSH_CAVES),0,0).is(Biomes.LUSH_CAVES),"Category replaced cave");
        GenerationContextTest.check(rules.oceanBiome(.04,source,plains,0,0).is(Biomes.DEEP_OCEAN),"Category changed ocean policy");
        json.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("temperature","natural");
        var natural=new GenerationContext(2,IslandConfigLoader.parse(json),5,biomes);
        GenerationContextTest.check(natural.biomeRules().landBiome(1,natural.mask().sampleInfo(0,0).landSource(),plains,0,0).equals(plains),"Natural did not override root");
        json.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("temperature","cold");
        var cold=new GenerationContext(2,IslandConfigLoader.parse(json),5,biomes);
        GenerationContextTest.check(new BiomeClimateCatalog(biomes).matches(cold.biomeRules().landBiome(1,cold.mask().sampleInfo(0,0).landSource(),plains,0,0),IslandTemperature.COLD),"Island climate did not override root");
        json.addProperty("temperature","biomesoplenty:mushroom");
        try{new GenerationContext(2,IslandConfigLoader.parse(json),5,biomes);throw new AssertionError("Missing mod category accepted");}catch(IllegalArgumentException expected){}
        json.addProperty("temperature","temperate");json.addProperty("generation_version",4);
        try{PresetDocument.parse(json);throw new AssertionError("Legacy generation accepted root climate");}catch(IslandConfigException expected){}
        for(String invalid:new String[]{"unknown:group","frozen","", "42"}){json.addProperty("temperature",invalid);try{IslandConfigLoader.parse(json);throw new AssertionError("Invalid climate accepted");}catch(IslandConfigException expected){}}
        System.out.println("Generation 5 climate inheritance, categories, protected biomes and validation passed");
    }
}
