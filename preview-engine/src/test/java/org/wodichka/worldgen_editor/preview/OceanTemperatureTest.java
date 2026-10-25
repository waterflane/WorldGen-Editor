package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;

/** Ocean policy is shared by the mod and preview, independent of delegate climate and Y. */
final class OceanTemperatureTest {
    static void run(Registry<Biome> biomes)throws Exception {
        int samples=0;
        for(int version:new int[]{1,2,3})for(String entry:new String[]{"standard","cold","warm","temperate"})
        for(String root:new String[]{"default","cold","warm","temperate"}) {
            var json=JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":500}]}").getAsJsonObject();
            json.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("temperature",entry);
            var baseline=new GenerationContext(2,IslandConfigLoader.parse(json),version,biomes);
            if(!root.equals("default"))json.addProperty("ocean_temperature",root);
            var context=new GenerationContext(2,IslandConfigLoader.parse(json),version,biomes);
            var source=context.mask().sampleInfo(0,0).landSource();
            String expected=root.equals("default")?entry:root;
            for(int x:new int[]{-545,-1,0,513})for(double mask:new double[]{.04,.2})for(var key:java.util.List.of(Biomes.FROZEN_OCEAN,Biomes.WARM_OCEAN,Biomes.PLAINS,Biomes.LUSH_CAVES,Biomes.RIVER)) {
                var delegate=biomes.getHolderOrThrow(key);
                var ocean=context.biomeRules().oceanBiome(mask,source,delegate,x,-419);
                boolean allowed=switch(expected) {
                    case "cold" -> ocean.is(Biomes.FROZEN_OCEAN)||ocean.is(Biomes.DEEP_FROZEN_OCEAN)||ocean.is(Biomes.COLD_OCEAN)||ocean.is(Biomes.DEEP_COLD_OCEAN);
                    case "warm" -> ocean.is(Biomes.WARM_OCEAN)||ocean.is(Biomes.LUKEWARM_OCEAN)||ocean.is(Biomes.DEEP_LUKEWARM_OCEAN);
                    default -> ocean.is(mask<IslandTerrainHooks.DEEP_OCEAN_MASK?Biomes.DEEP_OCEAN:Biomes.OCEAN);
                };
                GenerationContextTest.check(allowed,"Ocean precedence failed: "+version+"/"+root+"/"+entry);
                GenerationContextTest.check(context.biomeRules().landBiome(.9,source,delegate,x,-419).equals(baseline.biomeRules().landBiome(.9,source,delegate,x,-419)),"Ocean setting changed land, cave or river");
                GenerationContextTest.check(context.mask().sample(x,-419)==baseline.mask().sample(x,-419),"Ocean setting changed mask");samples++;
            }
            // A deliberately frozen external ocean remains authoritative for every root setting.
            json.addProperty("outer_ocean","minecraft:deep_frozen_ocean");
            var external=new GenerationContext(2,IslandConfigLoader.parse(json),version,biomes);
            GenerationContextTest.check(external.biomeRules().outerOceanBiome().is(Biomes.DEEP_FROZEN_OCEAN),"Ocean temperature overrode outer_ocean");
        }
        System.out.println("Ocean temperature precedence and unchanged land/mask checks passed: "+samples);
    }
}
