package org.wodichka.worldgen_editor.preview;

import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.*;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;
import org.wodichka.worldgen_editor.world.*;

final class CoastTransitionTest {
    static void run(Registry<Biome> biomes)throws Exception {
        var config=IslandConfigLoader.parse(JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":1000,\"roughness\":0,\"coast\":{\"beach_width_blocks\":16,\"shallow_ocean_width_blocks\":128}}]}"));
        var context=new GenerationContext(2,config,4,biomes);var binding=new GenerationBinding();binding.set(context);
        // A smooth natural hill must not become a cliff when the coastal cap ends.
        var function=new IslandCoastFunction(DensityFunctions.yClampedGradient(-64,320,1,-1),63);function.bind(binding);
        var plain=IslandConfigLoader.toJson(config);plain.getAsJsonArray("entries").get(0).getAsJsonObject().remove("coast");
        var rawMask=new IslandMask(IslandConfigLoader.parse(plain),2);
        var original=DensityFunctions.yClampedGradient(-64,320,1,-1);
        var source=context.mask().sampleInfo(1000,0).coast().source();
        var low=CoastTerrain.envelope(new CoastField.Sample(-128,source,-.5),63);
        var high=CoastTerrain.envelope(new CoastField.Sample(-128,source,.5),63);
        GenerationContextTest.check(high.ceiling()-low.ceiling()>=4,"The shelf edge must retain natural height variation");
        for(double variation:new double[]{-.5,0,.5}){
            var left=CoastTerrain.envelope(new CoastField.Sample(-128+1e-6,source,variation),63);
            var right=CoastTerrain.envelope(new CoastField.Sample(-128-1e-6,source,variation),63);
            GenerationContextTest.check(Math.abs(left.floor()-right.floor())<1e-5&&Math.abs(left.ceiling()-right.ceiling())<1e-5,"Seabed bounds jump at the shelf edge");
        }
        var rows=new com.google.gson.JsonArray();
        int previous=0,previousOld=0,maxOldStep=0,maxStep=0,maxX=0;
        for(int x=600;x<=1450;x++){
            int height=-64;for(int y=319;y>=-64;y--)if(function.compute(new DensityFunction.SinglePointContext(x,y,0))>0){height=y;break;}
            var sample=context.mask().sampleInfo(x,0);int oldHeight=-64;
            for(int y=319;y>=-64;y--)if(oldDensity(original.compute(new DensityFunction.SinglePointContext(x,y,0)),y,rawMask.sample(x,0),sample.coast())>0){oldHeight=y;break;}
            var row=new com.google.gson.JsonArray();row.add(x);row.add(oldHeight);row.add(height);rows.add(row);
            if(x>600)maxOldStep=Math.max(maxOldStep,Math.abs(oldHeight-previousOld));previousOld=oldHeight;
            if(x>600&&Math.abs(height-previous)>maxStep){maxStep=Math.abs(height-previous);maxX=x;}previous=height;
        }
        GenerationContextTest.check(maxStep<=4,"Custom coast introduced a "+maxStep+" block cliff at x="+maxX+" in an otherwise smooth hill");
        GenerationContextTest.check(maxOldStep>=40,"The pre-fix reference must reproduce the coastal wall");
        var report=new com.google.gson.JsonObject();report.addProperty("old_max_step",maxOldStep);report.addProperty("new_max_step",maxStep);report.add("profile",rows);
        java.nio.file.Files.writeString(java.nio.file.Path.of("coast-transition-profile.json"),report.toString());
        System.out.println("Coast transition synthetic height profile: maximum adjacent step "+maxStep);
    }
    /** Regression oracle for the previously released v4 fixed-height layer. */
    private static double oldDensity(double original,int y,double raw,CoastField.Sample sample){
        double value=raw,d=Double.POSITIVE_INFINITY,b=16,s=128;
        if(sample!=null){d=sample.distance();if(d>=0){value=d<=b?.54+.04*d/b:.58+(Math.max(.58,raw)-.58)*smooth((d-b)/64);}
            else{double depth=-d;value=depth<=s?.54-.46*depth/s:Math.min(raw,.079*Math.max(0,1-(depth-s)/64));}}
        if(value>=.72)return original;
        double ceiling=(63+(value-.54)*80-y)/8,weight=Math.max(0,Math.min(1,(.72-value)/.14));
        double shaped=original+(Math.min(original,ceiling)-original)*weight;
        if(sample!=null){double band=Math.max(0,Math.min(1,(d>=0?b+64-d:s+64+d)/64));double layer=Math.max(0,Math.min(1,(16-ceiling*8)/8));shaped+=(ceiling-shaped)*layer*band;}
        return shaped;
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
