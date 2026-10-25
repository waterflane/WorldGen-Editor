package org.wodichka.worldgen_editor.neoforge.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.wodichka.worldgen_editor.neoforge.client.ClientPresets;

@Mixin(WorldCreationUiState.WorldTypeEntry.class)
public abstract class WorldTypeEntryMixin {
    @Inject(method="describePreset",at=@At("HEAD"),cancellable=true)
    private void wge$displayName(CallbackInfoReturnable<Component> cir){
        var selected=ClientPresets.selection((WorldCreationUiState.WorldTypeEntry)(Object)this);
        if(selected!=null)cir.setReturnValue(Component.literal(selected.label()));
    }
}
