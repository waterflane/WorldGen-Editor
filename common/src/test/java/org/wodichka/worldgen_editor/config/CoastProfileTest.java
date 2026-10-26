package org.wodichka.worldgen_editor.config;

import com.google.gson.*;
import org.wodichka.worldgen_editor.world.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class CoastProfileTest {
    public static void main(String[] args)throws Exception {
        int samples=0;
        try(var stream=CoastProfileTest.class.getResourceAsStream("/coast-profiles.json")){
            var cases=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();
            for(var item:cases){var test=item.getAsJsonObject();var config=IslandConfigLoader.parse(test.get("config"));
                if(!config.equals(IslandConfigLoader.parse(IslandConfigLoader.toJson(config))))throw new AssertionError("Coast roundtrip changed config");
                var mask=new IslandMask(config,2);
                for(var row:test.getAsJsonArray("points")){var p=row.getAsJsonArray();double actual=mask.sample(p.get(0).getAsDouble(),p.get(1).getAsDouble()),expected=p.get(2).getAsDouble();
                    if(!Double.isFinite(actual)||Math.abs(actual-expected)>1e-12)throw new AssertionError(test.get("name")+" mask mismatch "+p+" actual="+actual);samples++;}
                // Warm caches and a different query order must never change ownership or distances.
                for(int i=test.getAsJsonArray("points").size()-1;i>=0;i--){var p=test.getAsJsonArray("points").get(i).getAsJsonArray();if(Math.abs(mask.sample(p.get(0).getAsDouble(),p.get(1).getAsDouble())-p.get(2).getAsDouble())>1e-12)throw new AssertionError("Query-order dependent coast result");}
                for(int version:new int[]{1,2,3})try{new WorldSnapshotStore.Snapshot(true,"test",version,config);throw new AssertionError("Old snapshot accepted a custom coast");}catch(IllegalArgumentException expected){}
                if(PresetDocument.parse(test.getAsJsonObject("config")).generationVersion()!=4)throw new AssertionError("Preset lost generation version");
            }
        }
        var cancelConfig=IslandConfigLoader.parse(JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":160,\"coast\":{\"beach_width_blocks\":16,\"shallow_ocean_width_blocks\":128}}]}"));
        var cancellable=new IslandMask(cancelConfig,2);CoastField.cancellationGuard(()->false);
        try{cancellable.sample(200,0);throw new AssertionError("Stale coast query was not cancelled");}catch(java.util.concurrent.CancellationException expected){}finally{CoastField.cancellationGuard(null);}
        if(!Double.isFinite(cancellable.sample(200,0)))throw new AssertionError("Cancelled query poisoned mask caches");
        for(String coast:new String[]{"null","{}","{\"beach_width_blocks\":-1,\"shallow_ocean_width_blocks\":128}","{\"beach_width_blocks\":1.5,\"shallow_ocean_width_blocks\":128}","{\"beach_width_blocks\":16,\"shallow_ocean_width_blocks\":4097}"}){
            var json=JsonParser.parseString("{\"entries\":[{\"x\":0,\"z\":0,\"radius\":160,\"coast\":"+coast+"}]}");
            try{IslandConfigLoader.parse(json);throw new AssertionError("Invalid coast accepted: "+coast);}catch(IslandConfigException expected){}
        }
        System.out.println("Coast profile Java/JS parity and version checks passed: "+samples+" samples");
    }
}
