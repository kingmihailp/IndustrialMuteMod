package com.urlcamera.mod.client;

import com.urlcamera.mod.client.model.CameraEntityModel;
import com.urlcamera.mod.client.renderer.CameraEntityRenderer;
import com.urlcamera.mod.init.ModEntities;
import com.urlcamera.mod.init.ModModelLayers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "urlcamera", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEventHandlers {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAMERA.get(), CameraEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.CAMERA, CameraEntityModel::createBodyLayer);
    }
}
