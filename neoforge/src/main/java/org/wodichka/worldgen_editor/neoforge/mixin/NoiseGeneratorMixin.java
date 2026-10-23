package org.wodichka.worldgen_editor.neoforge.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.wodichka.worldgen_editor.world.*;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseGeneratorMixin implements OriginalNoiseSettings {
    @Shadow @Final @Mutable private Holder<NoiseGeneratorSettings> settings;
    @Unique private Holder<NoiseGeneratorSettings> wge$original;
    @Inject(method="<init>",at=@At("RETURN"))
    private void wge$compose(BiomeSource source,Holder<NoiseGeneratorSettings> original,CallbackInfo ci){
        wge$original=original;
        if(source instanceof IslandBiomeSource islands && islands.generationVersion()>=3)
            settings=Holder.direct(IslandNoiseComposition.compose(original.value()));
    }
    public Holder<NoiseGeneratorSettings> wge$originalSettings(){return wge$original;}
    @Inject(method="codec",at=@At("HEAD"),cancellable=true)
    private void wge$codec(CallbackInfoReturnable<MapCodec<? extends ChunkGenerator>> cir){
        if(((NoiseBasedChunkGenerator)(Object)this).getBiomeSource() instanceof IslandBiomeSource islands && islands.generationVersion()>=3)
            cir.setReturnValue(IslandGeneratorCodec.CODEC);
    }
}
