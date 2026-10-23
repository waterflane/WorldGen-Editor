package org.wodichka.worldgen_editor.world;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** Runtime graphs are derived; persistence retains the original registered settings. */
public interface OriginalNoiseSettings {
    Holder<NoiseGeneratorSettings> wge$originalSettings();
}
