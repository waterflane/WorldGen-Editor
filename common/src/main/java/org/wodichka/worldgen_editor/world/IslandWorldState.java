package org.wodichka.worldgen_editor.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.config.IslandConfig;
import org.wodichka.worldgen_editor.config.IslandConfigException;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class IslandWorldState {
    private static final Logger LOGGER = LoggerFactory.getLogger(Worldgen_editor.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<IslandConfig> CONFIG = new AtomicReference<>(IslandConfig.EMPTY);
    private static final AtomicReference<IslandMask> MASK = new AtomicReference<>(new IslandMask(IslandConfig.EMPTY, 0L));

    private static volatile long worldSeed;
    private static volatile boolean enabled;
    private static volatile boolean worldEnabled = true;
    private static volatile Path worldStatePath;
    private static volatile Holder<Biome> oceanBiome;
    private static volatile Holder<Biome> deepOceanBiome;
    private static volatile Holder<Biome> beachBiome;
    private static volatile Holder<Biome> plainsBiome;
    private static volatile Holder<Biome> forestBiome;
    private static volatile Holder<Biome> meadowBiome;

    private IslandWorldState() {
    }

    public static void loadGlobalConfig() {
        loadGlobalConfig("init", false);
    }

    private static void loadGlobalConfig(String phase, boolean keepPreviousOnFailure) {
        try {
            IslandConfig config = IslandConfigLoader.loadOrCreate();
            CONFIG.set(config);
            MASK.set(new IslandMask(config, worldSeed));
            enabled = effectiveEnabled();
            LOGGER.info("Loaded {} island entries from {} during {}", config.entries().size(), IslandConfigLoader.CONFIG_PATH, phase);
        } catch (IslandConfigException exception) {
            if (keepPreviousOnFailure) {
                LOGGER.error("Keeping previous island config because {} load failed: {}", phase, exception.getMessage());
                return;
            }

            LOGGER.error("Failed to load island config during {}: {}", phase, exception.getMessage());
            CONFIG.set(IslandConfig.EMPTY);
            MASK.set(new IslandMask(IslandConfig.EMPTY, worldSeed));
            enabled = false;
        }
    }

    public static void loadForServer(MinecraftServer server) {
        worldSeed = server.overworld().getSeed();
        loadGlobalConfig("world start", false);
        worldStatePath = server.getWorldPath(LevelResource.ROOT).resolve(Worldgen_editor.MOD_ID).resolve("worldgen_editor.json");
        worldEnabled = loadOrCreateWorldState(worldStatePath);
        enabled = effectiveEnabled();
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        oceanBiome = biomes.getHolderOrThrow(Biomes.OCEAN);
        deepOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_OCEAN);
        beachBiome = biomes.getHolderOrThrow(Biomes.BEACH);
        plainsBiome = biomes.getHolderOrThrow(Biomes.PLAINS);
        forestBiome = biomes.getHolderOrThrow(Biomes.FOREST);
        meadowBiome = biomes.getHolderOrThrow(Biomes.MEADOW);
        MASK.set(new IslandMask(CONFIG.get(), worldSeed));
        LOGGER.info("WorldGen Editor is {} for this world", enabled ? "enabled" : "disabled");
    }

    public static boolean reloadConfig() {
        try {
            IslandConfig config = IslandConfigLoader.loadOrCreate();
            CONFIG.set(config);
            MASK.set(new IslandMask(config, worldSeed));
            enabled = effectiveEnabled();
            LOGGER.info("Loaded {} island entries from {} during reload", config.entries().size(), IslandConfigLoader.CONFIG_PATH);
            return true;
        } catch (IslandConfigException exception) {
            LOGGER.error("Keeping previous island config because reload failed: {}", exception.getMessage());
            return false;
        }
    }

    public static boolean setEnabled(boolean value) {
        worldEnabled = value;
        enabled = effectiveEnabled();
        return saveWorldState(value);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isGlobalEnabled() {
        return CONFIG.get().enabled();
    }

    public static boolean isWorldEnabled() {
        return worldEnabled;
    }

    public static int islandCount() {
        return CONFIG.get().entries().size();
    }

    public static Path worldStatePath() {
        return worldStatePath;
    }

    public static Holder<Biome> oceanBiome(double islandMask) {
        Holder<Biome> ocean = oceanBiome;
        Holder<Biome> deepOcean = deepOceanBiome;
        if (ocean == null || deepOcean == null) {
            return null;
        }
        return islandMask < IslandTerrainHooks.DEEP_OCEAN_MASK ? deepOcean : ocean;
    }

    public static List<Holder<Biome>> oceanBiomes() {
        Holder<Biome> ocean = oceanBiome;
        Holder<Biome> deepOcean = deepOceanBiome;
        if (ocean == null || deepOcean == null) {
            return List.of();
        }
        return List.of(ocean, deepOcean);
    }

    public static List<Holder<Biome>> fallbackLandBiomes() {
        Holder<Biome> plains = plainsBiome;
        Holder<Biome> forest = forestBiome;
        Holder<Biome> meadow = meadowBiome;
        Holder<Biome> beach = beachBiome;
        if (plains == null || forest == null || meadow == null || beach == null) {
            return List.of();
        }
        return List.of(plains, forest, meadow, beach);
    }

    public static Holder<Biome> beachBiome() {
        return beachBiome;
    }

    public static Holder<Biome> landBiome(double islandMask, int blockX, int blockZ) {
        Holder<Biome> plains = plainsBiome;
        Holder<Biome> forest = forestBiome;
        Holder<Biome> meadow = meadowBiome;
        if (plains == null || forest == null || meadow == null) {
            return null;
        }

        double inner = smoothstep(IslandTerrainHooks.LAND_MASK, 0.96D, islandMask);
        long mixed = mix(worldSeed, blockX * 341873128712L);
        mixed = mix(mixed, blockZ * 132897987541L);
        double choice = ((mixed >>> 11) * 0x1.0p-53D);

        if (inner > 0.70D && choice < 0.28D) {
            return forest;
        }
        if (inner > 0.56D && choice > 0.78D) {
            return meadow;
        }
        return plains;
    }

    public static boolean isOceanBiome(Holder<Biome> biome) {
        return biome.is(Biomes.OCEAN)
                || biome.is(Biomes.DEEP_OCEAN)
                || biome.is(Biomes.COLD_OCEAN)
                || biome.is(Biomes.DEEP_COLD_OCEAN)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.is(Biomes.WARM_OCEAN);
    }

    public static IslandMask mask() {
        if (!enabled) {
            return new IslandMask(IslandConfig.EMPTY, worldSeed);
        }
        return MASK.get();
    }

    public static boolean hasConfig() {
        return !CONFIG.get().entries().isEmpty();
    }

    public static long worldSeed() {
        return worldSeed;
    }

    private static boolean loadOrCreateWorldState(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                writeWorldState(path, true);
                return true;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject object = GSON.fromJson(reader, JsonObject.class);
                if (object == null || !object.has("enabled") || !object.get("enabled").isJsonPrimitive()) {
                    writeWorldState(path, true);
                    return true;
                }
                return object.get("enabled").getAsBoolean();
            }
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            LOGGER.error("Could not load world state from {}. Disabling generation for this world.", path, exception);
            return false;
        }
    }

    private static boolean saveWorldState(boolean value) {
        Path path = worldStatePath;
        if (path == null) {
            LOGGER.error("Cannot save worldgen state before a server world is loaded");
            return false;
        }

        try {
            Files.createDirectories(path.getParent());
            writeWorldState(path, value);
            return true;
        } catch (IOException exception) {
            LOGGER.error("Could not save world state to {}", path, exception);
            return false;
        }
    }

    private static void writeWorldState(Path path, boolean value) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", value);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(object, writer);
        }
    }

    private static boolean effectiveEnabled() {
        return CONFIG.get().enabled() && worldEnabled;
    }

    private static double smoothstep(double min, double max, double value) {
        double x = (value - min) / (max - min);
        if (x < 0.0D) {
            x = 0.0D;
        } else if (x > 1.0D) {
            x = 1.0D;
        }
        return x * x * (3.0D - 2.0D * x);
    }

    private static long mix(long seed, long value) {
        long mixed = seed ^ value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }
}
