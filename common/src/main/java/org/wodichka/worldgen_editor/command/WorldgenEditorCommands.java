package org.wodichka.worldgen_editor.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.config.IslandConfigException;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;
import org.wodichka.worldgen_editor.world.IslandWorldState;

import java.io.IOException;
import java.nio.file.Path;

public final class WorldgenEditorCommands {
    private WorldgenEditorCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Worldgen_editor.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable")
                        .executes(context -> setEnabled(context.getSource(), true)))
                .then(Commands.literal("disable")
                        .executes(context -> setEnabled(context.getSource(), false)))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setPreset(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        if (IslandWorldState.setEnabled(enabled)) {
            source.sendSuccess(() -> Component.literal("WorldGen Editor world flag is now " + (enabled ? "enabled" : "disabled")
                    + ". Effective generation is " + (IslandWorldState.isEnabled() ? "enabled" : "disabled")
                    + "."), true);
            return 1;
        }

        source.sendFailure(Component.literal("Could not save WorldGen Editor world state."));
        return 0;
    }

    private static int status(CommandSourceStack source) {
        Path worldState = IslandWorldState.worldStatePath();
        source.sendSuccess(() -> Component.literal("WorldGen Editor: "
                + "effective=" + IslandWorldState.isEnabled()
                + ", global_enabled=" + IslandWorldState.isGlobalEnabled()
                + ", world_enabled=" + IslandWorldState.isWorldEnabled()
                + ", islands=" + IslandWorldState.islandCount()
                + ", preset=" + IslandWorldState.activePresetName()
                + ", config=" + IslandWorldState.activeConfigPath()
                + ", world_state=" + (worldState == null ? "not loaded" : worldState)), false);
        return IslandWorldState.isEnabled() ? 1 : 0;
    }

    private static int setPreset(CommandSourceStack source, String presetName) {
        try {
            IslandConfigLoader.setActivePreset(presetName);
            IslandWorldState.setPresetOverride(presetName);
            boolean reloaded = IslandWorldState.reloadConfig();
            if (reloaded) {
                source.sendSuccess(() -> Component.literal("WorldGen Editor preset is now '" + presetName + "'. Newly generated chunks will use it."), true);
                return 1;
            }
            source.sendFailure(Component.literal("Preset was saved, but reload failed. Check the log before generating new chunks."));
            return 0;
        } catch (IslandConfigException | IOException exception) {
            source.sendFailure(Component.literal("Could not set WorldGen Editor preset: " + exception.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandSourceStack source) {
        boolean success = IslandWorldState.reloadConfig();
        if (success) {
            source.sendSuccess(() -> Component.literal("WorldGen Editor config reloaded for newly generated chunks."), true);
            return 1;
        }

        source.sendFailure(Component.literal("WorldGen Editor config reload failed. Previous valid config is still active."));
        return 0;
    }
}
