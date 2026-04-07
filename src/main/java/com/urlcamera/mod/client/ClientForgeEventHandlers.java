package com.urlcamera.mod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.urlcamera.mod.entity.CameraEntity;
import com.urlcamera.mod.server.CameraWebServer;
import net.minecraft.client.Minecraft;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod.EventBusSubscriber(modid = "urlcamera", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEventHandlers {

    private static final Logger LOGGER = LogManager.getLogger("urlcamera/client-forge");

    /** JPEG encoding is CPU-heavy; keep it off the render thread. */
    private static final ExecutorService JPEG_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "urlcamera-jpeg");
        t.setDaemon(true);
        return t;
    });

    private static final int CAPTURE_W = 640;
    private static final int CAPTURE_H = 360;
    private static final long CAPTURE_INTERVAL_MS = 100; // max 10 fps
    private static long lastCapture = 0;
    private static boolean encodeInFlight = false; // don't queue faster than we encode

    private static final List<UUID> registeredCameraIds = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Keep the web-server's camera list in sync with loaded entities
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<UUID> currentCameras = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof CameraEntity camera)) continue;
            UUID uuid = camera.getUUID();
            currentCameras.add(uuid);
            String mode = camera.getRotationMode() == CameraEntity.MODE_ROTATING ? "ROTATING" : "STATIONARY";
            CameraWebServer.registerCamera(uuid, camera.getX(), camera.getY(), camera.getZ(), mode);
            if (!registeredCameraIds.contains(uuid)) registeredCameraIds.add(uuid);
        }

        List<UUID> toRemove = new ArrayList<>();
        for (UUID id : registeredCameraIds) {
            if (!currentCameras.contains(id)) {
                toRemove.add(id);
                CameraWebServer.unregisterCamera(id);
            }
        }
        registeredCameraIds.removeAll(toRemove);
    }

    // -------------------------------------------------------------------------
    // After each rendered frame: grab the framebuffer and push to all cameras.
    // Heavy JPEG encoding is offloaded to a background thread so the render
    // thread is never blocked.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (CameraWebServer.getRegisteredCameraIds().isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastCapture < CAPTURE_INTERVAL_MS) return;
        if (encodeInFlight) return; // previous frame not done yet – skip
        lastCapture = now;

        RenderTarget target = mc.getMainRenderTarget();
        if (target == null || target.width <= 0 || target.height <= 0) return;

        // ----- Render-thread work: download GPU pixels into a Java int[] -----
        int srcW = target.width;
        int srcH = target.height;
        int[] pixels = new int[CAPTURE_W * CAPTURE_H];

        try (NativeImage img = new NativeImage(srcW, srcH, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            img.downloadTexture(0, false);
            img.flipY(); // OpenGL Y=0 is bottom; flip so Y=0 is top

            float sx = (float) srcW / CAPTURE_W;
            float sy = (float) srcH / CAPTURE_H;
            for (int py = 0; py < CAPTURE_H; py++) {
                int iy = Math.min((int) (py * sy), srcH - 1);
                for (int px = 0; px < CAPTURE_W; px++) {
                    int ix = Math.min((int) (px * sx), srcW - 1);
                    // NativeImage RGBA: bits 0-7 = R, 8-15 = G, 16-23 = B
                    int rgba = img.getPixelRGBA(ix, iy);
                    int r = rgba & 0xFF;
                    int g = (rgba >> 8) & 0xFF;
                    int b = (rgba >> 16) & 0xFF;
                    pixels[py * CAPTURE_W + px] = (r << 16) | (g << 8) | b;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[URL Camera] Pixel download error: {}", e.getMessage());
            return;
        }

        // ----- Background thread: encode JPEG and push to every camera -----
        List<UUID> ids = new ArrayList<>(CameraWebServer.getRegisteredCameraIds());
        encodeInFlight = true;
        JPEG_POOL.submit(() -> {
            try {
                BufferedImage img = new BufferedImage(CAPTURE_W, CAPTURE_H, BufferedImage.TYPE_INT_RGB);
                img.setRGB(0, 0, CAPTURE_W, CAPTURE_H, pixels, 0, CAPTURE_W);

                ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
                ImageIO.write(img, "jpg", baos);
                byte[] jpeg = baos.toByteArray();

                for (UUID id : ids) {
                    CameraWebServer.updateFrame(id, jpeg);
                }
            } catch (Exception e) {
                LOGGER.debug("[URL Camera] JPEG encode error: {}", e.getMessage());
            } finally {
                encodeInFlight = false;
            }
        });
    }
}
