package org.wodichka.worldgen_editor.neoforge.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.wodichka.worldgen_editor.world.*;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseGeneratorMixin {
    @Shadow @Final @Mutable private Holder<NoiseGeneratorSettings> settings;
    @Inject(method="<init>",at=@At("RETURN"))
    private void wge$compose(BiomeSource source,Holder<NoiseGeneratorSettings> original,CallbackInfo ci){
        if(source instanceof IslandBiomeSource islands && islands.generationVersion()>=3)
            settings=Holder.direct(IslandNoiseComposition.compose(original.value()));
    }
}
