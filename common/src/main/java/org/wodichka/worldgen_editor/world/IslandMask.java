package org.wodichka.worldgen_editor.world;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wodichka.worldgen_editor.Worldgen_editor;
import org.wodichka.worldgen_editor.config.IslandConfig;
import org.wodichka.worldgen_editor.config.IslandEntry;
import org.wodichka.worldgen_editor.config.IslandEntryType;
import org.wodichka.worldgen_editor.config.IslandNoise;
import org.wodichka.worldgen_editor.config.IslandTemperature;

import java.util.ArrayList;
import java.util.List;

public final class IslandMask {
    private static final Logger LOGGER = LoggerFactory.getLogger(Worldgen_editor.MOD_ID);
    private static final int ARCHIPELAGO_MAX_ATTEMPTS_PER_ISLAND = 64;

    private final List<CompiledIsland> landShapes;
    private final List<CompiledIsland> oceanShapes;
    private final List<CompiledParent> archipelagoParents;

    public IslandMask(IslandConfig config, long worldSeed) {
        List<CompiledIsland> land = new ArrayList<>();
        List<CompiledIsland> ocean = new ArrayList<>();
        List<CompiledParent> parents = new ArrayList<>();
        for (IslandEntry entry : config.entries()) {
            if (entry.type() == IslandEntryType.OCEAN) {
                ocean.add(new CompiledIsland(entry, sourceInfo(entry, worldSeed, null), worldSeed));
            } else if (entry.type() == IslandEntryType.ARCHIPELAGO) {
                parents.add(new CompiledParent(entry, sourceInfo(entry, 0L, entry.name())));
                compileArchipelago(entry, land);
            } else {
                land.add(new CompiledIsland(entry, sourceInfo(entry, worldSeed, null), worldSeed));
            }
        }
        this.landShapes = List.copyOf(land);
        this.oceanShapes = List.copyOf(ocean);
        this.archipelagoParents = List.copyOf(parents);
    }

    public boolean isEmpty() {
        return landShapes.isEmpty() && oceanShapes.isEmpty() && archipelagoParents.isEmpty();
    }

    public double sample(double blockX, double blockZ) {
        return sampleInfo(blockX, blockZ).value();
    }

    public SampleInfo sampleInfo(double blockX, double blockZ) {
        double union = 0.0D;
        double additive = 0.0D;
        double strongestLand = 0.0D;
        SourceInfo landSource = null;

        for (CompiledIsland island : landShapes) {
            double value = island.sample(blockX, blockZ);
            if (island.overlap) {
                additive += value;
            } else {
                union = Math.max(union, value);
            }
            if (value > strongestLand) {
                strongestLand = value;
                landSource = island.sourceInfo;
            }
        }

        double land = clamp01(Math.max(union, additive));
        double ocean = 0.0D;
        SourceInfo oceanSource = null;
        for (CompiledIsland oceanShape : oceanShapes) {
            double value = oceanShape.sample(blockX, blockZ);
            if (value > ocean) {
                ocean = value;
                oceanSource = oceanShape.sourceInfo;
            }
        }

        double parentValue = 0.0D;
        SourceInfo parentSource = null;
        for (CompiledParent parent : archipelagoParents) {
            double value = parent.sample(blockX, blockZ);
            if (value > parentValue) {
                parentValue = value;
                parentSource = parent.sourceInfo;
            }
        }

        return new SampleInfo(clamp01(Math.min(land, 1.0D - ocean)), landSource, oceanSource, parentSource);
    }

    public record SourceInfo(String name, IslandEntryType type, String parentName, long climateSeed, List<String> excludedBiomes,
                             IslandTemperature temperature, int biomePatchSize) {
        public SourceInfo {
            excludedBiomes = List.copyOf(excludedBiomes);
        }
    }

    public record SampleInfo(double value, SourceInfo landSource, SourceInfo oceanSource, SourceInfo archipelagoSource) {
    }

