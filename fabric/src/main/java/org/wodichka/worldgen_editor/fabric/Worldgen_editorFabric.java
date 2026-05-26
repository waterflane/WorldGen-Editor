package org.wodichka.worldgen_editor.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.world.level.Level;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.command.WorldgenEditorCommands;
import org.wodichka.worldgen_editor.world.IslandBiomeSource;
import org.wodichka.worldgen_editor.world.IslandContinentsFunction;
import org.wodichka.worldgen_editor.world.IslandDensityFunction;
import org.wodichka.worldgen_editor.world.IslandWorldState;

public final class Worldgen_editorFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        IslandBiomeSource.register();
        IslandContinentsFunction.register();
        IslandDensityFunction.register();
        Worldgen_editor.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WorldgenEditorCommands.register(dispatcher));
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.dimension() == Level.OVERWORLD) {
                IslandWorldState.loadForServer(server);
            }
        });
    }
}
