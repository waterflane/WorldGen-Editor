package org.wodichka.worldgen_editor.world;

import net.minecraft.world.level.levelgen.*;

/** Derive an isolated WGE graph from the loaded generator, without editing registry entries. */
public final class IslandNoiseComposition {
    private IslandNoiseComposition(){}
    public static NoiseGeneratorSettings compose(NoiseGeneratorSettings original){
        var router=original.noiseRouter();
        if(router.finalDensity() instanceof IslandCoastFunction)return original;
        DensityFunction target=router.continents().mapAll(f->f);
        while(target instanceof DensityFunctions.HolderHolder holder)target=holder.function().value();
        // mapAll expands references into direct holders; compare the canonical subtree, not registry identity.
        final DensityFunction continents=target;
        if(continents.minValue()==continents.maxValue())throw new IllegalArgumentException("Unsupported constant continentalness graph for WGE version 3");
        int[] replaced={0};
        var mapped=router.mapAll(function->{
            if(function.equals(continents)){replaced[0]++;return new IslandContinentsFunction(function);}
            return function;
        });
        if(replaced[0]==0)throw new IllegalArgumentException("Could not locate the effective continentalness graph for WGE");
        var result=new NoiseRouter(mapped.barrierNoise(),mapped.fluidLevelFloodednessNoise(),mapped.fluidLevelSpreadNoise(),mapped.lavaNoise(),
            mapped.temperature(),mapped.vegetation(),mapped.continents(),mapped.erosion(),mapped.depth(),mapped.ridges(),
            new IslandCoastFunction(mapped.initialDensityWithoutJaggedness(),original.seaLevel()),
            new IslandCoastFunction(mapped.finalDensity(),original.seaLevel()),mapped.veinToggle(),mapped.veinRidged(),mapped.veinGap());
        return new NoiseGeneratorSettings(original.noiseSettings(),original.defaultBlock(),original.defaultFluid(),result,
            original.surfaceRule(),original.spawnTarget(),original.seaLevel(),original.disableMobGeneration(),
            original.isAquifersEnabled(),original.oreVeinsEnabled(),original.useLegacyRandomSource());
    }
}
