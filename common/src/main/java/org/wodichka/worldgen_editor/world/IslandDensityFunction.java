package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.wodichka.worldgen_editor.Worldgen_editor;

public final class IslandDensityFunction implements DensityFunction {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Worldgen_editor.MOD_ID, "island_final_density");
    public static final MapCodec<IslandDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(IslandDensityFunction::delegate),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(IslandDensityFunction::seaLevel)
    ).apply(instance, IslandDensityFunction::new));
    public static final KeyDispatchDataCodec<IslandDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final double OCEAN_FLOOR_OFFSET = 28.0D;
    private static boolean registered;

    private final DensityFunction delegate;
    private final int seaLevel;

    public IslandDensityFunction(DensityFunction delegate, int seaLevel) {
        this.delegate = delegate;
        this.seaLevel = seaLevel;
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, ID, MAP_CODEC);
    }

    @Override
    public double compute(FunctionContext context) {
        double vanilla = delegate.compute(context);
        return shape(context, vanilla);
    }

    @Override
    public void fillArray(double[] densities, ContextProvider contextProvider) {
        delegate.fillArray(densities, contextProvider);
        for (int index = 0; index < densities.length; index++) {
            densities[index] = shape(contextProvider.forIndex(index), densities[index]);
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        DensityFunction mapped = delegate.mapAll(visitor);
        return visitor.apply(new IslandDensityFunction(mapped, seaLevel));
    }

    @Override
    public double minValue() {
        return Math.min(delegate.minValue() - 0.75D, -1.5D);
    }

    @Override
    public double maxValue() {
        return Math.max(delegate.maxValue() + 0.5D, 1.5D);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private double shape(FunctionContext context, double vanilla) {
        IslandMask mask = IslandWorldState.mask();
        if (mask.isEmpty()) {
            return vanilla;
        }

        double island = mask.sample(context.blockX(), context.blockZ());
        if (island <= IslandTerrainHooks.FULL_OCEAN_MASK) {
            return oceanDensity(context.blockY());
        }

        double shore = smoothstep(IslandTerrainHooks.FULL_OCEAN_MASK, IslandTerrainHooks.LAND_MASK, island);
        double ocean = oceanDensity(context.blockY());
        double land = landDensity(vanilla, context.blockY(), island, shore);
        return lerp(ocean, land, shore);
    }

    private double landDensity(double vanilla, int y, double island, double shore) {
        double inner = smoothstep(IslandTerrainHooks.LAND_MASK, 0.94D, island);
        double belowSeaSupport = clamp((seaLevel + 2.0D - y) / 28.0D, -0.08D, 0.22D);
        double support = belowSeaSupport * (0.08D + inner * 0.12D);
        double shoreCut = (1.0D - shore) * 0.10D;
        return vanilla + support - shoreCut;
    }

    private double oceanDensity(int y) {
        double oceanFloor = seaLevel - OCEAN_FLOOR_OFFSET;
        if (y <= oceanFloor) {
            return 0.42D;
        }
        if (y < seaLevel - 4.0D) {
            double delta = (y - oceanFloor) / (seaLevel - 4.0D - oceanFloor);
            return lerp(0.30D, -0.36D, delta);
        }
        return -0.80D - Math.max(0.0D, y - seaLevel) * 0.018D;
    }

    private DensityFunction delegate() {
        return delegate;
    }

    private int seaLevel() {
        return seaLevel;
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
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

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
