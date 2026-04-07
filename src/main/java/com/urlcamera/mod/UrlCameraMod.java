package com.urlcamera.mod;

import com.urlcamera.mod.init.ModCreativeTabs;
import com.urlcamera.mod.init.ModEntities;
import com.urlcamera.mod.init.ModItems;
import com.urlcamera.mod.server.CameraWebServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("urlcamera")
public class UrlCameraMod {

    public static final String MOD_ID = "urlcamera";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public UrlCameraMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[URL Camera] Starting web server...");
            CameraWebServer.start();
            LOGGER.info("[URL Camera] Web server started on port 8765");
        });
    }
}
