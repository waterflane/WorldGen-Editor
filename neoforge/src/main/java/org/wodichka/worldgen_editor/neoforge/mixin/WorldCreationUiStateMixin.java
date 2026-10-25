package org.wodichka.worldgen_editor.neoforge.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.wodichka.worldgen_editor.neoforge.client.ClientPresets;
import java.util.List;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Shadow private WorldCreationUiState.WorldTypeEntry worldType;
    @Unique private String wge$selected;
    @Inject(method={"getNormalPresetList","getAltPresetList"},at=@At("RETURN"),cancellable=true)
    private void wge$livePresets(CallbackInfoReturnable<List<WorldCreationUiState.WorldTypeEntry>> cir){
        cir.setReturnValue(ClientPresets.expand((WorldCreationUiState)(Object)this,cir.getReturnValue()));
    }
    @Inject(method="updatePresetLists",at=@At("HEAD"))
    private void wge$rememberSelection(CallbackInfo ci){var selected=ClientPresets.selection(worldType);wge$selected=selected==null?null:selected.id();}
    @Inject(method="updatePresetLists",at=@At("RETURN"))
    private void wge$restoreSelection(CallbackInfo ci){
        if(wge$selected!=null){var entry=ClientPresets.find((WorldCreationUiState)(Object)this,wge$selected);if(entry!=null)((WorldCreationUiState)(Object)this).setWorldType(entry);}
    }
}
