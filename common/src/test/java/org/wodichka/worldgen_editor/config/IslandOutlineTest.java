package org.wodichka.worldgen_editor.config;

import com.google.gson.*;
import org.wodichka.worldgen_editor.world.IslandMask;
import java.nio.charset.StandardCharsets;

public final class IslandOutlineTest {
    public static void run()throws Exception {
        try(var stream=IslandOutlineTest.class.getResourceAsStream("/island-outlines.json")) {
            var cases=JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();int count=0;
            for(var item:cases){var row=item.getAsJsonObject();var config=IslandConfigLoader.parse(row.get("config"));var mask=new IslandMask(config,Long.parseLong(row.get("seed").getAsString()));
                for(var value:row.getAsJsonArray("points")){var p=value.getAsJsonArray();double result=mask.sample(p.get(0).getAsDouble(),p.get(1).getAsDouble());
                    if(Math.abs(result-p.get(2).getAsDouble())>1e-12)throw new AssertionError("Outline Java/JS mismatch: "+row.get("name")+" "+p+" != "+result);count++;}
                if(!IslandConfigLoader.parse(IslandConfigLoader.toJson(config)).equals(config))throw new AssertionError("Outline serialization lost data");
                if(config.hasOutlines()){
                    var json=IslandConfigLoader.toJson(config);json.remove("painted_map");
                    if(PresetDocument.parse(json).generationVersion()!=3)throw new AssertionError("Outline preset did not select version 3");
                    json.addProperty("generation_version",2);
                    try{PresetDocument.parse(json);throw new AssertionError("Old version accepted an outline");}catch(IslandConfigException expected){}
                    for(String invalid:new String[]{"null","{}","{\"radius_x\":1,\"radius_z\":1}","{\"radius_x\":\"1\",\"radius_z\":1}"}){
                        json=IslandConfigLoader.toJson(config);json.getAsJsonArray("entries").get(1).getAsJsonObject().add("outline",JsonParser.parseString(invalid));
                        try{IslandConfigLoader.parse(json);throw new AssertionError("Invalid outline accepted");}catch(IslandConfigException expected){}
                    }
                }
            }
            System.out.println("Island outline Java/JS parity and serialization passed: "+count+" samples");
        }
    }
}
