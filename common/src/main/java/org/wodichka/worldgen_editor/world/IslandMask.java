package org.wodichka.worldgen_editor.world;

import org.wodichka.worldgen_editor.config.IslandConfig;
import org.wodichka.worldgen_editor.config.IslandEntry;
import org.wodichka.worldgen_editor.config.IslandNoise;

import java.util.ArrayList;
import java.util.List;

public final class IslandMask {
    private final List<CompiledIsland> islands;

    public IslandMask(IslandConfig config, long worldSeed) {
        List<CompiledIsland> compiled = new ArrayList<>();
        for (IslandEntry entry : config.entries()) {
            compiled.add(new CompiledIsland(entry, worldSeed));
        }
        this.islands = List.copyOf(compiled);
    }

    public boolean isEmpty() {
        return islands.isEmpty();
    }

    public double sample(double blockX, double blockZ) {
        double union = 0.0D;
        double additive = 0.0D;

        for (CompiledIsland island : islands) {
            double value = island.sample(blockX, blockZ);
            if (island.overlap) {
                additive += value;
            } else {
                union = Math.max(union, value);
            }
        }

        return clamp01(Math.max(union, additive));
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

    private static final class CompiledIsland {
        private final boolean overlap;
        private final double centerX;
        private final double centerZ;
        private final double xDivisor;
        private final double zDivisor;
        private final double cos;
        private final double sin;
        private final double multiplier;
        private final double noiseStrength;
        private final double edgeWidth;
        private final IslandNoise noise;
        private final long seed;

        private CompiledIsland(IslandEntry entry, long worldSeed) {
            this.overlap = entry.overlap();
            this.centerX = entry.centerX();
            this.centerZ = entry.centerZ();
            this.xDivisor = entry.xDivisor();
            this.zDivisor = entry.zDivisor();
            double radians = Math.toRadians(entry.rotationDegrees());
            this.cos = Math.cos(radians);
            this.sin = Math.sin(radians);
            this.multiplier = entry.multiplier();
            this.noiseStrength = entry.noiseStrength();
            this.edgeWidth = entry.edgeWidth();
            this.noise = entry.noise();
            this.seed = mix(worldSeed, stableStringSeed(entry.noise().seed()));
        }

        private double sample(double blockX, double blockZ) {
            double dx = blockX - centerX;
            double dz = blockZ - centerZ;
            double rotatedX = dx * cos - dz * sin;
            double rotatedZ = dx * sin + dz * cos;
            double normalizedX = rotatedX / xDivisor;
            double normalizedZ = rotatedZ / zDivisor;
            double distance = Math.sqrt(normalizedX * normalizedX + normalizedZ * normalizedZ);
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
}
