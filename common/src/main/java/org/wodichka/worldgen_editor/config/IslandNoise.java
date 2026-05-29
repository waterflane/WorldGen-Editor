package org.wodichka.worldgen_editor.config;

import java.util.List;

public record IslandNoise(List<Double> amplitudes, String seed, int firstOctave, double scale) {
    public IslandNoise {
        amplitudes = List.copyOf(amplitudes);
    }
}
