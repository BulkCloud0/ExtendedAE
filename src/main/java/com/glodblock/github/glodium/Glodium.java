package com.glodblock.github.glodium;

import net.minecraft.util.ResourceLocation;

/**
 * Minimal Glodium compatibility surface embedded for the 1.16.5 backport.
 * ExtendedAE only needs deterministic namespaced resource identifiers here.
 */
public final class Glodium {
    private Glodium() {
    }

    public static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
