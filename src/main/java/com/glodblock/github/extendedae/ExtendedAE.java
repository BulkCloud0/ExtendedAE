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
import net.minecraft.util.ResourceLocation;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Embedded ExtendedAE bootstrap used by ExpansionAE.
 *
 * This class is intentionally not annotated with @Mod. ExpansionAE owns the
 * single Forge mod container and invokes this bootstrap so all original
 * ExtendedAE mechanics live inside the unified jar.
 */
public class ExtendedAE {

    public static final String MODID = "expatternprovider";
    public static final Logger LOGGER = LogManager.getLogger("ExpansionAE/ExtendedAE");
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

        // CreativeModeTab is not a Forge registry in 1.16.5. The backport
        // exposes the tab through the legacy ItemGroup path instead of the
        // 1.20 RegisterEvent/Registries.CREATIVE_MODE_TAB flow.
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
