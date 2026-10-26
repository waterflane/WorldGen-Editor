package org.wodichka.worldgen_editor.config;
import com.google.gson.JsonParser;
import org.wodichka.worldgen_editor.world.*;
import java.nio.file.*;

public final class WorldSnapshotStoreTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception {
        Path root=Path.of("build","snapshot-tests").toAbsolutePath();Files.createDirectories(root);
        Path dir=Files.createTempDirectory(root,"world-");
        var config=IslandConfigLoader.parse(JsonParser.parseString("{\"enabled\":true,\"entries\":[{\"x\":0,\"z\":0,\"radius\":100}]}"));
        Path state=dir.resolve("worldgen_editor.json");
        Files.writeString(state,"{\"enabled\":false}");
        var legacy=WorldSnapshotStore.loadOrCreate(state,"default",2,name->config);
        check(!legacy.enabled()&&legacy.generationVersion()==1,"old flag/version must survive");
        var pinned=WorldSnapshotStore.loadOrCreate(state,"other",2,name->{throw new AssertionError("pinned world must not reload global presets");});
        check(legacy.equals(pinned),"snapshot changed after restart");
        var migrated=new WorldSnapshotStore.Snapshot(true,"small_island",2,config);
        WorldSnapshotStore.save(state,migrated);
        check(WorldSnapshotStore.loadOrCreate(state,"default",1,name->config).equals(migrated),"migration must persist");
        var painted=IslandConfigLoader.parse(JsonParser.parseString("{\"enabled\":true,\"entries\":[],\"painted_map\":{\"version\":1,\"step\":4,\"size\":64,\"tiles\":[]}}"));
        String previous=Files.readString(state);
        try{new WorldSnapshotStore.Snapshot(true,"custom_map",2,painted);throw new AssertionError("old snapshot accepted a painted map");}catch(IllegalArgumentException expected){}
        check(Files.readString(state).equals(previous),"invalid painted snapshot changed saved state");
        var custom=new WorldSnapshotStore.Snapshot(true,"custom_map",3,painted);WorldSnapshotStore.save(state,custom);
        check(WorldSnapshotStore.loadOrCreate(state,"default",1,name->config).equals(custom),"painted world snapshot changed on restart");
        var temperatureJson=IslandConfigLoader.toJson(painted);temperatureJson.addProperty("ocean_temperature","warm");
        var temperatureSnapshot=new WorldSnapshotStore.Snapshot(true,"custom_map",3,IslandConfigLoader.parse(temperatureJson));
        WorldSnapshotStore.save(state,temperatureSnapshot);
        check(WorldSnapshotStore.loadOrCreate(state,"default",1,name->{throw new AssertionError("Reload read the global preset");}).equals(temperatureSnapshot),"snapshot lost ocean temperature on restart");
        var invalidTemperature=JsonParser.parseString(Files.readString(state)).getAsJsonObject();invalidTemperature.getAsJsonObject("config").addProperty("ocean_temperature","standard");
        Files.writeString(state,invalidTemperature.toString());
        try{WorldSnapshotStore.loadOrCreate(state,"default",1,name->config);throw new AssertionError("invalid ocean setting accepted");}
        catch(IslandConfigException expected){check(Files.readString(state).equals(invalidTemperature.toString()),"failed ocean load rewrote snapshot");}
        for(String invalid:new String[]{"{", "{\"enabled\":\"false\"}","{\"generation_version\":1.5}","{\"generation_version\":5}","{\"generation_version\":\"2\"}","{\"preset\":\"../escape\"}"}){
            Files.writeString(state,invalid);
            try{WorldSnapshotStore.loadOrCreate(state,"default",1,name->config);throw new AssertionError("invalid snapshot accepted");}
            catch(IslandConfigException expected){check(Files.readString(state).equals(invalid),"failed load rewrote the snapshot");}
        }
        System.out.println("World snapshot persistence and failure checks passed");
    }
}
