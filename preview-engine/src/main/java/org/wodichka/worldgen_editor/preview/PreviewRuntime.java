package org.wodichka.worldgen_editor.preview;

import com.google.gson.*;

/** Loaded resources stay alive; every create call owns independent generation state. */
public interface PreviewRuntime {
    PreviewWorld create(JsonElement config,long seed,int version,String preset)throws Exception;
    JsonObject catalog();
    String fingerprint();
}
