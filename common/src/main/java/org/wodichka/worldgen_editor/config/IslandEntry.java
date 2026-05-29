package org.wodichka.worldgen_editor.config;

public record IslandEntry(
        IslandEntryType type,
        String name,
        boolean overlap,
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
}
