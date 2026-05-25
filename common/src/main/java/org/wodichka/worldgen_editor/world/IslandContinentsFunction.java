package org.wodichka.worldgen_editor.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.wodichka.worldgen_editor.Worldgen_editor;

public final class IslandContinentsFunction implements DensityFunction {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Worldgen_editor.MOD_ID, "island_continents");
    public static final MapCodec<IslandContinentsFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(IslandContinentsFunction::delegate)
    ).apply(instance, IslandContinentsFunction::new));
    public static final KeyDispatchDataCodec<IslandContinentsFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static boolean registered;

    private final DensityFunction delegate;

    public IslandContinentsFunction(DensityFunction delegate) {
        this.delegate = delegate;
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
        return shape(context, delegate.compute(context));
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
        return visitor.apply(new IslandContinentsFunction(mapped));
    }

    @Override
    public double minValue() {
        return Math.min(delegate.minValue(), -1.1D);
    }

    @Override
    public double maxValue() {
        return Math.max(delegate.maxValue(), 0.55D);
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
            double ocean = lerp(-0.82D, -0.46D, smoothstep(IslandTerrainHooks.DEEP_OCEAN_MASK, IslandTerrainHooks.FULL_OCEAN_MASK, island));
            return Math.min(vanilla, ocean);
        }

        double shore = smoothstep(IslandTerrainHooks.FULL_OCEAN_MASK, IslandTerrainHooks.LAND_MASK, island);
        double inner = smoothstep(IslandTerrainHooks.LAND_MASK, 0.92D, island);
        double islandContinents = lerp(-0.22D, 0.34D, inner);
        double softened = lerp(-0.46D, Math.max(vanilla, islandContinents), shore);
        return lerp(vanilla, softened, 0.82D);
    }

    private DensityFunction delegate() {
        return delegate;
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
}
