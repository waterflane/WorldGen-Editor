package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import org.wodichka.worldgen_editor.config.IslandTemperature;
import org.wodichka.worldgen_editor.world.BiomeClimateCatalog;
import java.util.Map;

public final class PreviewCatalog {
    public static JsonObject create(RegistryAccess access,Map<String,String> sources,JsonObject colors){
        var result=new JsonObject();var biomes=new JsonArray();var climates=new JsonArray();
        var registry=access.registryOrThrow(Registries.BIOME);var catalog=new BiomeClimateCatalog(registry);
        registry.holders().sorted(java.util.Comparator.comparing(BiomeClimateCatalog::id)).forEach(b->{
            String id=BiomeClimateCatalog.id(b),namespace=b.key().location().getNamespace();var item=new JsonObject();
            item.addProperty("id",id);item.addProperty("name",title(b.key().location().getPath()));item.addProperty("source",sources.getOrDefault(namespace,title(namespace)));
            if(colors.has(id))item.add("color",colors.get(id));biomes.add(item);
        });
        for(var choice:IslandTemperature.values()){
            if(choice.special()&&catalog.candidates(choice).isEmpty())continue;
            String id=choice.id(),namespace=id.contains(":")?id.split(":")[0]:"minecraft";var item=new JsonObject();
            item.addProperty("id",id);item.addProperty("name",choice==IslandTemperature.STANDARD?"Default":title(id.contains(":")?id.split(":")[1]:id));
            item.addProperty("source",sources.getOrDefault(namespace,title(namespace)));climates.add(item);
        }
        result.add("biomes",biomes);result.add("climates",climates);return result;
    }
    private static String title(String text){return java.util.Arrays.stream(text.replace('/',' ').split("[_ ]")).filter(s->!s.isEmpty()).map(s->Character.toUpperCase(s.charAt(0))+s.substring(1)).collect(java.util.stream.Collectors.joining(" "));}
    private PreviewCatalog(){}
}
