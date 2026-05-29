package org.wodichka.worldgen_editor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.wodichka.worldgen_editor.Worldgen_editor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class IslandConfigLoader {
    public static final Path CONFIG_PATH = Path.of("config", Worldgen_editor.MOD_ID, "continents.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Double> DEFAULT_AMPLITUDES = List.of(1.0D, 0.78D, 0.55D, 0.34D);
    private static final double DEFAULT_NOISE_SCALE = 3.2D;
    private static final int DEFAULT_FIRST_OCTAVE = -1;
    private static final double DEFAULT_MULTIPLIER = 1.0D;
    private static final double DEFAULT_NOISE_STRENGTH = 0.18D;
    private static final double DEFAULT_EDGE_WIDTH = 0.16D;
    private static final double DEFAULT_SHAPE_POWER = 2.0D;
    private static final double MIN_SHAPE_POWER = 0.75D;
    private static final double MAX_SHAPE_POWER = 8.0D;
    private static final int DEFAULT_ARCHIPELAGO_COUNT = 12;
    private static final double DEFAULT_ARCHIPELAGO_SPREAD = 0.9D;
    private static final double DEFAULT_ARCHIPELAGO_SPACING = 1.2D;
    private static final double DEFAULT_ARCHIPELAGO_MIN_STRETCH = 0.65D;
    private static final double DEFAULT_ARCHIPELAGO_MAX_STRETCH = 1.6D;
    private static final double DEFAULT_ARCHIPELAGO_MIN_SHAPE_POWER = 1.2D;
    private static final double DEFAULT_ARCHIPELAGO_MAX_SHAPE_POWER = 3.8D;
    private static final int DEFAULT_BIOME_PATCH_SIZE = 512;
    private static final int MIN_BIOME_PATCH_SIZE = 4;
    private static final int MAX_BIOME_PATCH_SIZE = 4096;

    private IslandConfigLoader() {
    }

    public static IslandConfig loadOrCreate() throws IslandConfigException {
        try {
            ensureDefaultExists();
            return load(CONFIG_PATH);
        } catch (IOException exception) {
            throw new IslandConfigException("Could not read island config at " + CONFIG_PATH, exception);
        }
    }

    public static IslandConfig load(Path path) throws IOException, IslandConfigException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            return parse(root);
        } catch (JsonParseException exception) {
            throw new IslandConfigException("Invalid JSON in " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void ensureDefaultExists() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        Files.createDirectories(CONFIG_PATH.getParent());
        try (InputStream input = IslandConfigLoader.class.getResourceAsStream("/assets/worldgen_editor/default_continents.json")) {
            if (input == null) {
                throw new IOException("Bundled default_continents.json is missing");
            }
            Files.copy(input, CONFIG_PATH);
        }
    }

    static IslandConfig parse(JsonElement root) throws IslandConfigException {
        JsonObject object = requireObject(root, "root");
        boolean enabled = optionalBoolean(object, "enabled", false, "root");
        String outerOcean = optionalString(object, "outer_ocean", IslandConfig.DEFAULT_OUTER_OCEAN, "root");
        if (!isValidBiomeId(outerOcean)) {
            throw new IslandConfigException("root.outer_ocean must be a biome id like minecraft:deep_ocean");
        }
        JsonArray entries = requireArray(object, "entries", "root");
        List<IslandEntry> parsedEntries = new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            JsonObject entry = requireObject(entries.get(index), "entries[" + index + "]");
            String path = "entries[" + index + "]";
            String name = optionalString(entry, "name", "island_" + (index + 1), path);
            IslandEntryType type = parseEntryType(optionalString(entry, "type", "island", path), path);
            JsonObject noise = optionalObject(entry, "noise", path);
            List<Double> amplitudes = noise == null || !has(noise, "amplitudes")
                    ? DEFAULT_AMPLITUDES
                    : parseAmplitudes(requireArray(noise, "amplitudes", path + ".noise"), index);

            double centerX = requireAliasDouble(entry, path, "x", "center_x");
            double centerZ = requireAliasDouble(entry, path, "z", "center_z");
            double radius = optionalPositiveDouble(entry, path, -1.0D, "radius", "size");
            double radiusX = optionalPositiveDouble(entry, path, radius, "radius_x", "x_divisor");
            double radiusZ = optionalPositiveDouble(entry, path, radius, "radius_z", "z_divisor");
            double stretchX = optionalPositiveDouble(entry, path, 1.0D, "stretch_x");
            double stretchZ = optionalPositiveDouble(entry, path, 1.0D, "stretch_z");
            double compiledRadiusX = radiusX * stretchX;
            double compiledRadiusZ = radiusZ * stretchZ;

            if (radiusX <= 0.0D || radiusZ <= 0.0D) {
                throw new IslandConfigException(path + " must define either radius or both radius_x and radius_z");
            }

            int count = type == IslandEntryType.ARCHIPELAGO ? optionalPositiveInt(entry, path, DEFAULT_ARCHIPELAGO_COUNT, "count") : 0;
            double clusterRadius = Math.max(compiledRadiusX, compiledRadiusZ);
            double minRadius = type == IslandEntryType.ARCHIPELAGO
                    ? optionalPositiveDouble(entry, path, clusterRadius * 0.08D, "min_radius")
                    : 0.0D;
            double maxRadius = type == IslandEntryType.ARCHIPELAGO
                    ? optionalPositiveDouble(entry, path, clusterRadius * 0.18D, "max_radius")
                    : 0.0D;
            if (type == IslandEntryType.ARCHIPELAGO && minRadius > maxRadius) {
                throw new IslandConfigException(path + ".min_radius must be <= max_radius");
            }

            double minStretch = type == IslandEntryType.ARCHIPELAGO
                    ? optionalPositiveDouble(entry, path, DEFAULT_ARCHIPELAGO_MIN_STRETCH, "min_stretch")
                    : 0.0D;
            double maxStretch = type == IslandEntryType.ARCHIPELAGO
                    ? optionalPositiveDouble(entry, path, DEFAULT_ARCHIPELAGO_MAX_STRETCH, "max_stretch")
                    : 0.0D;
            if (type == IslandEntryType.ARCHIPELAGO && minStretch > maxStretch) {
                throw new IslandConfigException(path + ".min_stretch must be <= max_stretch");
            }

            double minShapePower = type == IslandEntryType.ARCHIPELAGO
                    ? optionalRangeDouble(entry, path, DEFAULT_ARCHIPELAGO_MIN_SHAPE_POWER, MIN_SHAPE_POWER, MAX_SHAPE_POWER, "min_shape_power")
                    : 0.0D;
            double maxShapePower = type == IslandEntryType.ARCHIPELAGO
                    ? optionalRangeDouble(entry, path, DEFAULT_ARCHIPELAGO_MAX_SHAPE_POWER, MIN_SHAPE_POWER, MAX_SHAPE_POWER, "max_shape_power")
                    : 0.0D;
            if (type == IslandEntryType.ARCHIPELAGO && minShapePower > maxShapePower) {
                throw new IslandConfigException(path + ".min_shape_power must be <= max_shape_power");
            }

            parsedEntries.add(new IslandEntry(
                    type,
                    name,
                    optionalBoolean(entry, "overlap", false, path),
                    parseBiomeSelectors(optionalArray(entry, "exclude_biomes", path), path + ".exclude_biomes"),
                    parseTemperature(optionalString(entry, firstPresent(entry, "temperature", "climate"), "standard", path), path),
                    optionalRangeInt(entry, path, DEFAULT_BIOME_PATCH_SIZE, MIN_BIOME_PATCH_SIZE, MAX_BIOME_PATCH_SIZE, "biome_patch_size"),
                    new IslandNoise(
                            amplitudes,
                            noise == null ? name : optionalString(noise, "seed", name, path + ".noise"),
                            noise == null ? DEFAULT_FIRST_OCTAVE : optionalInt(noise, "first_octave", DEFAULT_FIRST_OCTAVE, path + ".noise"),
                            noise == null ? DEFAULT_NOISE_SCALE : optionalPositiveDouble(noise, path + ".noise", DEFAULT_NOISE_SCALE, "scale")
                    ),
                    compiledRadiusX,
                    compiledRadiusZ,
                    optionalDouble(entry, path, 0.0D, "rotation", "rotation_degrees"),
                    optionalRangeDouble(entry, path, DEFAULT_SHAPE_POWER, MIN_SHAPE_POWER, MAX_SHAPE_POWER, "shape_power"),
                    optionalPositiveDouble(entry, path, DEFAULT_MULTIPLIER, "multiplier", "size_multiplier"),
                    centerX,
                    centerZ,
                    optionalRangeDouble(entry, path, DEFAULT_NOISE_STRENGTH, 0.0D, 1.0D, "roughness", "noise_strength"),
                    optionalPositiveDouble(entry, path, DEFAULT_EDGE_WIDTH, "shore_width", "edge_width"),
                    count,
                    minRadius,
                    maxRadius,
                    type == IslandEntryType.ARCHIPELAGO ? optionalRangeDouble(entry, path, DEFAULT_ARCHIPELAGO_SPREAD, 0.05D, 1.0D, "spread") : 0.0D,
                    type == IslandEntryType.ARCHIPELAGO ? optionalPositiveDouble(entry, path, DEFAULT_ARCHIPELAGO_SPACING, "spacing") : 0.0D,
                    minStretch,
                    maxStretch,
                    minShapePower,
                    maxShapePower
            ));
        }

        return new IslandConfig(enabled, outerOcean, parsedEntries);
    }

    private static List<String> parseBiomeSelectors(JsonArray array, String path) throws IslandConfigException {
        if (array == null) {
            return List.of();
        }

        List<String> selectors = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IslandConfigException(path + "[" + index + "] must be a string");
            }

            String selector = element.getAsString();
            if (!isValidBiomeSelector(selector)) {
                throw new IslandConfigException(path + "[" + index + "] must be a biome id or tag like minecraft:plains or #minecraft:is_ocean");
            }
            selectors.add(selector);
        }
        return selectors;
    }

    private static boolean isValidBiomeSelector(String selector) {
        String id = selector.startsWith("#") ? selector.substring(1) : selector;
        return isValidBiomeId(id);
    }

    private static boolean isValidBiomeId(String id) {
        if (id.isEmpty()) {
            return false;
        }
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1 || id.indexOf(':', separator + 1) >= 0) {
            return false;
        }
        return isValidResourcePart(id.substring(0, separator), false) && isValidResourcePart(id.substring(separator + 1), true);
    }

    private static boolean isValidResourcePart(String value, boolean path) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '-'
                    || character == '.'
                    || path && character == '/';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static IslandEntryType parseEntryType(String value, String path) throws IslandConfigException {
        return switch (value.toLowerCase()) {
            case "island" -> IslandEntryType.ISLAND;
            case "ocean" -> IslandEntryType.OCEAN;
            case "archipelago" -> IslandEntryType.ARCHIPELAGO;
            default -> throw new IslandConfigException(path + ".type must be one of island, ocean, archipelago");
        };
    }

    private static IslandTemperature parseTemperature(String value, String path) throws IslandConfigException {
        return switch (value.toLowerCase()) {
            case "standard", "standart", "vanilla", "auto" -> IslandTemperature.STANDARD;
            case "cold" -> IslandTemperature.COLD;
            case "temperate", "normal" -> IslandTemperature.TEMPERATE;
            case "warm", "hot" -> IslandTemperature.WARM;
            default -> throw new IslandConfigException(path + ".temperature must be one of standard, cold, temperate, warm");
        };
    }

    private static List<Double> parseAmplitudes(JsonArray array, int entryIndex) throws IslandConfigException {
        if (array.isEmpty()) {
            throw new IslandConfigException("entries[" + entryIndex + "].noise.amplitudes must not be empty");
        }

        List<Double> amplitudes = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            double amplitude = requireFiniteDouble(array.get(index), "entries[" + entryIndex + "].noise.amplitudes[" + index + "]");
            if (amplitude < 0.0D) {
                throw new IslandConfigException("entries[" + entryIndex + "].noise.amplitudes[" + index + "] must be >= 0");
            }
            amplitudes.add(amplitude);
        }
        return amplitudes;
    }

    private static JsonElement require(JsonObject object, String key, String path) throws IslandConfigException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            throw new IslandConfigException(path + "." + key + " is required");
        }
        return value;
    }

    private static JsonObject requireObject(JsonElement element, String path) throws IslandConfigException {
        if (element == null || !element.isJsonObject()) {
            throw new IslandConfigException(path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject optionalObject(JsonObject object, String key, String path) throws IslandConfigException {
        if (!has(object, key)) {
            return null;
        }

        JsonElement value = object.get(key);
        if (!value.isJsonObject()) {
            throw new IslandConfigException(path + "." + key + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray optionalArray(JsonObject object, String key, String path) throws IslandConfigException {
        if (!has(object, key)) {
            return null;
        }

        JsonElement value = object.get(key);
        if (!value.isJsonArray()) {
            throw new IslandConfigException(path + "." + key + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static JsonArray requireArray(JsonObject object, String key, String path) throws IslandConfigException {
        JsonElement value = require(object, key, path);
        if (!value.isJsonArray()) {
            throw new IslandConfigException(path + "." + key + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static boolean requireBoolean(JsonObject object, String key, int entryIndex) throws IslandConfigException {
        JsonElement value = require(object, key, "entries[" + entryIndex + "]");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IslandConfigException("entries[" + entryIndex + "]." + key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static boolean optionalBoolean(JsonObject object, String key, boolean fallback, String path) throws IslandConfigException {
        if (!has(object, key)) {
            return fallback;
        }

        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IslandConfigException(path + "." + key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String requireString(JsonObject object, String key, String path) throws IslandConfigException {
        JsonElement value = require(object, key, path);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IslandConfigException(path + "." + key + " must be a string");
        }
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String key, String fallback, String path) throws IslandConfigException {
        if (!has(object, key)) {
            return fallback;
        }

        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IslandConfigException(path + "." + key + " must be a string");
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject object, String key, String path) throws IslandConfigException {
        double value = requireDouble(object, key, path);
        if (value != Math.rint(value)) {
            throw new IslandConfigException(path + "." + key + " must be an integer");
        }
        return (int) value;
    }

    private static int optionalInt(JsonObject object, String key, int fallback, String path) throws IslandConfigException {
        if (!has(object, key)) {
            return fallback;
        }

        double value = requireDouble(object, key, path);
        if (value != Math.rint(value)) {
            throw new IslandConfigException(path + "." + key + " must be an integer");
        }
        return (int) value;
    }

    private static int optionalPositiveInt(JsonObject object, String path, int fallback, String... keys) throws IslandConfigException {
        for (String key : keys) {
            if (has(object, key)) {
                int value = requireInt(object, key, path);
                if (value <= 0) {
                    throw new IslandConfigException(path + "." + key + " must be positive");
                }
                return value;
            }
        }
        return fallback;
    }

    private static int optionalRangeInt(JsonObject object, String path, int fallback, int min, int max, String... keys) throws IslandConfigException {
        for (String key : keys) {
            if (has(object, key)) {
                int value = requireInt(object, key, path);
                if (value < min || value > max) {
                    throw new IslandConfigException(path + "." + key + " must be between " + min + " and " + max);
                }
                return value;
            }
        }
        return fallback;
    }

    private static double requirePositiveDouble(JsonObject object, String key, String path) throws IslandConfigException {
        double value = requireDouble(object, key, path);
        if (value <= 0.0D) {
            throw new IslandConfigException(path + "." + key + " must be positive");
        }
        return value;
    }

    private static double optionalPositiveDouble(JsonObject object, String path, double fallback, String... keys) throws IslandConfigException {
        for (String key : keys) {
            if (has(object, key)) {
                double value = requireDouble(object, key, path);
                if (value <= 0.0D) {
                    throw new IslandConfigException(path + "." + key + " must be positive");
                }
                return value;
            }
        }
        return fallback;
    }

    private static double optionalRangeDouble(JsonObject object, String path, double fallback, double min, double max, String... keys) throws IslandConfigException {
        for (String key : keys) {
            if (has(object, key)) {
                double value = requireDouble(object, key, path);
                if (value < min || value > max) {
                    throw new IslandConfigException(path + "." + key + " must be between " + min + " and " + max);
                }
                return value;
            }
        }
        return fallback;
    }

    private static double optionalDouble(JsonObject object, String path, double fallback, String... keys) throws IslandConfigException {
        for (String key : keys) {
            if (has(object, key)) {
                return requireDouble(object, key, path);
            }
        }
        return fallback;
    }

    private static double requireAliasDouble(JsonObject object, String path, String... keys) throws IslandConfigException {
        for (String key : keys) {
            if (has(object, key)) {
                return requireDouble(object, key, path);
            }
        }
        throw new IslandConfigException(path + " must define " + String.join(" or ", keys));
    }

    private static double requireDouble(JsonObject object, String key, String path) throws IslandConfigException {
        return requireFiniteDouble(require(object, key, path), path + "." + key);
    }

    private static double requireFiniteDouble(JsonElement element, String path) throws IslandConfigException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IslandConfigException(path + " must be a number");
        }

        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IslandConfigException(path + " must be finite");
        }
        return value;
    }

    private static boolean has(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull();
    }

    private static String firstPresent(JsonObject object, String primary, String secondary) {
        return has(object, primary) ? primary : secondary;
    }
}
