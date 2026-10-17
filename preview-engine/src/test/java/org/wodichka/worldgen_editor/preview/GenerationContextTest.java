package org.wodichka.worldgen_editor.preview;
import com.google.gson.*;
import net.minecraft.world.level.biome.Biomes;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;

public final class GenerationContextTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var registries=PreviewWorld.bootstrap();var biomes=registries.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        JsonObject input=JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":500,\"temperature\":\"warm\"}]}").getAsJsonObject();
        var config=IslandConfigLoader.parse(input);var source=new IslandMask(config,42).sampleInfo(0,0).landSource();
        var fixed=new GenerationContext(42,config,2,biomes);var legacy=new GenerationContext(42,config,1,biomes);
        var ocean=biomes.getHolderOrThrow(Biomes.OCEAN);var cave=biomes.getHolderOrThrow(Biomes.LUSH_CAVES);
        check(legacy.biomeRules().landBiome(1,source,ocean,0,0).is(Biomes.PLAINS),"v1 ocean replacement changed");
        check(!fixed.biomeRules().landBiome(1,source,ocean,0,0).is(Biomes.PLAINS),"v2 warm island cannot select temperate plains");
        check(fixed.biomeRules().landBiome(1,source,cave,0,0).equals(cave),"cave biome must survive surface temperature");
        check(fixed.biomeRules().landBiome(1,source,biomes.getHolderOrThrow(Biomes.RIVER),0,0).is(Biomes.RIVER),"surface climate must preserve rivers");
        check(fixed.biomeRules().landBiome(1,source,biomes.getHolderOrThrow(Biomes.FROZEN_RIVER),0,0).is(Biomes.RIVER),"warm climate must thaw a vanilla river");
        var coldInput=input.deepCopy();coldInput.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("temperature","cold");
        var cold=new GenerationContext(42,IslandConfigLoader.parse(coldInput),2,biomes);
        check(cold.biomeRules().landBiome(1,cold.mask().sampleInfo(0,0).landSource(),biomes.getHolderOrThrow(Biomes.RIVER),0,0).is(Biomes.FROZEN_RIVER),"cold climate must freeze a vanilla river");
        var excludedInput=input.deepCopy();excludedInput.getAsJsonArray("entries").get(0).getAsJsonObject().add("exclude_biomes",JsonParser.parseString("[\"#minecraft:is_badlands\"]"));
        var excludedConfig=IslandConfigLoader.parse(excludedInput);var excluded=new GenerationContext(42,excludedConfig,2,biomes);
        var excludedSource=excluded.mask().sampleInfo(0,0).landSource();
        check(!excluded.biomeRules().landBiome(1,excludedSource,biomes.getHolderOrThrow(Biomes.BADLANDS),0,0).is(Biomes.BADLANDS),"biome tags must be loaded");
        excludedInput.getAsJsonArray("entries").get(0).getAsJsonObject().add("exclude_biomes",JsonParser.parseString("[\"minecraft:lush_caves\",\"minecraft:river\"]"));
        var explicitExclusions=new GenerationContext(42,IslandConfigLoader.parse(excludedInput),2,biomes);
        var explicitSource=explicitExclusions.mask().sampleInfo(0,0).landSource();
        check(!explicitExclusions.biomeRules().landBiome(1,explicitSource,cave,0,0).is(Biomes.LUSH_CAVES),"explicit cave exclusions must win over preservation");
        check(!explicitExclusions.biomeRules().landBiome(1,explicitSource,biomes.getHolderOrThrow(Biomes.RIVER),0,0).is(Biomes.RIVER),"explicit river exclusions must win over preservation");
        var other=new PreviewWorld(input,12345,2);String before=other.biomeId(256,300,0);
        var first=new PreviewWorld(input,0,2);
        for(int x=-800;x<=800;x+=80)first.biomeId(x,300,0);
        check(before.equals(other.biomeId(256,300,0)),"creating another world changed an existing context");
        var outerInput=input.deepCopy();outerInput.addProperty("outer_ocean","minecraft:warm_ocean");
        var outerWorld=new PreviewWorld(outerInput,0,2);
        var outerSource=(IslandBiomeSource)outerWorld.generator().getBiomeSource();
        var owner=new GenerationBinding();owner.set(outerWorld.context());outerSource.bind(owner);
        check(outerSource.possibleBiomes().contains(biomes.getHolderOrThrow(Biomes.WARM_OCEAN)),"outer ocean must be possible before its first sample");
        check(outerSource.getNoiseBiome(5000,75,5000,outerWorld.random().sampler()).is(Biomes.WARM_OCEAN),"configured outer ocean must be sampled");
        outerInput.addProperty("outer_ocean","minecraft:deep_frozen_ocean");
        owner.set(new GenerationContext(0,IslandConfigLoader.parse(outerInput),2,biomes));
        check(outerSource.possibleBiomes().contains(biomes.getHolderOrThrow(Biomes.DEEP_FROZEN_OCEAN)),"cached possible biomes must cover a new outer ocean");
        check(outerSource.getNoiseBiome(5000,75,5000,outerWorld.random().sampler()).is(Biomes.DEEP_FROZEN_OCEAN),"binding replacement must update biome rules");
        owner.set(null);
        check(before.equals(other.biomeId(256,300,0)),"clearing one binding must leave other contexts intact");
        var accessor=net.minecraft.world.level.LevelHeightAccessor.create(-64,384);
        for(int x=-8;x<8;x++)for(int z=-8;z<8;z++){
            int expected=first.generator().getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,accessor,first.random())-1;
            check(first.surfaceY(x,z)==expected,"batched surface sampler differs from Minecraft at "+x+","+z);
        }
        var disabled=input.deepCopy();disabled.addProperty("enabled",false);var vanilla=new PreviewWorld(disabled,0,2);
        check(vanilla.context().mask().isEmpty(),"disabled generation must delegate to vanilla");
        var empty=input.deepCopy();empty.add("entries",new JsonArray());check(new PreviewWorld(empty,0,2).context().mask().isEmpty(),"empty config must delegate");
        try(var inputStream=GenerationContextTest.class.getResourceAsStream("/legacy-biomes.json")) {
            var cases=JsonParser.parseReader(new java.io.InputStreamReader(inputStream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            for(var item:cases){
                var row=item.getAsJsonObject();var raw=new JsonObject();raw.addProperty("enabled",true);var entry=new JsonObject();
                entry.addProperty("name","oracle");entry.addProperty("x",0);entry.addProperty("z",0);entry.addProperty("radius",100);
                entry.addProperty("temperature",row.get("temperature").getAsString());var entries=new JsonArray();entries.add(entry);raw.add("entries",entries);
                long seed=Long.parseLong(row.get("seed").getAsString());var snapshot=new GenerationContext(seed,IslandConfigLoader.parse(raw),1,biomes);
                var info=snapshot.mask().sampleInfo(0,0).landSource();var delegate=biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,net.minecraft.resources.ResourceLocation.withDefaultNamespace(row.get("delegate").getAsString())));
                int x=row.get("x").getAsInt(),z=row.get("z").getAsInt();double mask=row.get("mask").getAsDouble();
                var land=snapshot.biomeRules().landBiome(mask,info,delegate,x,z);var sea=snapshot.biomeRules().oceanBiome(mask,info,delegate,x,z);
                check(land.unwrapKey().orElseThrow().location().toString().equals(row.get("land").getAsString()),"legacy land biome changed");
                check(sea.unwrapKey().orElseThrow().location().toString().equals(row.get("ocean").getAsString()),"legacy ocean biome changed");
            }
        }
        System.out.println("Generation context checks and 576 legacy biome cases passed");
    }
}
