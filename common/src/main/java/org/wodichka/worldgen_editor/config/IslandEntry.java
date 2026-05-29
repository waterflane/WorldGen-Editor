package org.wodichka.worldgen_editor.config;

import java.util.List;

public record IslandEntry(
        IslandEntryType type,
        String name,
        boolean overlap,
        List<String> excludedBiomes,
        IslandTemperature temperature,
        int biomePatchSize,
        IslandNoise noise,
        double xDivisor,
        double zDivisor,
        double rotationDegrees,
        double shapePower,
        double multiplier,
        double centerX,
        double centerZ,
        double noiseStrength,
        double edgeWidth,
        int count,
        double minRadius,
        double maxRadius,
        double spread,
        double spacing,
        double minStretch,
        double maxStretch,
        double minShapePower,
        double maxShapePower
) {
    public IslandEntry {
        excludedBiomes = List.copyOf(excludedBiomes);
    }
}
