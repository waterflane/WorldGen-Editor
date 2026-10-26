package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.Heightmap;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;
import java.nio.file.*;

final class CoastContextTest {
    static void run(Registry<Biome> biomes)throws Exception {
        var input=JsonParser.parseString("""
          {"enabled":true,"generation_version":4,"outer_ocean":"minecraft:deep_frozen_ocean",
           "entries":[{"x":0,"z":0,"radius":160,"roughness":0,"coast":{"beach_width_blocks":16,"shallow_ocean_width_blocks":128}}]}
          """).getAsJsonObject();
        var config=IslandConfigLoader.parse(input);var context=new GenerationContext(2,config,4,biomes);
        var shallow=context.mask().sampleInfo(200,0).coast();var beach=context.mask().sampleInfo(144,0).coast();
        GenerationContextTest.check(context.biomeRules().customCoastBiome(shallow,biomes.getHolderOrThrow(Biomes.FROZEN_OCEAN),200,0).is(Biomes.OCEAN),"Metric shelf inherited frozen delegate");
        GenerationContextTest.check(context.biomeRules().customCoastBiome(context.mask().sampleInfo(320,0).coast(),null,320,0).is(Biomes.DEEP_FROZEN_OCEAN),"Outer ocean lost its explicit biome");
        GenerationContextTest.check(context.biomeRules().customCoastBiome(beach,biomes.getHolderOrThrow(Biomes.PLAINS),144,0).is(Biomes.BEACH),"Beach did not expand inland");
        for(var key:java.util.List.of(Biomes.RIVER,Biomes.LUSH_CAVES))GenerationContextTest.check(context.biomeRules().customCoastBiome(beach,biomes.getHolderOrThrow(key),144,0)==null,"Beach replaced river/cave");
        var coldCut=new IslandMask.SourceInfo("Cold cutout",IslandEntryType.OCEAN,null,2,java.util.List.of(),IslandTemperature.COLD,512);
        var cutBiome=context.biomeRules().customCoastBiome(shallow,null,200,0,coldCut);
        GenerationContextTest.check(cutBiome.is(Biomes.FROZEN_OCEAN)||cutBiome.is(Biomes.COLD_OCEAN),"Explicit ocean cutout climate was ignored");
        for(String temperature:new String[]{"cold","warm","temperate"}){
            var explicit=input.deepCopy();explicit.addProperty("ocean_temperature",temperature);
            var c=new GenerationContext(2,IslandConfigLoader.parse(explicit),4,biomes);
            var chosen=c.biomeRules().customCoastBiome(c.mask().sampleInfo(200,0).coast(),null,200,0);
            GenerationContextTest.check(!chosen.is(Biomes.DEEP_OCEAN)&&!chosen.is(Biomes.DEEP_COLD_OCEAN)&&!chosen.is(Biomes.DEEP_FROZEN_OCEAN)&&!chosen.is(Biomes.DEEP_LUKEWARM_OCEAN),"Metric shelf picked a deep ocean");
        }
        var path=Files.createTempDirectory(Path.of("."),"coast-reload-").resolve("worldgen_editor.json");
        var saved=new WorldSnapshotStore.Snapshot(true,"coast",4,config);WorldSnapshotStore.save(path,saved);
        GenerationContextTest.check(WorldSnapshotStore.loadOrCreate(path,"default",3,name->{throw new AssertionError("Reload read global presets");}).equals(saved),"Coast snapshot changed on reopen");
        for(int version:new int[]{1,2,3})try{new GenerationContext(2,config,version,biomes);throw new AssertionError("Old world silently accepted a metric coast");}catch(IllegalArgumentException expected){}
        var world=new PreviewWorld(input,2,4);var accessor=net.minecraft.world.level.LevelHeightAccessor.create(-64,384);int checks=0;
        for(int x:new int[]{-320,-200,-160,-144,-120,0,120,144,160,200,280,320})for(int z:new int[]{0,64}){
            int expected=world.generator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,accessor,world.random())-1;
            GenerationContextTest.check(world.surfaceY(x,z,8)==expected,"Metric coast preview base height differs from generator");
            String fast=world.heightIndependentBiomeId(x,z);
            if(fast!=null)for(int y:new int[]{-32,0,expected,300})GenerationContextTest.check(fast.equals(world.biomeId(x,y,z)),"Coast fast biome differs from BiomeManager");checks++;
        }
        // WORLD_SURFACE includes water; inspect solid base blocks to verify an
        // actual shallow seabed instead of checking only biome colors or water height.
        for(int x:new int[]{144,152,200,240,280}){
            var column=world.generator().getBaseColumn(x,0,accessor,world.random());int floor=-64;
            for(int y=319;y>=-64;y--){var block=column.getBlock(y);if(!block.isAir()&&block.getFluidState().isEmpty()){floor=y;break;}}
            var sample=world.context().mask().sampleInfo(x,0);double target=world.generator().getSeaLevel()+(sample.value()-.54)*80;
            GenerationContextTest.check(floor>=Math.floor(target)-5&&floor<=Math.ceil(target)+3,"Coastal seabed/land did not follow the metric envelope at "+x+": "+floor+" target="+target);
        }
        var inherited=JsonParser.parseString(Files.readString(Path.of("../../common/src/test/resources/island-outlines.json"))).getAsJsonArray().get(1).getAsJsonObject().getAsJsonObject("config").deepCopy();
        inherited.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("temperature","warm");
        var priorClimate=new GenerationContext(2,IslandConfigLoader.parse(inherited),3,biomes).mask().sampleInfo(-337,-257).landSource();
        inherited.getAsJsonArray("entries").get(1).getAsJsonObject().add("coast",JsonParser.parseString("{\"beach_width_blocks\":16,\"shallow_ocean_width_blocks\":128}"));
        var metricClimate=new GenerationContext(2,IslandConfigLoader.parse(inherited),4,biomes).mask().sampleInfo(-337,-257).landSource();
        GenerationContextTest.check(priorClimate.equals(metricClimate),"Adding coast widths changed an outline's inherited surface climate");
        var noProfile=input.deepCopy();noProfile.getAsJsonArray("entries").get(0).getAsJsonObject().remove("coast");
        var old=new PreviewWorld(noProfile,2,3);var newer=new PreviewWorld(noProfile,2,4);
        for(int x:new int[]{0,144,160,200,320}){GenerationContextTest.check(old.surfaceY(x,0)==newer.surfaceY(x,0),"v4 without coast changed terrain");GenerationContextTest.check(old.biomeId(x,300,0).equals(newer.biomeId(x,300,0)),"v4 without coast changed biomes");}
        System.out.println("Coast profiles: climate, cave/river preservation, pinned reload and "+checks+" exact base columns passed");
    }
}

