package org.wodichka.worldgen_editor.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** File envelopes are decoded before the generation config. Editor state never reaches worldgen. */
public record PresetDocument(IslandConfig config, int generationVersion) {
    public static final long MAX_BYTES=32L*1024*1024;

    public static JsonObject readObject(Path path)throws IOException,IslandConfigException {
        if(Files.isSymbolicLink(path))throw new IslandConfigException("Preset links are not supported: "+path.getFileName());
        if(Files.size(path)>MAX_BYTES)throw new IslandConfigException("Preset exceeds 32 MiB: "+path.getFileName());
        try(var reader=Files.newBufferedReader(path)){
            var value=JsonParser.parseReader(reader);
            if(!value.isJsonObject())throw new IslandConfigException("Preset root must be an object");
            return value.getAsJsonObject();
        }catch(JsonParseException|IllegalStateException error){throw new IslandConfigException("Invalid JSON in "+path.getFileName()+": "+error.getMessage(),error);}
    }

    public static PresetDocument read(Path path)throws IOException,IslandConfigException { return parse(readObject(path)); }

    public static PresetDocument parse(JsonObject root)throws IslandConfigException {
        boolean project=root.has("format");
        if(project){
            if(!root.get("format").isJsonPrimitive()||!root.getAsJsonPrimitive("format").isString()
                    ||!root.get("format").getAsString().equals("wge-map-project"))throw new IslandConfigException("Unsupported map project format");
            if(integer(root,"version",-1)!=1)throw new IslandConfigException("Unsupported map project version");
        }
        JsonObject body=root;
        if(project||root.has("config")){
            if(!root.has("config")||!root.get("config").isJsonObject())throw new IslandConfigException("Project config must be an object");
            body=root.getAsJsonObject("config");
        }
        boolean outlines=body.has("entries")&&body.get("entries").isJsonArray()&&java.util.stream.StreamSupport.stream(body.getAsJsonArray("entries").spliterator(),false).anyMatch(entry->entry.isJsonObject()&&entry.getAsJsonObject().has("outline"));
        boolean coasts=body.has("entries")&&body.get("entries").isJsonArray()&&java.util.stream.StreamSupport.stream(body.getAsJsonArray("entries").spliterator(),false).anyMatch(entry->entry.isJsonObject()&&entry.getAsJsonObject().has("coast"));
        int version=integer(project?body:root,"generation_version",project?(coasts?4:3):root.has("config")?1:coasts?4:body.has("painted_map")||outlines?3:2);
        if(version<1||version>5)throw new IslandConfigException("Unsupported generation_version: "+version);
        if(body.has("painted_map")&&version<3)throw new IslandConfigException("Painted maps require generation_version 3. Select this map from the refreshed world-type list.");
        if(coasts&&version<4)throw new IslandConfigException("Custom coasts require generation_version 4");
        if(outlines&&version<3)throw new IslandConfigException("Island outlines require generation_version 3");
        JsonObject config=body.deepCopy();
        if(root.has("name")&&body!=root)config.add("name",root.get("name"));
        if(!config.has("enabled"))config.addProperty("enabled",true);
        if(!project&&body!=root&&root.has("enabled")){
            if(!root.get("enabled").isJsonPrimitive()||!root.getAsJsonPrimitive("enabled").isBoolean())throw new IslandConfigException("World enabled must be boolean");
            if(!root.get("enabled").getAsBoolean())config.addProperty("enabled",false);
        }
        var parsed=IslandConfigLoader.parse(config);
        if(parsed.hasModernClimate()&&version<5)throw new IslandConfigException("Extended temperatures require generation_version 5");
        return new PresetDocument(parsed,version);
    }

    private static int integer(JsonObject root,String key,int fallback)throws IslandConfigException {
        if(!root.has(key))return fallback;
        var value=root.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IslandConfigException(key+" must be an integer");
        double number=value.getAsDouble();
        if(!Double.isFinite(number)||number!=Math.rint(number)||number<Integer.MIN_VALUE||number>Integer.MAX_VALUE)throw new IslandConfigException(key+" must be an integer");
        return (int)number;
    }
}
