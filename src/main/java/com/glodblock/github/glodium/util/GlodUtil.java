package com.glodblock.github.glodium.util;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Small 1.16.5 compatibility implementation of Glodium utility methods. */
public final class GlodUtil {
    private static final Map<Class<?>, TileEntityType<? extends TileEntity>> TILE_CACHE =
            new IdentityHashMap<Class<?>, TileEntityType<? extends TileEntity>>();

    private GlodUtil() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends TileEntity> TileEntityType<T> getTileType(
            Class<T> clazz, Supplier<? extends T> supplier, Block block) {
        if (block == null) {
            return (TileEntityType<T>) TILE_CACHE.get(clazz);
        }
        TileEntityType<? extends TileEntity> existing = TILE_CACHE.get(clazz);
        if (existing == null) {
            existing = TileEntityType.Builder.of(supplier, block).build(null);
            TILE_CACHE.put(clazz, existing);
        }
        return (TileEntityType<T>) existing;
    }

    @SuppressWarnings("unchecked")
    public static <T extends TileEntity> TileEntityType<T> getTileType(Class<T> clazz) {
        TileEntityType<? extends TileEntity> type = TILE_CACHE.get(clazz);
        if (type == null) {
            throw new IllegalArgumentException(clazz.getName() + " is not a registered tile entity");
        }
        return (TileEntityType<T>) type;
    }

    public static boolean checkInvalidRL(String rl, IForgeRegistry<?> registry) {
        return checkInvalidRL(new ResourceLocation(rl), registry);
    }

    public static boolean checkInvalidRL(ResourceLocation rl, IForgeRegistry<?> registry) {
        return registry.containsKey(rl);
    }

    public static double clamp(double num, double floor, double ceil) {
        return Math.min(ceil, Math.max(floor, num));
    }

    public static Dist side() {
        return Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER
                ? Dist.DEDICATED_SERVER
                : Dist.CLIENT;
    }
}
