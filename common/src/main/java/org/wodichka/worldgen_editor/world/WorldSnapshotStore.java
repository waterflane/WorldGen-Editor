package org.wodichka.worldgen_editor.world;

import com.google.gson.*;
import org.wodichka.worldgen_editor.config.*;
import java.io.IOException;
import java.nio.file.*;

/** Validated, atomically replaced per-world data. Loading a pinned snapshot never reads a different preset. */
public final class WorldSnapshotStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public record Snapshot(boolean enabled,String preset,int generationVersion,IslandConfig config) {
        public Snapshot {
            GenerationContext.validateVersion(generationVersion);
            if(preset==null||!preset.matches("[a-z0-9_.-]+"))throw new IllegalArgumentException("Invalid world preset name");
            if(preset==null||preset.isBlank()||config==null)throw new IllegalArgumentException("Incomplete world snapshot");
        }
    }
    public interface ConfigSupplier { IslandConfig get(String preset)throws IslandConfigException; }
    public static Snapshot loadOrCreate(Path path,String defaultPreset,int defaultVersion,ConfigSupplier defaults)throws IOException,IslandConfigException {
        try {
            boolean existing=Files.exists(path);
            JsonObject saved=existing?JsonParser.parseString(Files.readString(path)).getAsJsonObject():new JsonObject();
            boolean enabled=true;
            if(saved.has("enabled")) {
                if(!saved.get("enabled").isJsonPrimitive()||!saved.getAsJsonPrimitive("enabled").isBoolean())throw new IslandConfigException("World enabled must be boolean");
                enabled=saved.get("enabled").getAsBoolean();
            }
            String preset=saved.has("preset")?saved.get("preset").getAsString():defaultPreset;
            int version=existing ? GenerationContext.LEGACY_VERSION : defaultVersion;
            if(saved.has("generation_version")) {
                if(!saved.get("generation_version").isJsonPrimitive() || !saved.getAsJsonPrimitive("generation_version").isNumber())throw new IslandConfigException("generation_version must be a number");
                double number=saved.get("generation_version").getAsDouble();
                if(!Double.isFinite(number)||number!=Math.rint(number)||number<1||number>2)throw new IslandConfigException("Unsupported generation_version");
                version=(int)number;
            }
            IslandConfig config=saved.has("config")?IslandConfigLoader.parse(saved.get("config")):defaults.get(preset);
            Snapshot snapshot=new Snapshot(enabled,preset,version,config);
            if(!saved.has("config"))save(path,snapshot);
            return snapshot;
        } catch(JsonParseException|IllegalStateException|IllegalArgumentException error) {
            throw new IslandConfigException("Invalid world snapshot: "+error.getMessage(),error);
        }
    }
    public static void save(Path path,Snapshot snapshot)throws IOException {
        JsonObject value=new JsonObject();value.addProperty("enabled",snapshot.enabled());value.addProperty("preset",snapshot.preset());
        value.addProperty("generation_version",snapshot.generationVersion());value.add("config",IslandConfigLoader.toJson(snapshot.config()));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"snapshot-",".tmp");
        try {
            Files.writeString(temp,JSON.toJson(value));
            try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private WorldSnapshotStore(){}
}
