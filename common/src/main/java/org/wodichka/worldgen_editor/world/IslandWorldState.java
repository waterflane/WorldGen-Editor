package org.wodichka.worldgen_editor.world;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wodichka.worldgen_editor.config.*;

import java.io.IOException;
import java.nio.file.*;

/** Server lifecycle/commands only. Generation functions never consult this global facade. */
public final class IslandWorldState {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldgen_editor");
    private static GenerationBinding binding;
    private static String preset;
    private static Path statePath;
    private static boolean worldEnabled = true;
    private static IslandConfig storedConfig = IslandConfig.EMPTY;
    private static int version = 1;
    private static boolean restartRequired;
    private IslandWorldState() {}

    public static void loadGlobalConfig() {
        try { IslandConfigLoader.loadOrCreate(); }
        catch (IslandConfigException error) { LOGGER.error("Could not load WGE config", error); }
    }

    /** Called before ServerLevel/ChunkMap constructors can cache possible biomes or noises. */
    public static void prepareForServer(MinecraftServer server) {
        clear();
        var stem = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator generator)
                || !(generator.getBiomeSource() instanceof IslandBiomeSource source)) return;
        preset = source.presetName(); version = source.generationVersion();
        statePath = server.getWorldPath(LevelResource.ROOT).resolve("worldgen_editor/worldgen_editor.json");
        try {
            var saved=WorldSnapshotStore.loadOrCreate(statePath,preset,version,IslandConfigLoader::loadOrCreate);
            worldEnabled=saved.enabled();preset=saved.preset();version=saved.generationVersion();storedConfig=saved.config();
            Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
            binding = new GenerationBinding();
            binding.set(new GenerationContext(server.getWorldData().worldGenOptions().seed(), effectiveConfig(), version, biomes));
            source.bind(binding);
            generator.generatorSettings().value().noiseRouter().mapAll(function -> {
                if (function instanceof IslandContinentsFunction island) island.bind(binding);
                if (function instanceof IslandDensityFunction island) island.bind(binding);
                if (function instanceof IslandCoastFunction island) island.bind(binding);
                return function;
            });
            save(storedConfig, worldEnabled, preset, version);
        } catch (Exception error) {
            // Never silently generate a different world when a pinned snapshot cannot be loaded.
            clear();
            throw new IllegalStateException("Could not prepare WGE generation snapshot", error);
        }
    }

    public static void clear() {
        if (binding != null) binding.set(null);
        binding = null; preset = null; statePath = null;
        worldEnabled = true; storedConfig = IslandConfig.EMPTY; version = 1;
        restartRequired=false;
    }

    private static IslandConfig effectiveConfig() {
        return storedConfig.withEnabled(storedConfig.enabled() && worldEnabled);
    }

    private static boolean replace(IslandConfig config, boolean flag, String name, int newVersion) {
        if (binding == null) return false;
        try {
            GenerationContext previous = binding.get();
            IslandConfig effective = config.withEnabled(config.enabled() && flag);
            // Validate before saving, but keep the running graph immutable. NoiseChunk and
            // RandomState may already contain cached cells or be used by generation workers.
            new GenerationContext(previous.seed(), effective, newVersion, previous.biomes());
            save(config, flag, name, newVersion);
            storedConfig = config; worldEnabled = flag; preset = name; version = newVersion;
            restartRequired=true;
            return true;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Keeping previous WGE snapshot", error); return false;
        }
    }

    public static boolean reloadConfig() {
        try { return replace(IslandConfigLoader.loadOrCreate(preset), worldEnabled, preset, version); }
        catch (IslandConfigException error) { LOGGER.error("Keeping previous WGE config", error); return false; }
    }
    public static boolean selectPreset(String name) {
        try { return replace(IslandConfigLoader.loadOrCreate(name), worldEnabled, name, version); }
        catch (IslandConfigException error) { LOGGER.error("Could not select preset", error); return false; }
    }
    public static boolean migrate() { return replace(storedConfig, worldEnabled, preset, Math.max(version,2)); }
    public static boolean setEnabled(boolean flag) { return replace(storedConfig, flag, preset, version); }
    public static boolean isEnabled() { return binding != null && binding.get().config().enabled(); }
    public static boolean isGlobalEnabled() { return storedConfig.enabled(); }
    public static boolean isWorldEnabled() { return worldEnabled; }
    public static boolean hasWorldPreset() { return preset != null; }
    public static String worldPresetName() { return preset; }
    public static String activePresetName() { return preset == null ? "none" : preset; }
    public static Path activeConfigPath() { return statePath; }
    public static Path worldStatePath() { return statePath; }
    public static int islandCount() { return storedConfig.entries().size(); }
    public static int generationVersion() { return binding!=null&&binding.get()!=null?binding.get().generationVersion():version; }
    public static boolean requiresRestart() { return restartRequired; }
    public static GenerationContext context() { return binding == null ? null : binding.get(); }

    private static void save(IslandConfig config, boolean flag, String name, int algorithm) throws IOException {
        WorldSnapshotStore.save(statePath,new WorldSnapshotStore.Snapshot(flag,name,algorithm,config));
    }
}
