package org.wodichka.worldgen_editor.config;

import com.google.gson.JsonParser;
import org.wodichka.worldgen_editor.world.IslandMask;

public final class IslandConfigLoaderTest {
    public static void main(String[] args) throws Exception {
        parsesSimpleConfig();
        parsesValidConfig();
        parsesOuterOcean();
        rejectsMissingEntries();
        rejectsMissingRadius();
        rejectsInvalidOuterOcean();
        rejectsEmptyAmplitudes();
        rejectsInvalidType();
        rejectsInvalidBiomeSelector();
        parsesBiomeExclusions();
        parsesTemperatureAndBiomePatchSize();
        parsesStandardTemperatureAliases();
        rejectsInvalidTemperatureAndPatchSize();
        rejectsInvalidArchipelagoRanges();
        parsesOceanAndArchipelago();
        oceanMaskCarvesLand();
        sampleInfoReportsSources();
        archipelagoMaskIsDeterministic();
        islandMaskIsDeterministic();
    }

    private static void parsesSimpleConfig() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "enabled": true,
                  "entries": [
                    {
                      "name": "Simple",
                      "x": 100,
                      "z": -200,
                      "radius": 450
                    }
                  ]
                }"""));

        IslandEntry entry = config.entries().getFirst();
        assertTrue(config.enabled(), "top-level enabled should parse");
        assertTrue(IslandConfig.DEFAULT_OUTER_OCEAN.equals(config.outerOcean()), "outer_ocean should default to deep ocean");
        assertTrue(entry.centerX() == 100.0D, "simple x should parse");
        assertTrue(entry.centerZ() == -200.0D, "simple z should parse");
        assertTrue(entry.type() == IslandEntryType.ISLAND, "missing type should default to island");
        assertTrue(entry.xDivisor() == 450.0D, "simple radius should set x radius");
        assertTrue(entry.zDivisor() == 450.0D, "simple radius should set z radius");
        assertTrue(entry.shapePower() == 2.0D, "simple config should get default shape power");
        assertTrue(!entry.noise().amplitudes().isEmpty(), "simple config should get default noise");
    }

    private static void parsesValidConfig() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "overlap": false,
                      "noise": {
                        "amplitudes": [1.0, 0.5],
                        "seed": "demo",
                        "first_octave": -1,
                        "scale": 3.0
                      },
                      "x_divisor": 100.0,
                      "z_divisor": 120.0,
                      "rotation_degrees": 15.0,
                      "multiplier": 1.0,
                      "center_x": 0.0,
                      "center_z": 0.0
                    }
                  ]
                }"""));

        assertTrue(config.entries().size() == 1, "valid config should parse one entry");
    }

    private static void parsesOuterOcean() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "enabled": true,
                  "outer_ocean": "minecraft:warm_ocean",
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100
                    }
                  ]
                }"""));

        assertTrue("minecraft:warm_ocean".equals(config.outerOcean()), "top-level outer_ocean should parse");
    }

    private static void rejectsMissingEntries() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("{}")), "missing entries must fail");
    }

    private static void rejectsMissingRadius() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0
                    }
                  ]
                }""")), "missing radius must fail");
    }

    private static void rejectsInvalidOuterOcean() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "outer_ocean": "#minecraft:is_ocean",
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100
                    }
                  ]
                }""")), "outer_ocean must be a biome id, not a tag");
    }

    private static void rejectsEmptyAmplitudes() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "overlap": false,
                      "noise": {
                        "amplitudes": [],
                        "seed": "demo",
                        "first_octave": -1,
                        "scale": 3.0
                      },
                      "x_divisor": 100.0,
                      "z_divisor": 120.0,
                      "rotation_degrees": 15.0,
                      "multiplier": 1.0,
                      "center_x": 0.0,
                      "center_z": 0.0
                    }
                  ]
                }""")), "empty amplitudes must fail");
    }

    private static void rejectsInvalidType() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "type": "lake",
                      "x": 0,
                      "z": 0,
                      "radius": 100
                    }
                  ]
                }""")), "invalid entry type must fail");
    }

    private static void rejectsInvalidBiomeSelector() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100,
                      "exclude_biomes": ["Minecraft:Desert"]
                    }
                  ]
                }""")), "invalid biome selector syntax must fail");
    }

    private static void parsesBiomeExclusions() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100,
                      "exclude_biomes": [
                        "minecraft:desert",
                        "#minecraft:is_badlands"
                      ]
                    }
                  ]
                }"""));

        assertTrue(config.entries().getFirst().excludedBiomes().size() == 2, "exclude_biomes should parse ids and tags");
    }

    private static void parsesTemperatureAndBiomePatchSize() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100,
                      "temperature": "warm",
                      "biome_patch_size": 1024
                    },
                    {
                      "x": 300,
                      "z": 0,
                      "radius": 100,
                      "climate": "cold"
                    }
                  ]
                }"""));

        assertTrue(config.entries().getFirst().temperature() == IslandTemperature.WARM, "temperature should parse");
        assertTrue(config.entries().getFirst().biomePatchSize() == 1024, "biome_patch_size should parse");
        assertTrue(config.entries().get(1).temperature() == IslandTemperature.COLD, "climate alias should parse");
    }

    private static void parsesStandardTemperatureAliases() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    { "x": 0, "z": 0, "radius": 100 },
                    { "x": 300, "z": 0, "radius": 100, "temperature": "standard" },
                    { "x": 600, "z": 0, "radius": 100, "temperature": "standart" },
                    { "x": 900, "z": 0, "radius": 100, "temperature": "vanilla" },
                    { "x": 1200, "z": 0, "radius": 100, "temperature": "auto" }
                  ]
                }"""));

        for (IslandEntry entry : config.entries()) {
            assertTrue(entry.temperature() == IslandTemperature.STANDARD, "standard temperature aliases should parse");
        }
    }

    private static void rejectsInvalidTemperatureAndPatchSize() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100,
                      "temperature": "boiling"
                    }
                  ]
                }""")), "invalid temperature must fail");

        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 100,
                      "biome_patch_size": 2
                    }
                  ]
                }""")), "too small biome_patch_size must fail");
    }

    private static void rejectsInvalidArchipelagoRanges() {
        assertThrows(() -> IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "type": "archipelago",
                      "x": 0,
                      "z": 0,
                      "radius": 1000,
                      "min_radius": 300,
                      "max_radius": 100
                    }
                  ]
                }""")), "archipelago min_radius > max_radius must fail");
    }

    private static void parsesOceanAndArchipelago() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "type": "ocean",
                      "x": 10,
                      "z": 20,
                      "radius_x": 100,
                      "radius_z": 300,
                      "shape_power": 1.4
                    },
                    {
                      "type": "archipelago",
                      "x": -10,
                      "z": -20,
                      "radius": 900,
                      "count": 7,
                      "min_radius": 80,
                      "max_radius": 160,
                      "min_shape_power": 1.2,
                      "max_shape_power": 3.4
                    }
                  ]
                }"""));

        assertTrue(config.entries().get(0).type() == IslandEntryType.OCEAN, "ocean type should parse");
        assertTrue(config.entries().get(0).shapePower() == 1.4D, "ocean shape_power should parse");
        assertTrue(config.entries().get(1).type() == IslandEntryType.ARCHIPELAGO, "archipelago type should parse");
        assertTrue(config.entries().get(1).count() == 7, "archipelago count should parse");
        assertTrue(config.entries().get(1).minShapePower() == 1.2D, "archipelago min shape power should parse");
        assertTrue(config.entries().get(1).maxShapePower() == 3.4D, "archipelago max shape power should parse");
    }

    private static void oceanMaskCarvesLand() throws Exception {
        IslandConfig landOnlyConfig = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 500,
                      "roughness": 0.0
                    }
                  ]
                }"""));
        IslandConfig carvedConfig = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "x": 0,
                      "z": 0,
                      "radius": 500,
                      "roughness": 0.0
                    },
                    {
                      "type": "ocean",
                      "x": 0,
                      "z": 0,
                      "radius": 120,
                      "roughness": 0.0
                    }
                  ]
                }"""));

        double landOnly = new IslandMask(landOnlyConfig, 1L).sample(0.0D, 0.0D);
        double carved = new IslandMask(carvedConfig, 1L).sample(0.0D, 0.0D);

        assertTrue(landOnly > 0.9D, "land center should be solid before ocean carve");
        assertTrue(carved < 0.1D, "ocean entry should carve land mask");
    }

    private static void sampleInfoReportsSources() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "name": "Main",
                      "x": 0,
                      "z": 0,
                      "radius": 500,
                      "roughness": 0.0
                    },
                    {
                      "name": "Lagoon",
                      "type": "ocean",
                      "x": 0,
                      "z": 0,
                      "radius": 120,
                      "roughness": 0.0
                    },
                    {
                      "name": "Outer",
                      "type": "archipelago",
                      "x": 1000,
                      "z": 0,
                      "radius": 500,
                      "count": 2,
                      "spacing": 1.0,
                      "noise": {
                        "seed": "outer"
                      }
                    }
                  ]
                }"""));

        IslandMask mask = new IslandMask(config, 42L);
        IslandMask.SampleInfo lagoon = mask.sampleInfo(0.0D, 0.0D);
        IslandMask.SampleInfo archipelago = mask.sampleInfo(1000.0D, 0.0D);

        assertTrue(lagoon.landSource() != null && "Main".equals(lagoon.landSource().name()), "sampleInfo should report strongest land source");
        assertTrue(lagoon.oceanSource() != null && "Lagoon".equals(lagoon.oceanSource().name()), "sampleInfo should report strongest ocean source");
        assertTrue(archipelago.archipelagoSource() != null && "Outer".equals(archipelago.archipelagoSource().name()), "sampleInfo should report archipelago parent source");
        assertTrue(archipelago.archipelagoSource().temperature() == IslandTemperature.STANDARD, "sampleInfo should expose source temperature");
        assertTrue(archipelago.archipelagoSource().biomePatchSize() == 512, "sampleInfo should expose source biome patch size");
    }

    private static void archipelagoMaskIsDeterministic() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "type": "archipelago",
                      "x": 0,
                      "z": 0,
                      "radius": 1200,
                      "count": 10,
                      "min_radius": 80,
                      "max_radius": 200,
                      "spacing": 1.0,
                      "noise": {
                        "seed": "archipelago"
                      }
                    }
                  ]
                }"""));

        IslandMask first = new IslandMask(config, 99L);
        IslandMask second = new IslandMask(config, 99L);
        IslandMask differentWorldSeed = new IslandMask(config, 123456L);

        for (int x = -1000; x <= 1000; x += 125) {
            for (int z = -1000; z <= 1000; z += 125) {
                assertTrue(Double.compare(first.sample(x, z), second.sample(x, z)) == 0, "archipelago same seed must be deterministic");
                assertTrue(Double.compare(first.sample(x, z), differentWorldSeed.sample(x, z)) == 0, "archipelago must not depend on world seed");
            }
        }

        IslandConfig differentConfigSeed = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "type": "archipelago",
                      "x": 0,
                      "z": 0,
                      "radius": 1200,
                      "count": 10,
                      "min_radius": 80,
                      "max_radius": 200,
                      "spacing": 1.0,
                      "noise": {
                        "seed": "other_archipelago"
                      }
                    }
                  ]
                }"""));
        assertTrue(anyDifferentSample(first, new IslandMask(differentConfigSeed, 99L)), "different archipelago noise.seed should change child islands");
    }

    private static void islandMaskIsDeterministic() throws Exception {
        IslandConfig config = IslandConfigLoader.parse(JsonParser.parseString("""
                {
                  "entries": [
                    {
                      "overlap": false,
                      "noise": {
                        "amplitudes": [1.0, 0.5, 0.25],
                        "seed": "demo",
                        "first_octave": -1,
                        "scale": 3.0
                      },
                      "x_divisor": 100.0,
                      "z_divisor": 120.0,
                      "rotation_degrees": 15.0,
                      "multiplier": 1.0,
                      "center_x": 0.0,
                      "center_z": 0.0
                    }
                  ]
                }"""));
        IslandMask first = new IslandMask(config, 12345L);
        IslandMask second = new IslandMask(config, 12345L);
        IslandMask otherSeed = new IslandMask(config, 54321L);

        double firstValue = first.sample(64.0D, -32.0D);
        double secondValue = second.sample(64.0D, -32.0D);

        assertTrue(Double.compare(firstValue, secondValue) == 0, "same seed must be deterministic");
        assertTrue(anyDifferentSample(first, otherSeed), "different seed should change noisy coastline");
    }

    private static boolean anyDifferentSample(IslandMask first, IslandMask second) {
        for (int x = -160; x <= 160; x += 16) {
            for (int z = -160; z <= 160; z += 16) {
                if (Double.compare(first.sample(x, z), second.sample(x, z)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertThrows(ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (IslandConfigException expected) {
            return;
        } catch (Exception exception) {
            throw new AssertionError(message + ": wrong exception " + exception, exception);
        }
        throw new AssertionError(message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
