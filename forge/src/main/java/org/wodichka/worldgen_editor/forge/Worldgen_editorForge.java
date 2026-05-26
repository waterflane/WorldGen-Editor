package org.wodichka.worldgen_editor.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.command.WorldgenEditorCommands;
import org.wodichka.worldgen_editor.world.IslandBiomeSource;
import org.wodichka.worldgen_editor.world.IslandContinentsFunction;
import org.wodichka.worldgen_editor.world.IslandDensityFunction;
import org.wodichka.worldgen_editor.world.IslandWorldState;

@Mod(Worldgen_editor.MOD_ID)
public final class Worldgen_editorForge {
    public Worldgen_editorForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerRegistries);
        Worldgen_editor.init();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerRegistries(RegisterEvent event) {
        event.register(Registries.BIOME_SOURCE, IslandBiomeSource.ID, () -> IslandBiomeSource.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandContinentsFunction.ID, () -> IslandContinentsFunction.MAP_CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandDensityFunction.ID, () -> IslandDensityFunction.MAP_CODEC);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        WorldgenEditorCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void levelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            IslandWorldState.loadForServer(level.getServer());
        }
    }
}
