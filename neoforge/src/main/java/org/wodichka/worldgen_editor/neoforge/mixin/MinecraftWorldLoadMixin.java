package org.wodichka.worldgen_editor.neoforge.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wodichka.worldgen_editor.world.*;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;
import java.util.concurrent.CompletableFuture;

/** Validate and pin the map before spinning up the integrated server. Invalid maps return to a working screen. */
@Mixin(Minecraft.class)
public abstract class MinecraftWorldLoadMixin {
    @Unique private WorldStem wge$approved;
    @Inject(method="doWorldLoad",at=@At("HEAD"),cancellable=true)
    private void wge$validateBeforeStarting(LevelStorageSource.LevelStorageAccess storage,PackRepository packs,WorldStem stem,boolean fresh,CallbackInfo ci){
        if(wge$approved==stem)return;
        var level=stem.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        if(level==null||!(level.generator() instanceof NoiseBasedChunkGenerator generator)||!(generator.getBiomeSource() instanceof IslandBiomeSource source))return;
        ci.cancel();
        var minecraft=(Minecraft)(Object)this;
        var progress=new GenericMessageScreen(Component.literal("Validating WGE map…"));minecraft.setScreen(progress);
        CompletableFuture.runAsync(()->{
            try{
                var snapshot=WorldSnapshotStore.loadOrCreate(storage.getLevelPath(LevelResource.ROOT).resolve("worldgen_editor/worldgen_editor.json"),
                    source.presetName(),source.generationVersion(),id->IslandConfigLoader.loadOrCreate(id,source.generationVersion()>=3));
                new GenerationContext(stem.worldData().worldGenOptions().seed(),snapshot.config().withEnabled(snapshot.enabled()&&snapshot.config().enabled()),
                    snapshot.generationVersion(),stem.registries().compositeAccess().registryOrThrow(Registries.BIOME));
                minecraft.execute(()->{
                    if(minecraft.screen!=progress){wge$closePrepared(stem,storage);return;}
                    wge$approved=stem;try{minecraft.doWorldLoad(storage,packs,stem,fresh);}finally{wge$approved=null;}
                });
            }catch(Exception error){
                org.slf4j.LoggerFactory.getLogger("worldgen_editor").error("WGE map validation failed before server startup",error);
                wge$closePrepared(stem,storage);
                minecraft.execute(()->{
                    if(minecraft.screen==progress)minecraft.setScreen(new DisconnectedScreen(new SelectWorldScreen(new TitleScreen()),Component.literal("WGE map could not be loaded"),
                        Component.literal("Preset: "+source.presetName()+".json\n"+error.getMessage()+"\n\nFix the JSON in config/worldgen_editor/presets and try again. Editor projects and exported presets are supported. Painted maps require a new world selected from the live WGE world-type list."),Component.literal("Back to Worlds")));
                });
            }
        });
    }
    @Unique private static void wge$closePrepared(WorldStem stem,LevelStorageSource.LevelStorageAccess storage){
        try{stem.close();}catch(Exception ignored){}storage.safeClose();
    }
}
