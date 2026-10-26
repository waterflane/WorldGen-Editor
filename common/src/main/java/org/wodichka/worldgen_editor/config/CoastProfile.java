package org.wodichka.worldgen_editor.config;

/** Absolute world-block widths; omitted profiles retain legacy generation. */
public record CoastProfile(int beachWidthBlocks, int shallowOceanWidthBlocks) {
    public CoastProfile {
        if(beachWidthBlocks<0||beachWidthBlocks>4096||shallowOceanWidthBlocks<0||shallowOceanWidthBlocks>4096)
            throw new IllegalArgumentException("Coast widths must be integers between 0 and 4096 blocks");
    }
}
