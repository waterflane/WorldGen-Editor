package org.wodichka.worldgen_editor.preview;
import com.google.gson.*;
import net.minecraft.world.level.biome.Biomes;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;

public final class GenerationContextTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var registries=PreviewWorld.bootstrap();var biomes=registries.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        OceanTemperatureTest.run(biomes);
        OutlineContextTest.run(biomes);
        // Regression: an inherited cold delegate must not freeze an unconfigured coastline.
        for(int version:new int[]{1,2,3}) {
            var normalConfig=IslandConfigLoader.parse(JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":500}]}"));
            var normal=new GenerationContext(2,normalConfig,version,biomes);
            var normalSource=normal.mask().sampleInfo(0,0).landSource();
            for(var delegateKey:java.util.List.of(Biomes.FROZEN_OCEAN,Biomes.COLD_OCEAN,Biomes.SNOWY_PLAINS,Biomes.WARM_OCEAN)) {
                var delegate=biomes.getHolderOrThrow(delegateKey);
                check(normal.biomeRules().oceanBiome(.2,normalSource,delegate,-545,-419).is(Biomes.OCEAN),"unconfigured coastline inherited temperature in v"+version+" from "+delegateKey.location());
                check(normal.biomeRules().oceanBiome(.04,normalSource,delegate,-545,-419).is(Biomes.DEEP_OCEAN),"unconfigured deep ocean inherited temperature in v"+version);
            }
        }
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
        outerInput.addProperty("outer_ocean","minecraft:soul_sand_valley");
        owner.set(new GenerationContext(0,IslandConfigLoader.parse(outerInput),2,biomes));
        check(outerSource.getNoiseBiome(5000,75,5000,outerWorld.random().sampler()).is(Biomes.DEEP_OCEAN),"invalid outer ocean must fall back to a registered ocean");
        check(outerSource.possibleBiomes().contains(biomes.getHolderOrThrow(Biomes.DEEP_OCEAN)),"fallback ocean must remain possible after a reload");
        owner.set(null);
        check(before.equals(other.biomeId(256,300,0)),"clearing one binding must leave other contexts intact");
        var accessor=net.minecraft.world.level.LevelHeightAccessor.create(-64,384);
        for(int x=-8;x<8;x++)for(int z=-8;z<8;z++){
            int expected=first.generator().getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,accessor,first.random())-1;
            check(first.surfaceY(x,z)==expected,"batched surface sampler differs from Minecraft at "+x+","+z);
        }
        var parallelWorld=new PreviewWorld(input,0,2);
        for(int i=0;i<64;i++){
            int x=-383+(i%8)*127,z=-381+(i/8)*125;
            int expected=first.generator().getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,accessor,first.random())-1;
            check(parallelWorld.surfaceY(x,z,32)==expected,"sparse surface sampler differs at nonaligned column "+x+","+z);
        }
        var expectedHeights=new int[64];var expectedBiomes=new String[64];
        for(int i=0;i<64;i++){
            int x=-384+(i%8)*128,z=-384+(i/8)*128;
            expectedHeights[i]=first.generator().getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,accessor,first.random())-1;
            expectedBiomes[i]=first.biomeId(x,expectedHeights[i],z);
        }
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(6)){
            var jobs=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<256;i++){final int index=i%64;jobs.add(pool.submit(()->{
                int x=-384+(index%8)*128,z=-384+(index/8)*128,y=parallelWorld.surfaceY(x,z,32);
                check(y==expectedHeights[index],"parallel height sampling changed the result");
                check(parallelWorld.biomeId(x,y,z).equals(expectedBiomes[index]),"parallel biome cache changed the result");
            }));}
            for(var job:jobs)job.get();
        }
        var disabled=input.deepCopy();disabled.addProperty("enabled",false);var vanilla=new PreviewWorld(disabled,0,2);
        int surfaceChecks=0;
        for(String preset:new String[]{"default","archipelago","small_island"}){
            JsonElement layout;
            try(var stream=GenerationContextTest.class.getResourceAsStream("/assets/worldgen_editor/presets/"+preset+".json")){
                layout=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
            }
            for(long seed:new long[]{0,12345,Long.MIN_VALUE}){
                var candidate=new PreviewWorld(layout,seed,2);
                for(int i=0;i<64;i++){
                    int x=-1791+(i%8)*511,z=-1789+(i/8)*509;
                    int expected=candidate.generator().getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,accessor,candidate.random())-1;
                    check(candidate.surfaceY(x,z,32)==expected,"upper-band surface differs for "+preset+" seed "+seed+" at "+x+","+z);
                    String independent=candidate.heightIndependentBiomeId(x,z);
                    if(independent!=null)for(int y:new int[]{-64,-33,0,48,63,64,96,128,192,255,300,319})
                        check(independent.equals(candidate.biomeId(x,y,z)),"height-independent biome differs for "+preset+" at "+x+","+y+","+z);
                    surfaceChecks++;
                }
            }
        }
        check(vanilla.context().mask().isEmpty(),"disabled generation must delegate to vanilla");
        check(vanilla.heightIndependentBiomeId(9000,9000)==null,"disabled generation must not skip vanilla heights");
        for(int version:new int[]{1,2})for(String temperature:new String[]{"standard","cold","warm","temperate"}){
            var layout=JsonParser.parseString("{\"outer_ocean\":\"minecraft:warm_ocean\",\"entries\":[{\"type\":\"ocean\",\"x\":0,\"z\":0,\"radius\":400,\"temperature\":\""+temperature+"\"}]}");
            var oceanWorld=new PreviewWorld(layout,Long.MIN_VALUE,version);
            for(int x:new int[]{-513,-399,-3,0,2,3,399,513})for(int z:new int[]{-2,0,3,513}){
                String independent=oceanWorld.heightIndependentBiomeId(x,z);
                if(independent!=null)for(int y=-64;y<=319;y++)
                    check(independent.equals(oceanWorld.biomeId(x,y,z)),"constant ocean or Voronoi proof failed at "+x+","+y+","+z);
            }
        }
        var empty=input.deepCopy();empty.add("entries",new JsonArray());check(new PreviewWorld(empty,0,2).context().mask().isEmpty(),"empty config must delegate");
        var painted=empty.deepCopy();painted.add("painted_map",JsonParser.parseString("{\"version\":1,\"step\":4,\"size\":64,\"tiles\":[]}"));
        var custom=new PreviewWorld(painted,0,3);
        check(!custom.context().mask().isEmpty(),"empty painted canvas must be an ocean");
        check(custom.generator().generatorSettings().value().noiseRouter().finalDensity() instanceof IslandCoastFunction,"v3 must compose actual terrain density");
        for(int x:new int[]{-257,-1,0,255,1024}){
            int expected=custom.generator().getBaseHeight(x,0,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,accessor,custom.random())-1;
            check(custom.surfaceY(x,0,32)==expected,"v3 compiled sampler differs from Minecraft");
            check(custom.biomeId(x,300,0).equals("minecraft:deep_ocean"),"empty painted canvas selected land");
        }
        painted.addProperty("enabled",false);
        var disabledV3=new PreviewWorld(painted,0,3);
        check(disabledV3.context().mask().isEmpty(),"disabling v3 must disable painted mask");
        for(int x:new int[]{-257,-1,0,255,1024})check(disabledV3.surfaceY(x,0,32)==vanilla.surfaceY(x,0,32),"disabled v3 changed vanilla terrain");
        try{new PreviewWorld(painted,0,2);throw new AssertionError("old generation accepted a painted map");}catch(IllegalArgumentException expected){}
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
                // The approved ocean fix applies to old versions too. Keep historical fixtures intact:
                // only unconfigured oceans intentionally stop inheriting the delegate's climate.
                String expectedOcean=row.get("temperature").getAsString().equals("standard")
                        ? mask<IslandTerrainHooks.DEEP_OCEAN_MASK?"minecraft:deep_ocean":"minecraft:ocean"
                        : row.get("ocean").getAsString();
                check(sea.unwrapKey().orElseThrow().location().toString().equals(expectedOcean),"explicit legacy ocean biome or corrected default changed");
            }
        }
        // Simulate the server facade with an isolated state file; a live graph has cached noise cells.
        var activeBinding=new GenerationBinding();activeBinding.set(fixed);
        var stateFile=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."),"staged-world-").resolve("worldgen_editor.json");
        String[] fields={"binding","preset","statePath","storedConfig","worldEnabled","version"};
        Object[] values={activeBinding,"default",stateFile,config,true,2};
        try{
            for(int i=0;i<fields.length;i++){var field=IslandWorldState.class.getDeclaredField(fields[i]);field.setAccessible(true);field.set(null,values[i]);}
            check(IslandWorldState.setEnabled(false),"valid generation change was not saved");
            check(activeBinding.get()==fixed,"live generation graph changed while noise cells were cached; apply saved changes only on restart");
            check(!WorldSnapshotStore.loadOrCreate(stateFile,"default",2,name->config).enabled(),"restart configuration was not persisted");
        }finally{IslandWorldState.clear();}
        System.out.println("Generation context checks, 576 legacy biome cases and "+surfaceChecks+" multi-preset surface columns passed");
    }
}
