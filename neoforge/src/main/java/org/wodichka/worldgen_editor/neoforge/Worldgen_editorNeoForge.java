package org.wodichka.worldgen_editor.neoforge;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.command.WorldgenEditorCommands;
import org.wodichka.worldgen_editor.world.IslandBiomeSource;
import org.wodichka.worldgen_editor.world.IslandContinentsFunction;
import org.wodichka.worldgen_editor.world.IslandDensityFunction;
import org.wodichka.worldgen_editor.world.IslandCoastFunction;
import org.wodichka.worldgen_editor.world.IslandGeneratorCodec;
import org.wodichka.worldgen_editor.world.IslandWorldState;

@Mod(Worldgen_editor.MOD_ID)
public final class Worldgen_editorNeoForge {
    public Worldgen_editorNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRegistries);
        if (Boolean.getBoolean("wge.terrablenderVerification")) {
            try { Class.forName("org.wodichka.worldgen_editor.verification.TerraBlenderVerification").getMethod("register").invoke(null); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        if (Boolean.getBoolean("wge.verification")) {
            modEventBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) -> {
                try { event.register(Class.forName("org.wodichka.worldgen_editor.verification.GenerationGameTests")); }
                catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
            });
        }
        Worldgen_editor.init();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
    }

    private void registerRegistries(RegisterEvent event) {
        event.register(Registries.BIOME_SOURCE, IslandBiomeSource.ID, () -> IslandBiomeSource.CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandContinentsFunction.ID, () -> IslandContinentsFunction.MAP_CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandDensityFunction.ID, () -> IslandDensityFunction.MAP_CODEC);
        event.register(Registries.DENSITY_FUNCTION_TYPE, IslandCoastFunction.ID, () -> IslandCoastFunction.MAP_CODEC);
        event.register(Registries.CHUNK_GENERATOR, IslandGeneratorCodec.ID, () -> IslandGeneratorCodec.CODEC);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        WorldgenEditorCommands.register(event.getDispatcher());
    }

    private void serverStarting(ServerAboutToStartEvent event) {
        IslandWorldState.prepareForServer(event.getServer());
        TerraBlenderCompatibility.initialize(event.getServer());
    }

    private void serverStopped(ServerStoppedEvent event) {
        IslandWorldState.clear();
    }
}