    private static void compileArchipelago(IslandEntry entry, List<CompiledIsland> land) {
        long baseSeed = stableStringSeed(entry.noise().seed());
        SourceInfo sourceInfo = sourceInfo(entry, 0L, entry.name());
        List<PlacedChild> placed = new ArrayList<>();

        for (int index = 0; index < entry.count(); index++) {
            boolean accepted = false;
            for (int attempt = 0; attempt < ARCHIPELAGO_MAX_ATTEMPTS_PER_ISLAND; attempt++) {
                long attemptSeed = mix(baseSeed, index * 4099L + attempt * 131L);
                double angle = randomUnit(attemptSeed, 1L) * Math.PI * 2.0D;
                double distance = Math.sqrt(randomUnit(attemptSeed, 2L)) * entry.spread();
                double localX = Math.cos(angle) * distance * entry.xDivisor();
                double localZ = Math.sin(angle) * distance * entry.zDivisor();
                double radians = Math.toRadians(entry.rotationDegrees());
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                double centerX = entry.centerX() + localX * cos - localZ * sin;
                double centerZ = entry.centerZ() + localX * sin + localZ * cos;
                double radius = lerp(entry.minRadius(), entry.maxRadius(), randomUnit(attemptSeed, 3L));
                double stretch = lerp(entry.minStretch(), entry.maxStretch(), randomUnit(attemptSeed, 4L));
                boolean stretchX = randomUnit(attemptSeed, 5L) < 0.5D;
                double radiusX = stretchX ? radius * stretch : radius;
                double radiusZ = stretchX ? radius : radius * stretch;
                double rotation = randomUnit(attemptSeed, 6L) * 360.0D;
                double shapePower = lerp(entry.minShapePower(), entry.maxShapePower(), randomUnit(attemptSeed, 7L));
                double approximateRadius = Math.max(radiusX, radiusZ);

                if (!hasSpacingConflict(placed, centerX, centerZ, approximateRadius, entry.spacing())) {
                    String childName = entry.name() + " " + (index + 1);
                    IslandNoise childNoise = new IslandNoise(
                            entry.noise().amplitudes(),
                            entry.noise().seed() + "_" + (index + 1),
                            entry.noise().firstOctave(),
                            entry.noise().scale()
                    );
                    land.add(new CompiledIsland(
                            entry.overlap(),
                            sourceInfo,
                            centerX,
                            centerZ,
                            radiusX,
                            radiusZ,
                            rotation,
                            shapePower,
                            entry.multiplier(),
                            entry.noiseStrength(),
                            entry.edgeWidth(),
                            childNoise,
                            0L
                    ));
                    placed.add(new PlacedChild(centerX, centerZ, approximateRadius));
                    accepted = true;
                    break;
                }
            }

            if (!accepted) {
                LOGGER.warn("Could only place {} of {} islands for archipelago '{}'", placed.size(), entry.count(), entry.name());
                return;
            }
        }
    }

    private static SourceInfo sourceInfo(IslandEntry entry, long worldSeed, String parentName) {
        return new SourceInfo(
                entry.name(),
                entry.type(),
                parentName,
                mix(worldSeed, stableStringSeed(entry.noise().seed())),
                entry.excludedBiomes(),
                entry.temperature(),
                entry.biomePatchSize()
        );
    }

