package org.wodichka.worldgen_editor.config;

import com.google.gson.*;
import org.wodichka.worldgen_editor.world.IslandMask;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class PaintedMapTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        try(var stream=PaintedMapTest.class.getResourceAsStream("/painted-mask.json")){
            var fixture=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var map=PaintedMap.parse(fixture.get("map"));
            for(var row:fixture.getAsJsonArray("points")){
                var p=row.getAsJsonArray();double expected=p.get(3).getAsDouble();
                check(Math.abs(map.apply(p.get(2).getAsDouble(),p.get(0).getAsDouble(),p.get(1).getAsDouble())-expected)<1e-12,"Java/JS painted mask mismatch: "+p);
            }
            var again=PaintedMap.parse(map.toJson());
            check(map.toJson().equals(again.toJson()),"painted map roundtrip changed data");
            var input=new JsonObject();input.addProperty("enabled",true);input.add("entries",new JsonArray());input.add("painted_map",map.toJson());
            var config=IslandConfigLoader.parse(input);
            check(IslandConfigLoader.toJson(config).get("painted_map").equals(map.toJson()),"config dropped painted map");
            check(!new IslandMask(config,0).isEmpty(),"paint-only map must enable generation");
            check(new IslandMask(config.withEnabled(false).withEnabled(true),0).sample(-1,-1)==map.apply(0,-1,-1),"mask lost paint after enable roundtrip");
            input.add("painted_map",JsonParser.parseString("{\"version\":1,\"step\":4,\"size\":64,\"tiles\":[]}"));
            check(!new IslandMask(IslandConfigLoader.parse(input),0).isEmpty(),"new empty canvas must mean ocean, not vanilla");
        }
        for(String invalid:new String[]{"null","{}","{\"version\":2}","{\"version\":1,\"step\":4,\"size\":64,\"tiles\":[{\"x\":0,\"z\":0,\"runs\":[4097,65535,65535]}]}"}){
            try{PaintedMap.parse(JsonParser.parseString(invalid));throw new AssertionError("invalid map accepted");}catch(IslandConfigException expected){}
        }
        System.out.println("Sparse painted map parity and validation passed");
    }
}
