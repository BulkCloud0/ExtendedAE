package com.glodblock.github.extendedae;

import com.glodblock.github.extendedae.client.ClientRegistryHandler;
import com.glodblock.github.extendedae.client.hotkey.PatternHotKey;
import com.glodblock.github.extendedae.common.EAERegistryHandler;
import com.glodblock.github.extendedae.common.EPPItemAndBlock;
import com.glodblock.github.extendedae.common.hooks.CutterHook;
import com.glodblock.github.extendedae.common.me.taglist.TagPriorityList;
import com.glodblock.github.extendedae.config.EPPConfig;
import com.glodblock.github.extendedae.container.ContainerExCraftingTerminal;
import com.glodblock.github.extendedae.network.EPPNetworkHandler;
import com.glodblock.github.extendedae.xmod.LoadList;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * Embedded ExtendedAE bootstrap used by ExpansionAE.
 *
 * <p>The legacy registry/resource namespace remains {@code expatternprovider}
 * so existing recipes, models and registry identifiers do not need to be
 * renamed while the 1.16.5 port is completed. This class is deliberately not
 * a Forge {@code @Mod} entrypoint; ExpansionAE owns the single mod container.
 */
public class ExtendedAE {

    public static final String MODID = "expatternprovider";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static ExtendedAE INSTANCE;

    public static ExtendedAE bootstrap() {
        if (INSTANCE == null) {
            new ExtendedAE();
        }
        return INSTANCE;
    }

    public ExtendedAE() {
        if (INSTANCE != null) {
            throw new IllegalStateException("ExtendedAE is already embedded");
        }
        INSTANCE = this;
        LoadList.init();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EPPConfig.SPEC);
        EPPItemAndBlock.init(EAERegistryHandler.INSTANCE);
        bus.register(EAERegistryHandler.INSTANCE);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> bus.register(ClientRegistryHandler.INSTANCE));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientRegistryHandler.INSTANCE::registerAEHotkey);
        bus.addListener(this::commonSetup);
        bus.addListener(this::clientSetup);
        bus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey() == Registries.CREATIVE_MODE_TAB) {
                EAERegistryHandler.INSTANCE.registerTab(e.getVanillaRegistry());
            }
        });
        MinecraftForge.EVENT_BUS.register(CutterHook.INSTANCE);
        MinecraftForge.EVENT_BUS.addListener(this::onTagUpdate);
    }

    public void commonSetup(FMLCommonSetupEvent event) {
        EAERegistryHandler.INSTANCE.onInit();
        EPPNetworkHandler.INSTANCE.init();
    }

    public void clientSetup(FMLClientSetupEvent event) {
        ClientRegistryHandler.INSTANCE.init();
        PatternHotKey.init();
    }

    public void onTagUpdate(TagsUpdatedEvent event) {
        TagPriorityList.reset();
        ContainerExCraftingTerminal.initXPFluid();
    }

    public static ResourceLocation id(String id) {
        return new ResourceLocation(MODID, id);
    }
}
