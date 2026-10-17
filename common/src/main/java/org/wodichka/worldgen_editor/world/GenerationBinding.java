package org.wodichka.worldgen_editor.world;

import java.util.concurrent.atomic.AtomicReference;

/** A generation graph owns this handle; cloning a density function preserves the handle. */
public final class GenerationBinding {
    private final AtomicReference<GenerationContext> snapshot = new AtomicReference<>();
    private volatile GenerationBinding target;

    public GenerationContext get() {
        GenerationBinding current = target;
        return current == null ? snapshot.get() : current.get();
    }

    public void set(GenerationContext context) { snapshot.set(context); }

    public void attach(GenerationBinding binding) {
        if (binding != this) target = binding;
    }
}
