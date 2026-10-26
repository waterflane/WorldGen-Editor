package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.*;
import java.nio.file.*;

final class CoastTransitionTest {
    static void run(Registry<Biome> biomes)throws Exception {
        int profiles=0;
        for(int width:new int[]{0,16,128,4096}){
            var input=JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":1000,\"roughness\":0,\"coast\":{\"beach_width_blocks\":"+width+",\"shallow_ocean_width_blocks\":"+width+"}}]}");
            var context=new GenerationContext(3,IslandConfigLoader.parse(input),4,biomes);var binding=new GenerationBinding();binding.set(context);
            var cave=new IslandCoastFunction(DensityFunctions.constant(-.25),63);cave.bind(binding);
            var depth=new IslandDepthFunction(DensityFunctions.add(DensityFunctions.yClampedGradient(-64,320,1.5,-1.5),DensityFunctions.constant(-.5)),DensityFunctions.constant(.4),63);depth.bind(binding);
            double previous=Double.NaN;int n=0;
            for(int x=600;x<=1450;x++){
                var point=new DensityFunction.SinglePointContext(x,63,0);double d=depth.compute(point);
                GenerationContextTest.check(cave.compute(point)==-.25,"Custom coast filled a cave/river after final density");
                if(n++>0)GenerationContextTest.check(Math.abs(d-previous)*128<4,"Natural coast introduced a hard height step at "+x+" width="+width);
                GenerationContextTest.check(Double.isFinite(d),"Nonfinite pre-cave depth");previous=d;
                GenerationContextTest.check(d<=0.0078125,"Custom coast raised a valley into a parallel ridge");
            }
            var key=new DensityFunction.SinglePointContext(990,63,0);double original=depth.compute(key);
            binding.set(new GenerationContext(3,IslandConfig.EMPTY,4,biomes));
            GenerationContextTest.check(depth.compute(key)==0.0078125,"Reload retained a cached coast correction");
            binding.set(context);GenerationContextTest.check(depth.compute(key)==original,"Restoring a context changed the coast field");profiles++;
        }
        var fixture=JsonParser.parseString(Files.readString(Path.of("../../common/src/test/resources/natural-coast-repro.json"))).getAsJsonObject();
        var world=new PreviewWorld(fixture.get("config"),Long.parseLong(fixture.get("seed").getAsString()),4);
        int river=solid(world,96,-24),land=solid(world,328,-168);
        System.out.println("Reported river climate="+world.random().sampler().sample(24,15,-6)+" depth="+world.random().router().depth().compute(new DensityFunction.SinglePointContext(96,63,-24)));
        GenerationContextTest.check(river<62,"Reported river is still filled to the sea-level land floor: "+river);
        GenerationContextTest.check(land>=63,"Reported jungle land is still flooded by coast shaping: "+land);
        var sample=world.context().mask().sampleInfo(328,-168);
        var rules=world.context().biomeRules();var beach=biomes.getHolderOrThrow(Biomes.BEACH);
        var replacement=rules.naturalCoastBiome(sample.coast(),beach,328,-168,sample.landSource(),80,63);
        GenerationContextTest.check(replacement!=null&&!rules.isBeach(replacement),"Original beach leaked outside the natural beach zone");
        for(var biome:java.util.List.of(Biomes.RIVER,Biomes.LUSH_CAVES))
            GenerationContextTest.check(rules.naturalCoastBiome(sample.coast(),biomes.getHolderOrThrow(biome),328,-168,sample.landSource(),64,63)==null,"Natural coast replaced a river or cave biome");
        int[] depthNodes={0};world.random().router().mapAll(f->{if(f instanceof IslandDepthFunction)depthNodes[0]++;return f;});
        GenerationContextTest.check(depthNodes[0]>=3,"Pre-cave depth is missing from the effective noise graph");
        for(long seed:new long[]{0,2,3,Long.MIN_VALUE}){
            var holes=JsonParser.parseString("{\"enabled\":true,\"generation_version\":4,\"entries\":[{\"x\":0,\"z\":0,\"radius\":160,\"roughness\":0,\"coast\":{\"beach_width_blocks\":16,\"shallow_ocean_width_blocks\":128}},{\"type\":\"ocean\",\"x\":0,\"z\":0,\"radius\":12,\"roughness\":0}]}").getAsJsonObject();
            var holeWorld=new PreviewWorld(holes,seed,4);
            GenerationContextTest.check(solid(holeWorld,0,0)<63,"Custom coast closed a small internal water body for seed "+seed);
            var cut=holes.getAsJsonArray("entries").get(1).getAsJsonObject();cut.remove("radius");cut.addProperty("radius_x",300);cut.addProperty("radius_z",12);
            var strait=new PreviewWorld(holes,seed,4);
            for(int x:new int[]{-80,0,80})GenerationContextTest.check(solid(strait,x,0)<63,"Custom coast closed an ocean channel for seed "+seed+" x="+x);
        }
        System.out.println("Natural coast: "+profiles+" continuous profiles, unfilled caves, cache reload, reported river="+river+" jungle="+land+", depth nodes="+depthNodes[0]);
    }
    static int solid(PreviewWorld world,int x,int z){
        var column=world.generator().getBaseColumn(x,z,net.minecraft.world.level.LevelHeightAccessor.create(-64,384),world.random());
        for(int y=319;y>=-64;y--){var state=column.getBlock(y);if(!state.isAir()&&state.getFluidState().isEmpty())return y;}
        return -64;
    }
}
