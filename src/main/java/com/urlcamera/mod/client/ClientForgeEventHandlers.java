package com.urlcamera.mod.client;

import com.urlcamera.mod.entity.CameraEntity;
import com.urlcamera.mod.server.CameraWebServer;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "urlcamera", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEventHandlers {

    private static final Logger LOGGER = LogManager.getLogger("urlcamera/client-forge");
    private static long lastFrameCapture = 0;
    private static final long FRAME_INTERVAL_MS = 100;

    // Track known camera entity UUIDs for web server registration
    private static final List<UUID> registeredCameraIds = new ArrayList<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Collect currently loaded camera entities
        List<UUID> currentCameras = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CameraEntity camera) {
                UUID uuid = camera.getUUID();
                currentCameras.add(uuid);

                // Register or update camera in web server
                String mode = camera.getRotationMode() == CameraEntity.MODE_ROTATING ? "ROTATING" : "STATIONARY";
                CameraWebServer.registerCamera(uuid,
                        camera.getX(), camera.getY(), camera.getZ(), mode);

                if (!registeredCameraIds.contains(uuid)) {
                    registeredCameraIds.add(uuid);
                }
            }
        }

        // Unregister cameras that are no longer loaded
        List<UUID> toRemove = new ArrayList<>();
        for (UUID id : registeredCameraIds) {
            if (!currentCameras.contains(id)) {
                toRemove.add(id);
                CameraWebServer.unregisterCamera(id);
            }
        }
        registeredCameraIds.removeAll(toRemove);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        UUID activeCameraId = CameraViewManager.getActiveCameraId();

        if (activeCameraId == null) return;
        if (mc.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastFrameCapture < FRAME_INTERVAL_MS) return;
        lastFrameCapture = now;

        try {
            captureAndSendFrame(mc, activeCameraId);
        } catch (Exception e) {
            LOGGER.warn("[URL Camera] Frame capture error: " + e.getMessage());
        }
    }

    private static void captureAndSendFrame(Minecraft mc, UUID cameraId) {
        var renderTarget = mc.getMainRenderTarget();
        if (renderTarget == null) return;

        int texId = renderTarget.getColorTextureId();
        int width = renderTarget.width;
        int height = renderTarget.height;

        if (width <= 0 || height <= 0) return;

        try (NativeImage nativeImage = new NativeImage(width, height, false)) {
            // Bind the texture and download pixels from GPU
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(texId);
            nativeImage.downloadTexture(0, false);

            // Scale to 640x360 output resolution
            int targetWidth = 640;
            int targetHeight = 360;

            BufferedImage bufferedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

            float scaleX = (float) width / targetWidth;
            float scaleY = (float) height / targetHeight;

            for (int py = 0; py < targetHeight; py++) {
                // NativeImage Y is flipped relative to screen coordinates
                int srcY = height - 1 - (int) (py * scaleY);
                srcY = Math.max(0, Math.min(height - 1, srcY));

                for (int px = 0; px < targetWidth; px++) {
                    int srcX = (int) (px * scaleX);
                    srcX = Math.max(0, Math.min(width - 1, srcX));

                    // getPixelRGBA: bits 0-7 = Red, 8-15 = Green, 16-23 = Blue, 24-31 = Alpha
                    int rgba = nativeImage.getPixelRGBA(srcX, srcY);
                    int r = rgba & 0xFF;
                    int g = (rgba >> 8) & 0xFF;
                    int b = (rgba >> 16) & 0xFF;

                    bufferedImage.setRGB(px, py, (r << 16) | (g << 8) | b);
                }
            }

            // Encode as JPEG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "jpg", baos);
            byte[] jpegBytes = baos.toByteArray();

            CameraWebServer.updateFrame(cameraId, jpegBytes);

        } catch (Exception e) {
            LOGGER.debug("[URL Camera] Frame capture exception: " + e.getMessage());
        }
    }
}
