package org.wodichka.worldgen_editor.neoforge;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lets NeoForge bootstrap its test server; the preview stops before levels or tests run. */
@GameTestHolder("worldgen_editor")
@PrefixGameTestTemplate(false)
public final class PreviewRuntimeTest {
    @GameTest(template="neoforge:empty3x3",timeoutTicks=2147483647)
    public static void runtimeHost(GameTestHelper helper){}
}
