package org.wodichka.worldgen_editor.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.command.WorldgenEditorCommands;
import org.wodichka.worldgen_editor.world.IslandBiomeSource;
import org.wodichka.worldgen_editor.world.IslandContinentsFunction;
import org.wodichka.worldgen_editor.world.IslandDensityFunction;
import org.wodichka.worldgen_editor.world.IslandWorldState;

@Mod(Worldgen_editor.MOD_ID)
public final class Worldgen_editorNeoForge {
    public Worldgen_editorNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRegistries);
        Worldgen_editor.init();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::levelLoad);
    }

    private void registerRegistries(RegisterEvent event) {
        event.register(Registries.BIOME_SOURCE, IslandBiomeSource.ID, () -> IslandBiomeSource.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandContinentsFunction.ID, () -> IslandContinentsFunction.MAP_CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandDensityFunction.ID, () -> IslandDensityFunction.MAP_CODEC);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        WorldgenEditorCommands.register(event.getDispatcher());
    }

    private void levelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            IslandWorldState.loadForServer(level.getServer());
        }
    }
}
