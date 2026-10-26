package org.wodichka.worldgen_editor.verification.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Only loaded by the isolated ocean verification run; production jars exclude this source set. */
@Mixin(GameTestServer.class)
abstract class GameTestSeedMixin {
    @ModifyArg(method="<clinit>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/WorldOptions;<init>(JZZ)V"),index=0)
    private static long oceanSeed(long original) { return Long.getLong("wge.verificationSeed",2L); }
}
