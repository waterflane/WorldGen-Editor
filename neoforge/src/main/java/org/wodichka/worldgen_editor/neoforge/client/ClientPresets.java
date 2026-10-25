package org.wodichka.worldgen_editor.neoforge.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.*;
import org.wodichka.worldgen_editor.config.*;
import org.wodichka.worldgen_editor.world.IslandBiomeSource;
import java.util.*;
import java.util.concurrent.*;

/** Vanilla's World Type cycle button reads this live list. Registry entries and existing saves stay untouched. */
public final class ClientPresets {
    public record Selection(String id,String label,int version) {}
    private record Cache(Object registries,List<PresetCatalog.Entry> metadata,List<WorldCreationUiState.WorldTypeEntry> entries) {}
    private static final Map<WorldPreset,Selection> NAMES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<WorldCreationUiState,Cache> CACHES=new WeakHashMap<>();
    private static volatile List<PresetCatalog.Entry> metadata;
    private static final PresetCatalog CATALOG=new PresetCatalog(IslandConfigLoader.PRESETS_DIR);
    private static final ScheduledExecutorService SCANNER=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"WGE preset discovery");thread.setDaemon(true);return thread;});
    static {SCANNER.scheduleWithFixedDelay(()->{try{metadata=CATALOG.scan();}catch(Exception error){org.slf4j.LoggerFactory.getLogger("worldgen_editor").warn("Could not refresh WGE preset directory: {}",error.getMessage());}},0,1,TimeUnit.SECONDS);}
    private ClientPresets(){}

    public static Selection selection(WorldCreationUiState.WorldTypeEntry entry){return entry==null||entry.preset()==null?null:NAMES.get(entry.preset().value());}

    public static List<WorldCreationUiState.WorldTypeEntry> expand(WorldCreationUiState ui,List<WorldCreationUiState.WorldTypeEntry> vanilla){
        var result=new ArrayList<WorldCreationUiState.WorldTypeEntry>();
        for(var entry:vanilla)if(entry.preset()==null||entry.preset().unwrapKey().map(key->!key.location().getNamespace().equals("worldgen_editor")).orElse(true))result.add(entry);
        var snapshot=metadata;if(snapshot==null)return List.copyOf(result);
        var context=ui.getSettings();Object registries=context.worldgenRegistries();var cached=CACHES.get(ui);
        if(cached==null||cached.registries!=registries||!cached.metadata.equals(snapshot)){
            var entries=new ArrayList<WorldCreationUiState.WorldTypeEntry>();
            var names=new HashMap<String,Integer>();
            for(var item:snapshot){
                int ordinal=names.merge(item.name(),1,Integer::sum);
                String label="WGE: "+item.name()+(ordinal==1?"":" ("+ordinal+")")+(item.error()==null?"":" [invalid]");
                var preset=create(ui,item);
                NAMES.put(preset,new Selection(item.id(),label,item.generationVersion()));
                entries.add(new WorldCreationUiState.WorldTypeEntry(Holder.direct(preset)));
            }
            cached=new Cache(registries,snapshot,List.copyOf(entries));CACHES.put(ui,cached);
        }
        result.addAll(cached.entries);return List.copyOf(result);
    }

    public static WorldCreationUiState.WorldTypeEntry find(WorldCreationUiState ui,String id){
        return expand(ui,List.of()).stream().filter(entry->selection(entry).id().equals(id)).findFirst().orElse(null);
    }

    private static WorldPreset create(WorldCreationUiState ui,PresetCatalog.Entry item){
        var registries=ui.getSettings().worldgenLoadContext();
        var normal=registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
        var original=normal.overworld();
        if(!(original instanceof NoiseBasedChunkGenerator noise))throw new IllegalStateException("WGE requires a noise-based Overworld generator");
        var source=IslandBiomeSource.forPreset(noise.getBiomeSource(),item.id(),item.generationVersion(),registries.lookupOrThrow(Registries.BIOME));
        var settings=item.generationVersion()>=3?noise.generatorSettings():registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(
            net.minecraft.resources.ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("worldgen_editor","island_overworld")));
        var dimensions=new HashMap<>(normal.dimensions());var overworld=dimensions.get(LevelStem.OVERWORLD);
        dimensions.put(LevelStem.OVERWORLD,new LevelStem(overworld.type(),new NoiseBasedChunkGenerator(source,settings)));
        return new WorldPreset(dimensions);
    }
}
