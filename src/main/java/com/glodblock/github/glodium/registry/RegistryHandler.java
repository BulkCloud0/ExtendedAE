package com.glodblock.github.glodium.registry;

import com.glodblock.github.glodium.Glodium;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Forge 1.16.5 implementation of the small Glodium registry helper surface used
 * by ExtendedAE. It deliberately uses RegistryEvent instead of the 1.20
 * RegisterEvent API and keeps registration ordered by the native Forge phases.
 */
public class RegistryHandler {
    protected final String id;
    protected final List<Pair<String, Block>> blocks = new ArrayList<Pair<String, Block>>();
    protected final List<Pair<String, Item>> items = new ArrayList<Pair<String, Item>>();
    protected final List<Pair<String, TileEntityType<?>>> tiles = new ArrayList<Pair<String, TileEntityType<?>>>();
    protected final Map<String, Function<Block, Item>> itemBlocks = new HashMap<String, Function<Block, Item>>();

    public RegistryHandler(String modid) {
        this.id = modid;
    }

    public void block(String name, Block block) {
        this.blocks.add(Pair.of(name, block));
    }

    public void block(String name, Block block, Function<Block, Item> itemWrapper) {
        block(name, block);
        this.itemBlocks.put(name, itemWrapper);
    }

    public void item(String name, Item item) {
        this.items.add(Pair.of(name, item));
    }

    public void tile(String name, TileEntityType<?> type) {
        this.tiles.add(Pair.of(name, type));
    }

    @SubscribeEvent
    public final void runRegister(RegistryEvent.Register<?> event) {
        IForgeRegistry<?> registry = event.getRegistry();
        Class<?> type = registry.getRegistrySuperType();
        if (type == Block.class) {
            onRegisterBlocks(castRegistry(registry));
        } else if (type == Item.class) {
            onRegisterItems(castRegistry(registry));
        } else if (type == TileEntityType.class) {
            onRegisterTileEntities(castRegistry(registry));
        }
        register(event);
    }

    /** Hook for ExtendedAE-specific registry phases. */
    public void register(RegistryEvent.Register<?> event) {
    }

    protected void onRegisterBlocks(IForgeRegistry<Block> registry) {
        for (Pair<String, Block> entry : this.blocks) {
            registry.register(entry.getRight().setRegistryName(Glodium.id(this.id, entry.getLeft())));
        }
    }

    protected void onRegisterItems(IForgeRegistry<Item> registry) {
        for (Pair<String, Block> entry : this.blocks) {
            Item item;
            if (this.itemBlocks.containsKey(entry.getLeft())) {
                item = this.itemBlocks.get(entry.getLeft()).apply(entry.getRight());
            } else {
                item = new BlockItem(entry.getRight(), new Item.Properties());
            }
            registry.register(item.setRegistryName(Glodium.id(this.id, entry.getLeft())));
        }
        for (Pair<String, Item> entry : this.items) {
            registry.register(entry.getRight().setRegistryName(Glodium.id(this.id, entry.getLeft())));
        }
    }

    protected void onRegisterTileEntities(IForgeRegistry<TileEntityType<?>> registry) {
        for (Pair<String, TileEntityType<?>> entry : this.tiles) {
            registry.register(entry.getRight().setRegistryName(Glodium.id(this.id, entry.getLeft())));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> IForgeRegistry<T> castRegistry(IForgeRegistry<?> registry) {
        return (IForgeRegistry<T>) registry;
    }
}
