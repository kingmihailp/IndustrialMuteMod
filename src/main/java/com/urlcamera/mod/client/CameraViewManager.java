package com.urlcamera.mod.client;

import com.urlcamera.mod.entity.CameraEntity;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public class CameraViewManager {

    private static UUID activeCameraUUID = null;

    public static void enterCameraView(CameraEntity camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        activeCameraUUID = camera.getUUID();
        mc.setCameraEntity(camera);
    }

    public static void exitCameraView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        activeCameraUUID = null;
        mc.setCameraEntity(mc.player);
    }

    public static UUID getActiveCameraId() {
        return activeCameraUUID;
    }
}
