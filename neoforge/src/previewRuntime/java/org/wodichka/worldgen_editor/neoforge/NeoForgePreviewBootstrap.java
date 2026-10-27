package org.wodichka.worldgen_editor.neoforge;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.wodichka.worldgen_editor.preview.*;
import org.wodichka.worldgen_editor.world.*;
import java.nio.file.*;
import java.util.*;

/** Prepared development runtime only; never packaged in the released mod. */
public final class NeoForgePreviewBootstrap {
    private static boolean verified(Path site,String fingerprint){
        try{var report=JsonParser.parseString(Files.readString(site.resolveSibling("WorldGen Editor").resolve("tools/modded-preview-verification.json"))).getAsJsonObject();
            return report.getAsJsonObject("profiles").entrySet().stream().anyMatch(e->e.getValue().getAsJsonObject().get("resources").getAsString().equals(fingerprint)&&e.getValue().getAsJsonObject().get("status").getAsString().equals("verified"));
        }catch(Exception unavailable){return false;}
    }
    private static void hashClass(java.security.MessageDigest digest,Class<?> type)throws Exception {
        try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){if(input==null)throw new IllegalStateException("Missing runtime class "+type.getName());digest.update(input.readAllBytes());}
        for(var nested:type.getDeclaredClasses())hashClass(digest,nested);
    }
    private static String effectiveFingerprint(String code,net.minecraft.server.packs.resources.ResourceManager resources,Class<?> factory){
        try{var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));hashClass(digest,factory);
            for(String directory:List.of("worldgen","dimension","dimension_type","tags")){
                var stacks=new TreeMap<>(resources.listResourceStacks(directory,id->id.getPath().endsWith(".json")));
                for(var entry:stacks.entrySet()){// Stack order and bytes matter; Architectury-generated pack labels change on every launch.
                    var stack=new ArrayList<byte[]>();for(var resource:entry.getValue())try(var input=resource.open()){stack.add(input.readAllBytes());}
                    ResourceFingerprint.updateStack(digest,entry.getKey().toString(),stack);
                }
            }return "neoforge-preview-3-"+HexFormat.of().formatHex(digest.digest());
        }catch(Exception error){throw new IllegalStateException("Could not fingerprint loaded worldgen resources",error);}
    }
    public static void register(){
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,false,ServerAboutToStartEvent.class,event->{
            try{
                var access=event.getServer().registryAccess();Path site=Path.of(System.getProperty("wge.preview.site"));
                var sources=new HashMap<String,String>();sources.put("minecraft","Minecraft");ModList.get().getMods().forEach(mod->sources.put(mod.getModId(),mod.getDisplayName()));
                JsonObject colors=Files.isRegularFile(site.resolve("biome-palette.json"))?JsonParser.parseString(Files.readString(site.resolve("biome-palette.json"))).getAsJsonObject():new JsonObject();
                var presets=new TreeMap<String,LevelStem>();
                access.registryOrThrow(Registries.WORLD_PRESET).entrySet().forEach(entry->entry.getValue().overworld().ifPresent(stem->{if(stem.generator() instanceof NoiseBasedChunkGenerator)presets.put(entry.getKey().location().toString(),stem);}));
                if(presets.isEmpty())throw new IllegalStateException("No supported noise-based Overworld presets in this modpack");
                var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(colors.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update(System.getProperty("wge.preview.fingerprint").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(var type:List.of(GenerationContext.class,IslandBiomeRules.class,BiomeClimateCatalog.class,NaturalCoast.class,IslandMask.class,IslandNoiseComposition.class,IslandBiomeSource.class,IslandContinentsFunction.class,IslandDepthFunction.class,IslandCoastFunction.class,CoastField.class,org.wodichka.worldgen_editor.config.IslandConfigLoader.class,org.wodichka.worldgen_editor.config.IslandTemperature.class,PreviewWorld.class,ColumnSurfaceSampler.class,ResourceFingerprint.class,PreviewServer.class,NeoForgePreviewBootstrap.class)){
                    hashClass(digest,type);
                }
                String fingerprint="neoforge-preview-3-"+HexFormat.of().formatHex(digest.digest());
                PreviewServer.start(new String[]{site.toString()},new PreviewRuntime(){
                    private final String effective=effectiveFingerprint(fingerprint,event.getServer().getResourceManager(),getClass());
                    public String fingerprint(){return effective;}
                    public JsonObject catalog(){var result=PreviewCatalog.create(access,sources,colors);result.addProperty("resources",effective);result.addProperty("accuracy",verified(site,effective)?"verified":"unverified");
                        var mods=new JsonArray();ModList.get().getMods().forEach(mod->{var entry=new JsonObject();entry.addProperty("id",mod.getModId());entry.addProperty("name",mod.getDisplayName());entry.addProperty("version",mod.getVersion().toString());mods.add(entry);});result.add("mods",mods);var list=new JsonArray();presets.keySet().forEach(list::add);result.add("world_presets",list);return result;}
                    public PreviewWorld create(JsonElement config,long seed,int version,String preset)throws Exception{
                        if(version<3)throw new IllegalArgumentException("Modpack preview requires generation 3 or newer; keep legacy presets in the vanilla profile");
                        var stem=presets.get(preset);if(stem==null)throw new IllegalArgumentException("Unsupported world preset: "+preset);
                        var generator=(NoiseBasedChunkGenerator)stem.generator();
                        var delegate=generator.getBiomeSource() instanceof IslandBiomeSource island?island.delegate():generator.getBiomeSource();
                        var settings=(Object)generator instanceof OriginalNoiseSettings original?original.wge$originalSettings().value():generator.generatorSettings().value();
                        return new PreviewWorld(config,seed,version,access,delegate,settings,g->{
                            if(ModList.get().isLoaded("terrablender"))try{
                                Class.forName("terrablender.util.LevelUtils").getMethod("initializeBiomes",RegistryAccess.class,Holder.class,ResourceKey.class,net.minecraft.world.level.chunk.ChunkGenerator.class,long.class)
                                    .invoke(null,access,stem.type(),LevelStem.OVERWORLD,g,seed);
                            }catch(ReflectiveOperationException error){throw new IllegalStateException("Could not initialize TerraBlender for preview session",error);}
                        });
                    }
                });
                // Registries and server-load hooks have completed. No levels, spawn chunks or ticks are needed for supported samplers.
                new java.util.concurrent.CountDownLatch(1).await();
            }catch(Exception failure){throw new IllegalStateException("Modpack preview initialization failed",failure);}
        });
    }
}
