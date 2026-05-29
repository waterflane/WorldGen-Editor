package org.wodichka.worldgen_editor.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.config.IslandConfig;
import org.wodichka.worldgen_editor.config.IslandConfigException;
import org.wodichka.worldgen_editor.config.IslandConfigLoader;
import org.wodichka.worldgen_editor.config.IslandTemperature;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static volatile Registry<Biome> biomeRegistry;
    private static volatile Holder<Biome> oceanBiome;
    private static volatile Holder<Biome> deepOceanBiome;
    private static volatile Holder<Biome> outerOceanBiome;
    private static volatile Holder<Biome> frozenOceanBiome;
    private static volatile Holder<Biome> deepFrozenOceanBiome;
    private static volatile Holder<Biome> coldOceanBiome;
    private static volatile Holder<Biome> deepColdOceanBiome;
    private static volatile Holder<Biome> lukewarmOceanBiome;
    private static volatile Holder<Biome> deepLukewarmOceanBiome;
    private static volatile Holder<Biome> warmOceanBiome;
    private static volatile Holder<Biome> beachBiome;
    private static volatile Holder<Biome> plainsBiome;
    private static volatile Holder<Biome> forestBiome;
    private static volatile Holder<Biome> meadowBiome;
    private static volatile Holder<Biome> snowyPlainsBiome;
    private static volatile Holder<Biome> taigaBiome;
    private static volatile Holder<Biome> savannaBiome;
    private static volatile Holder<Biome> jungleBiome;
    private static volatile Holder<Biome> desertBiome;
    private static final Set<String> EXCLUSION_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final List<TagKey<Biome>> OCEAN_TAGS = List.of(tag("minecraft:is_ocean"), tag("c:is_ocean"), tag("forge:is_ocean"));
    private static final List<TagKey<Biome>> COLD_TAGS = List.of(tag("c:is_cold"), tag("forge:is_cold"), tag("c:cold"), tag("forge:cold"));
    private static final List<TagKey<Biome>> HOT_TAGS = List.of(tag("c:is_hot"), tag("forge:is_hot"), tag("c:is_warm"), tag("forge:is_warm"), tag("c:hot"), tag("forge:hot"));
    private static final List<TagKey<Biome>> SNOWY_TAGS = List.of(tag("c:is_snowy"), tag("forge:is_snowy"), tag("c:snowy"), tag("forge:snowy"));
    private static final List<TagKey<Biome>> TEMPERATE_TAGS = List.of(tag("c:is_temperate"), tag("forge:is_temperate"), tag("c:temperate"), tag("forge:temperate"));

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
        biomeRegistry = biomes;
        oceanBiome = biomes.getHolderOrThrow(Biomes.OCEAN);
        deepOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_OCEAN);
        frozenOceanBiome = biomes.getHolderOrThrow(Biomes.FROZEN_OCEAN);
        deepFrozenOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_FROZEN_OCEAN);
        coldOceanBiome = biomes.getHolderOrThrow(Biomes.COLD_OCEAN);
        deepColdOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_COLD_OCEAN);
        lukewarmOceanBiome = biomes.getHolderOrThrow(Biomes.LUKEWARM_OCEAN);
        deepLukewarmOceanBiome = biomes.getHolderOrThrow(Biomes.DEEP_LUKEWARM_OCEAN);
        warmOceanBiome = biomes.getHolderOrThrow(Biomes.WARM_OCEAN);
        beachBiome = biomes.getHolderOrThrow(Biomes.BEACH);
        plainsBiome = biomes.getHolderOrThrow(Biomes.PLAINS);
        forestBiome = biomes.getHolderOrThrow(Biomes.FOREST);
        meadowBiome = biomes.getHolderOrThrow(Biomes.MEADOW);
        snowyPlainsBiome = biomes.getHolderOrThrow(Biomes.SNOWY_PLAINS);
        taigaBiome = biomes.getHolderOrThrow(Biomes.TAIGA);
        savannaBiome = biomes.getHolderOrThrow(Biomes.SAVANNA);
        jungleBiome = biomes.getHolderOrThrow(Biomes.JUNGLE);
        desertBiome = biomes.getHolderOrThrow(Biomes.DESERT);
        refreshOuterOceanBiome();
        EXCLUSION_WARNINGS.clear();
        MASK.set(new IslandMask(CONFIG.get(), worldSeed));
        LOGGER.info("WorldGen Editor is {} for this world", enabled ? "enabled" : "disabled");
    }

    public static boolean reloadConfig() {
        try {
            IslandConfig config = IslandConfigLoader.loadOrCreate();
            CONFIG.set(config);
            MASK.set(new IslandMask(config, worldSeed));
            refreshOuterOceanBiome();
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
        return oceanBiome(islandMask, null, null, 0, 0);
    }

    public static Holder<Biome> oceanBiome(double islandMask, IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        boolean deep = islandMask < IslandTerrainHooks.DEEP_OCEAN_MASK;
        ClimateBand climate = climateBand(source, delegate, blockX, blockZ);
        List<Holder<Biome>> candidates = switch (climate) {
            case COLD -> deep
                    ? nonNullBiomes(deepFrozenOceanBiome, deepColdOceanBiome, frozenOceanBiome, coldOceanBiome)
                    : nonNullBiomes(frozenOceanBiome, coldOceanBiome);
            case WARM -> deep
                    ? nonNullBiomes(deepLukewarmOceanBiome, warmOceanBiome, lukewarmOceanBiome, deepOceanBiome, oceanBiome)
                    : nonNullBiomes(warmOceanBiome, lukewarmOceanBiome, oceanBiome);
            case TEMPERATE -> deep
                    ? nonNullBiomes(deepOceanBiome, oceanBiome)
                    : nonNullBiomes(oceanBiome, deepOceanBiome);
        };
        Holder<Biome> fallback = switch (climate) {
            case COLD -> firstNonNull(frozenOceanBiome, coldOceanBiome, oceanBiome);
            case WARM -> firstNonNull(warmOceanBiome, lukewarmOceanBiome, oceanBiome, deepOceanBiome);
            case TEMPERATE -> oceanBiome;
        };
        return firstAllowed(candidates, source, fallback);
    }

    public static Holder<Biome> outerOceanBiome() {
        return firstNonNull(outerOceanBiome, deepOceanBiome, oceanBiome);
    }

    public static List<Holder<Biome>> oceanBiomes() {
        return nonNullBiomes(
                oceanBiome,
                deepOceanBiome,
                outerOceanBiome,
                frozenOceanBiome,
                deepFrozenOceanBiome,
                coldOceanBiome,
                deepColdOceanBiome,
                lukewarmOceanBiome,
                deepLukewarmOceanBiome,
                warmOceanBiome
        );
    }

    public static List<Holder<Biome>> fallbackLandBiomes() {
        return nonNullBiomes(
                plainsBiome,
                forestBiome,
                meadowBiome,
                beachBiome,
                snowyPlainsBiome,
                taigaBiome,
                savannaBiome,
                jungleBiome,
                desertBiome
        );
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

    public static Holder<Biome> landBiome(IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        return landBiome(1.0D, source, delegate, blockX, blockZ);
    }

    public static Holder<Biome> landBiome(double islandMask, IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        if (source == null) {
            Holder<Biome> coast = replaceOceanDelegateOnLand(islandMask, delegate);
            if (coast != null) {
                return coast;
            }
            return delegate;
        }

        ClimateBand configured = configuredClimateBand(source);
        Holder<Biome> coast = replaceOceanDelegateOnLand(islandMask, delegate);
        if (coast != null && !isExcluded(coast, source)) {
            return coast;
        }

        if (configured == null) {
            if (!isExcluded(delegate, source)) {
                return delegate;
            }
            configured = climateBand(null, delegate, blockX, blockZ);
        } else if (delegate != null && !isExcluded(delegate, source) && !isOceanBiome(delegate) && matchesLandClimate(delegate, configured)) {
            return delegate;
        }

        List<Holder<Biome>> candidates = switch (configured) {
            case COLD -> nonNullBiomes(snowyPlainsBiome, taigaBiome);
            case WARM -> nonNullBiomes(savannaBiome, jungleBiome, desertBiome);
            case TEMPERATE -> nonNullBiomes(plainsBiome, forestBiome, meadowBiome);
        };
        Holder<Biome> fallback = pickAllowed(candidates, source, blockX, blockZ);
        if (fallback != null) {
            return fallback;
        }

        warnAllExcluded(source);
        return switch (configured) {
            case COLD -> firstNonNull(snowyPlainsBiome, taigaBiome, plainsBiome, delegate);
            case WARM -> firstNonNull(savannaBiome, jungleBiome, desertBiome, plainsBiome, delegate);
            case TEMPERATE -> firstNonNull(plainsBiome, forestBiome, meadowBiome, delegate);
        };
    }

    private static Holder<Biome> replaceOceanDelegateOnLand(double islandMask, Holder<Biome> delegate) {
        if (!isOceanBiome(delegate)) {
            return null;
        }
        if (islandMask < IslandTerrainHooks.LAND_MASK) {
            return firstNonNull(beachBiome, plainsBiome, forestBiome, meadowBiome);
        }
        return firstNonNull(plainsBiome, forestBiome, meadowBiome);
    }

    public static boolean isOceanBiome(Holder<Biome> biome) {
        return biome != null && (hasAnyTag(biome, OCEAN_TAGS)
                || biome.is(Biomes.OCEAN)
                || biome.is(Biomes.DEEP_OCEAN)
                || biome.is(Biomes.COLD_OCEAN)
                || biome.is(Biomes.DEEP_COLD_OCEAN)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)
                || biome.is(Biomes.WARM_OCEAN));
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

    private static Holder<Biome> pickAllowed(List<Holder<Biome>> candidates, IslandMask.SourceInfo source, int blockX, int blockZ) {
        List<Holder<Biome>> allowed = new ArrayList<>();
        for (Holder<Biome> candidate : candidates) {
            if (candidate != null && !isExcluded(candidate, source)) {
                allowed.add(candidate);
            }
        }
        if (allowed.isEmpty()) {
            return null;
        }

        int patchSize = biomePatchSize(source);
        long seed = source == null ? worldSeed : source.climateSeed();
        int cellX = Math.floorDiv(blockX, patchSize);
        int cellZ = Math.floorDiv(blockZ, patchSize);
        double localX = (double) Math.floorMod(blockX, patchSize) / patchSize;
        double localZ = (double) Math.floorMod(blockZ, patchSize) / patchSize;
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestIndex = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int candidateCellX = cellX + dx;
                int candidateCellZ = cellZ + dz;
                long mixed = mix(seed, candidateCellX * 341873128712L);
                mixed = mix(mixed, candidateCellZ * 132897987541L);
                double jitterX = randomUnit(mixed, 1L);
                double jitterZ = randomUnit(mixed, 2L);
                double siteX = dx + jitterX;
                double siteZ = dz + jitterZ;
                double distanceX = localX - siteX;
                double distanceZ = localZ - siteZ;
                double distance = distanceX * distanceX + distanceZ * distanceZ;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = Math.floorMod((int) (mix(mixed, 3L) >>> 32), allowed.size());
                }
            }
        }

        return allowed.get(bestIndex);
    }

    private static Holder<Biome> firstAllowed(List<Holder<Biome>> candidates, IslandMask.SourceInfo source, Holder<Biome> fallback) {
        for (Holder<Biome> candidate : candidates) {
            if (candidate != null && !isExcluded(candidate, source)) {
                return candidate;
            }
        }
        warnAllExcluded(source);
        return firstNonNull(fallback, oceanBiome, deepOceanBiome);
    }

    private static boolean isExcluded(Holder<Biome> biome, IslandMask.SourceInfo source) {
        if (biome == null || source == null || source.excludedBiomes().isEmpty()) {
            return false;
        }

        for (String selector : source.excludedBiomes()) {
            if (selector.startsWith("#")) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, ResourceLocation.parse(selector.substring(1)));
                if (biome.is(tag)) {
                    return true;
                }
            } else {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(selector));
                if (biome.is(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ClimateBand climateBand(IslandMask.SourceInfo source, Holder<Biome> delegate, int blockX, int blockZ) {
        ClimateBand configured = configuredClimateBand(source);
        if (configured != null) {
            return configured;
        }

        if (delegate != null) {
            if (isColdBiome(delegate)) {
                return ClimateBand.COLD;
            }
            if (isWarmBiome(delegate)) {
                return ClimateBand.WARM;
            }
            if (isTemperateBiome(delegate)) {
                return ClimateBand.TEMPERATE;
            }
        }

        return ClimateBand.TEMPERATE;
    }

    private static ClimateBand configuredClimateBand(IslandMask.SourceInfo source) {
        if (source == null || source.temperature() == null || source.temperature() == IslandTemperature.STANDARD) {
            return null;
        }
        return switch (source.temperature()) {
            case COLD -> ClimateBand.COLD;
            case TEMPERATE -> ClimateBand.TEMPERATE;
            case WARM -> ClimateBand.WARM;
            case STANDARD -> null;
        };
    }

    private static int biomePatchSize(IslandMask.SourceInfo source) {
        if (source == null || source.biomePatchSize() <= 0) {
            return 512;
        }
        return source.biomePatchSize();
    }

    private static void refreshOuterOceanBiome() {
        Registry<Biome> registry = biomeRegistry;
        Holder<Biome> fallback = firstNonNull(deepOceanBiome, oceanBiome);
        if (registry == null) {
            outerOceanBiome = fallback;
            return;
        }

        String configured = CONFIG.get().outerOcean();
        Holder<Biome> resolved = null;
        try {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(configured));
            resolved = registry.getHolder(key).orElse(null);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid outer_ocean '{}'; using minecraft:deep_ocean", configured);
        }

        if (resolved != null && isOceanBiome(resolved)) {
            outerOceanBiome = resolved;
            return;
        }

        if (resolved == null) {
            LOGGER.warn("outer_ocean '{}' is not registered; using minecraft:deep_ocean", configured);
        } else {
            LOGGER.warn("outer_ocean '{}' is not an ocean-like biome; using minecraft:deep_ocean", configured);
        }
        outerOceanBiome = fallback;
    }

    private static boolean matchesLandClimate(Holder<Biome> biome, ClimateBand climate) {
        return switch (climate) {
            case COLD -> isColdBiome(biome);
            case WARM -> isWarmBiome(biome) && !isColdBiome(biome);
            case TEMPERATE -> isTemperateBiome(biome) && !isColdBiome(biome) && !isWarmBiome(biome);
        };
    }

    private static boolean isColdBiome(Holder<Biome> biome) {
        return biome != null && (hasAnyTag(biome, COLD_TAGS)
                || hasAnyTag(biome, SNOWY_TAGS)
                || biome.is(Biomes.SNOWY_PLAINS)
                || biome.is(Biomes.ICE_SPIKES)
                || biome.is(Biomes.SNOWY_TAIGA)
                || biome.is(Biomes.GROVE)
                || biome.is(Biomes.SNOWY_SLOPES)
                || biome.is(Biomes.JAGGED_PEAKS)
                || biome.is(Biomes.FROZEN_PEAKS)
                || biome.is(Biomes.FROZEN_RIVER)
                || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || biome.is(Biomes.COLD_OCEAN)
                || biome.is(Biomes.DEEP_COLD_OCEAN));
    }

    private static boolean isWarmBiome(Holder<Biome> biome) {
        return biome != null && (hasAnyTag(biome, HOT_TAGS)
                || biome.is(Biomes.DESERT)
                || biome.is(Biomes.SAVANNA)
                || biome.is(Biomes.SAVANNA_PLATEAU)
                || biome.is(Biomes.WINDSWEPT_SAVANNA)
                || biome.is(Biomes.JUNGLE)
                || biome.is(Biomes.SPARSE_JUNGLE)
                || biome.is(Biomes.BAMBOO_JUNGLE)
                || biome.is(Biomes.BADLANDS)
                || biome.is(Biomes.ERODED_BADLANDS)
                || biome.is(Biomes.WOODED_BADLANDS)
                || biome.is(Biomes.WARM_OCEAN)
                || biome.is(Biomes.LUKEWARM_OCEAN)
                || biome.is(Biomes.DEEP_LUKEWARM_OCEAN));
    }

    private static boolean isTemperateBiome(Holder<Biome> biome) {
        return biome != null && (hasAnyTag(biome, TEMPERATE_TAGS)
                || biome.is(Biomes.PLAINS)
                || biome.is(Biomes.SUNFLOWER_PLAINS)
                || biome.is(Biomes.FOREST)
                || biome.is(Biomes.FLOWER_FOREST)
                || biome.is(Biomes.BIRCH_FOREST)
                || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
                || biome.is(Biomes.DARK_FOREST)
                || biome.is(Biomes.MEADOW)
                || biome.is(Biomes.TAIGA)
                || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA)
                || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.SWAMP)
                || biome.is(Biomes.MANGROVE_SWAMP)
                || biome.is(Biomes.RIVER)
                || biome.is(Biomes.OCEAN)
                || biome.is(Biomes.DEEP_OCEAN));
    }

    private static boolean hasAnyTag(Holder<Biome> biome, List<TagKey<Biome>> tags) {
        for (TagKey<Biome> tag : tags) {
            if (biome.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static TagKey<Biome> tag(String id) {
        return TagKey.create(Registries.BIOME, ResourceLocation.parse(id));
    }

    @SafeVarargs
    private static List<Holder<Biome>> nonNullBiomes(Holder<Biome>... biomes) {
        List<Holder<Biome>> holders = new ArrayList<>();
        for (Holder<Biome> biome : biomes) {
            if (biome != null) {
                holders.add(biome);
            }
        }
        return List.copyOf(holders);
    }

    @SafeVarargs
    private static Holder<Biome> firstNonNull(Holder<Biome>... biomes) {
        for (Holder<Biome> biome : biomes) {
            if (biome != null) {
                return biome;
            }
        }
        return null;
    }

    private static void warnAllExcluded(IslandMask.SourceInfo source) {
        if (source != null && EXCLUSION_WARNINGS.add(source.name())) {
            LOGGER.warn("All preferred biome fallbacks were excluded for '{}'; using safe vanilla fallback", source.name());
        }
    }

    private enum ClimateBand {
        COLD,
        TEMPERATE,
        WARM
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

    private static double randomUnit(long seed, long salt) {
        long mixed = mix(seed, salt * 0x9e3779b97f4a7c15L);
        return (mixed >>> 11) * 0x1.0p-53D;
    }
}
