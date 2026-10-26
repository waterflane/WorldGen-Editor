package org.wodichka.worldgen_editor.verification;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.LevelResource;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;
import java.nio.file.*;
import java.util.*;

/** Actual generated seed-2 chunks, HTTP preview and reload from the world's pinned snapshot. */
final class OceanVerification {
    static void verify(GameTestHelper helper)throws Exception {
        var level=helper.getLevel();var context=IslandWorldState.context();
        helper.assertTrue(level.getSeed()==2&&context!=null&&context.generationVersion()==3,"Expected isolated seed-2 v3 world");
        String climate=System.getProperty("wge.oceanClimate");
        helper.assertTrue(context.config().oceanTemperature()==(climate.equals("default")?null:IslandTemperature.valueOf(climate.toUpperCase(Locale.ROOT))),"World did not load requested ocean temperature");
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        var request=new JsonObject();request.addProperty("seed","2");request.addProperty("generation_version",3);request.add("config",IslandConfigLoader.toJson(context.config()));
        String session=GenerationGameTests.post("session",request).get("session").getAsString();
        var points=new ArrayList<BlockPos>();points.add(new BlockPos(-545,0,-419));points.add(new BlockPos(0,0,0));points.add(new BlockPos(16000,0,0));
        for(int z=-1024;z<=1024&&points.size()<19;z+=32)for(int x=-1024;x<=1024&&points.size()<19;x+=32) {
            var info=context.mask().sampleInfo(x,z);
            if(info.value()>.01&&info.value()<.29&&info.landSource()!=null)points.add(new BlockPos(x,0,z));
        }
        helper.assertTrue(points.size()>=10,"Painted fixture has no coastal control points");
        var noTemperature=IslandConfigLoader.toJson(context.config());noTemperature.remove("ocean_temperature");
        if("1".equals(System.getProperty("wge.outlineCase")))noTemperature=JsonParser.parseString(Files.readString(Path.of(System.getProperty("wge.outlineFixtures")))).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("config");
        var baseline=new GenerationContext(2,IslandConfigLoader.parse(noTemperature),3,context.biomes());
        var baselineBinding=new GenerationBinding();baselineBinding.set(baseline);
        var originalBinding=((IslandBiomeSource)generator.getBiomeSource()).binding();
        var baselineGenerator=(NoiseBasedChunkGenerator)net.minecraft.world.level.chunk.ChunkGenerator.CODEC.parse(RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess()),
                net.minecraft.world.level.chunk.ChunkGenerator.CODEC.encodeStart(RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess()),generator).getOrThrow()).getOrThrow();
        ((IslandBiomeSource)baselineGenerator.getBiomeSource()).bind(baselineBinding);
        baselineGenerator.generatorSettings().value().noiseRouter().mapAll(function->{
            if(function instanceof IslandContinentsFunction islands)islands.bind(baselineBinding);
            if(function instanceof IslandCoastFunction coast)coast.bind(baselineBinding);
            if(function instanceof IslandDepthFunction depth)depth.bind(baselineBinding);return function;
        });
        var baselineNoise=RandomState.create(baselineGenerator.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),2);
        int biomeChecks=0,heightChecks=0,normalOceanChecks=0;
        var heights=new ArrayList<Integer>();var biomeIds=new ArrayList<String>();
        for(var p:points) {
            int x=p.getX(),z=p.getZ(),y=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random)-1;
            helper.assertTrue(y==baselineGenerator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,baselineNoise)-1,"Ocean temperature changed base terrain");
            var tile=GenerationGameTests.post("tile",GenerationGameTests.query(session,x,0,z,"surface"));
            helper.assertTrue(tile.getAsJsonArray("heights").get(0).getAsInt()==y,"Base height differs from preview");heightChecks++;
            GenerationGameTests.compareFastSurface(helper,session,x,y,z);
            for(int sampleY:new int[]{y,300,0,-32}) { biomeIds.add(GenerationGameTests.compareBiome(helper,session,x,sampleY,z));biomeChecks++; }
            heights.add(y);
            if(climate.equals("default")||climate.equals("temperate")) {
                int qx=Math.floorDiv(x,4),qz=Math.floorDiv(z,4);var info=context.mask().sampleInfo(qx*4,qz*4);
                var climateSource=info.oceanSource()!=null?info.oceanSource():info.archipelagoSource()!=null?info.archipelagoSource():info.landSource();
                if(info.value()<IslandTerrainHooks.FULL_OCEAN_MASK && (!climate.equals("default")||climateSource==null||climateSource.temperature()==IslandTemperature.STANDARD||climateSource.temperature()==IslandTemperature.TEMPERATE)) {
                    var biome=generator.getBiomeSource().getNoiseBiome(qx,75,qz,random.sampler());
                    helper.assertTrue(biome.is(Biomes.OCEAN)||biome.is(Biomes.DEEP_OCEAN),"Unconfigured coast selected a temperature variant");normalOceanChecks++;
                }
            }
        }
        helper.assertTrue(((IslandBiomeSource)generator.getBiomeSource()).binding()==originalBinding,"Baseline checks changed live binding");
        level.getServer().saveEverything(false,true,true);
        Path world=level.getServer().getWorldPath(LevelResource.ROOT);
        var snapshot=WorldSnapshotStore.loadOrCreate(world.resolve("worldgen_editor/worldgen_editor.json"),"default",3,name->{throw new AssertionError("Reload read global config");});
        helper.assertTrue(snapshot.config().equals(context.config()),"Pinned ocean configuration changed on reload");
        var saved=net.minecraft.nbt.NbtIo.readCompressed(world.resolve("level.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var restored=(NoiseBasedChunkGenerator)net.minecraft.world.level.chunk.ChunkGenerator.CODEC.parse(RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,level.registryAccess()),
            saved.getCompound("Data").getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld").getCompound("generator")).getOrThrow();
        var reloadBinding=new GenerationBinding();reloadBinding.set(new GenerationContext(2,snapshot.config(),3,context.biomes()));((IslandBiomeSource)restored.getBiomeSource()).bind(reloadBinding);
        restored.generatorSettings().value().noiseRouter().mapAll(function->{
            if(function instanceof IslandContinentsFunction islands)islands.bind(reloadBinding);
            if(function instanceof IslandCoastFunction coast)coast.bind(reloadBinding);
            if(function instanceof IslandDepthFunction depth)depth.bind(reloadBinding);return function;
        });
        var reloadNoise=RandomState.create(restored.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),2);
        var biomeManager=new net.minecraft.world.level.biome.BiomeManager((qx,qy,qz)->restored.getBiomeSource().getNoiseBiome(qx,qy,qz,reloadNoise.sampler()),net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(2));
        int index=0;
        for(int i=0;i<points.size();i++) {var p=points.get(i);int y=heights.get(i);
            helper.assertTrue(restored.getBaseHeight(p.getX(),p.getZ(),Heightmap.Types.WORLD_SURFACE_WG,level,reloadNoise)-1==y,"Saved terrain changed after reload");
            for(int sampleY:new int[]{y,300,0,-32})helper.assertTrue(biomeManager.getBiome(new BlockPos(p.getX(),sampleY,p.getZ())).unwrapKey().orElseThrow().location().toString().equals(biomeIds.get(index++)),"Saved biomes changed after reload");
        }
        var result=new JsonObject();result.addProperty("seed",2);result.addProperty("ocean_temperature",climate);result.addProperty("biome_samples",biomeChecks);result.addProperty("base_height_samples",heightChecks);
        if(System.getProperty("wge.outlineCase")!=null){result.addProperty("outline_case",System.getProperty("wge.outlineCase"));result.addProperty("outline_objects",context.config().entries().stream().filter(entry->entry.outline()!=null).count());}
        result.addProperty("normal_ocean_samples",normalOceanChecks);result.addProperty("mismatches",0);result.addProperty("pinned_snapshot_reload",true);result.addProperty("base_heights_unchanged",true);
        result.add("points",new Gson().toJsonTree(points.stream().map(p->List.of(p.getX(),p.getZ())).toList()));result.add("biomes",new Gson().toJsonTree(biomeIds));result.add("heights",new Gson().toJsonTree(heights));
        Files.writeString(Path.of("ocean-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));helper.succeed();
    }
}