    private static boolean hasSpacingConflict(List<PlacedChild> placed, double centerX, double centerZ, double radius, double spacing) {
        for (PlacedChild child : placed) {
            double dx = centerX - child.centerX;
            double dz = centerZ - child.centerZ;
            double minDistance = (radius + child.radius) * spacing;
            if (dx * dx + dz * dz < minDistance * minDistance) {
                return true;
            }
        }
        return false;
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private record PlacedChild(double centerX, double centerZ, double radius) {
    }

    private static final class CompiledParent {
        private final SourceInfo sourceInfo;
        private final double centerX;
        private final double centerZ;
        private final double xDivisor;
        private final double zDivisor;
        private final double cos;
        private final double sin;
        private final double shapePower;
        private final double edgeWidth;

        private CompiledParent(IslandEntry entry, SourceInfo sourceInfo) {
            this.sourceInfo = sourceInfo;
            this.centerX = entry.centerX();
            this.centerZ = entry.centerZ();
            this.xDivisor = entry.xDivisor();
            this.zDivisor = entry.zDivisor();
            double radians = Math.toRadians(entry.rotationDegrees());
            this.cos = Math.cos(radians);
            this.sin = Math.sin(radians);
            this.shapePower = entry.shapePower();
            this.edgeWidth = entry.edgeWidth();
        }

        private double sample(double blockX, double blockZ) {
            double dx = blockX - centerX;
            double dz = blockZ - centerZ;
            double rotatedX = dx * cos - dz * sin;
            double rotatedZ = dx * sin + dz * cos;
            double normalizedX = rotatedX / xDivisor;
            double normalizedZ = rotatedZ / zDivisor;
            double field = 1.0D - superellipseDistance(normalizedX, normalizedZ, shapePower);
            return smoothstep(-edgeWidth, edgeWidth, field);
        }
    }

    private static final class CompiledIsland {
        private final boolean overlap;
        private final SourceInfo sourceInfo;
        private final double centerX;
        private final double centerZ;
        private final double xDivisor;
        private final double zDivisor;
        private final double cos;
        private final double sin;
        private final double multiplier;
        private final double shapePower;
        private final double noiseStrength;
        private final double edgeWidth;
        private final IslandNoise noise;
        private final long seed;

        private CompiledIsland(IslandEntry entry, SourceInfo sourceInfo, long worldSeed) {
            this(
                    entry.overlap(),
                    sourceInfo,
                    entry.centerX(),
                    entry.centerZ(),
                    entry.xDivisor(),
                    entry.zDivisor(),
                    entry.rotationDegrees(),
                    entry.shapePower(),
                    entry.multiplier(),
                    entry.noiseStrength(),
                    entry.edgeWidth(),
                    entry.noise(),
                    worldSeed
            );
        }

        private CompiledIsland(
                boolean overlap,
                SourceInfo sourceInfo,
                double centerX,
                double centerZ,
                double xDivisor,
                double zDivisor,
                double rotationDegrees,
                double shapePower,
                double multiplier,
                double noiseStrength,
                double edgeWidth,
                IslandNoise noise,
                long worldSeed
        ) {
            this.overlap = overlap;
            this.sourceInfo = sourceInfo;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.xDivisor = xDivisor;
            this.zDivisor = zDivisor;
            double radians = Math.toRadians(rotationDegrees);
            this.cos = Math.cos(radians);
            this.sin = Math.sin(radians);
            this.shapePower = shapePower;
            this.multiplier = multiplier;
            this.noiseStrength = noiseStrength;
            this.edgeWidth = edgeWidth;
            this.noise = noise;
            this.seed = mix(worldSeed, stableStringSeed(noise.seed()));
        }

        private double sample(double blockX, double blockZ) {
            double dx = blockX - centerX;
            double dz = blockZ - centerZ;
            double rotatedX = dx * cos - dz * sin;
            double rotatedZ = dx * sin + dz * cos;
            double normalizedX = rotatedX / xDivisor;
            double normalizedZ = rotatedZ / zDivisor;
            double distance = superellipseDistance(normalizedX, normalizedZ, shapePower);
            double edgeNoise = normalizedNoise(normalizedX * noise.scale(), normalizedZ * noise.scale()) * noiseStrength;
            double field = multiplier * (1.0D + edgeNoise) - distance;

            return smoothstep(-edgeWidth, edgeWidth, field);
        }

        private double normalizedNoise(double x, double z) {
            double sum = 0.0D;
            double amplitudeSum = 0.0D;
            double frequency = Math.pow(2.0D, noise.firstOctave());

            for (double amplitude : noise.amplitudes()) {
                sum += interpolatedValueNoise(x * frequency, z * frequency) * amplitude;
                amplitudeSum += amplitude;
                frequency *= 2.0D;
            }

            if (amplitudeSum == 0.0D) {
                return 0.0D;
            }
            return sum / amplitudeSum;
        }

        private double interpolatedValueNoise(double x, double z) {
            int x0 = fastFloor(x);
            int z0 = fastFloor(z);
            double tx = smoothFraction(x - x0);
            double tz = smoothFraction(z - z0);

            double a = valueNoise(x0, z0);
            double b = valueNoise(x0 + 1, z0);
            double c = valueNoise(x0, z0 + 1);
            double d = valueNoise(x0 + 1, z0 + 1);
            double ab = lerp(a, b, tx);
            double cd = lerp(c, d, tx);
            return lerp(ab, cd, tz);
        }

        private double valueNoise(int x, int z) {
            long mixed = seed;
            mixed = mix(mixed, x * 341873128712L);
            mixed = mix(mixed, z * 132897987541L);
            return ((mixed >>> 11) * 0x1.0p-53D) * 2.0D - 1.0D;
        }

        private static int fastFloor(double value) {
            int floor = (int) value;
            return value < floor ? floor - 1 : floor;
        }

        private static double smoothFraction(double value) {
            return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
        }

        private static double smoothstep(double min, double max, double value) {
            double x = clamp01((value - min) / (max - min));
            return x * x * (3.0D - 2.0D * x);
        }

        private static double lerp(double start, double end, double delta) {
            return start + (end - start) * delta;
        }
    }

    private static double superellipseDistance(double x, double z, double power) {
        return Math.pow(Math.pow(Math.abs(x), power) + Math.pow(Math.abs(z), power), 1.0D / power);
    }

    private static double smoothstep(double min, double max, double value) {
        double x = clamp01((value - min) / (max - min));
        return x * x * (3.0D - 2.0D * x);
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    private static double randomUnit(long seed, long salt) {
        long mixed = mix(seed, salt * 0x9e3779b97f4a7c15L);
        return (mixed >>> 11) * 0x1.0p-53D;
    }

    private static long stableStringSeed(String value) {
        long hash = 1125899906842597L;
        for (int index = 0; index < value.length(); index++) {
            hash = 31L * hash + value.charAt(index);
        }
        return hash;
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
