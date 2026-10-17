package org.wodichka.worldgen_editor.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.world.IslandWorldState;

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
                .then(Commands.literal("migrate")
                        .executes(context -> {
                            if (!IslandWorldState.migrate()) return 0;
                            context.getSource().sendSuccess(() -> Component.literal("Generation migrated to version 2; existing chunks are unchanged."), true);
                            return 1;
                        }))
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
        source.sendSuccess(() -> Component.literal("""
                WorldGen Editor Status
                - Effective: %s
                - Global config: %s
                - World flag: %s
                - World type preset: %s
                - Active preset: %s
                - Generation version: %d
                - Entries: %d
                - Config: %s
                - World state: %s""".formatted(
                enabledText(IslandWorldState.isEnabled()),
                enabledText(IslandWorldState.isGlobalEnabled()),
                enabledText(IslandWorldState.isWorldEnabled()),
                IslandWorldState.hasWorldPreset() ? IslandWorldState.worldPresetName() : "none",
                IslandWorldState.activePresetName(),
                IslandWorldState.generationVersion(),
                IslandWorldState.islandCount(),
                IslandWorldState.activeConfigPath(),
                worldState == null ? "not loaded" : worldState
        )), false);
        return IslandWorldState.isEnabled() ? 1 : 0;
    }

    private static String enabledText(boolean value) {
        return value ? "enabled" : "disabled";
    }

    private static int setPreset(CommandSourceStack source, String presetName) {
        if (IslandWorldState.selectPreset(presetName)) {
            source.sendSuccess(() -> Component.literal("World preset changed to " + presetName + "; only new chunks are affected."), true);
            return 1;
        }
        source.sendFailure(Component.literal("Could not change world preset; previous snapshot is still active."));
        return 0;
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
