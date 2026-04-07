package com.urlcamera.mod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.urlcamera.mod.entity.CameraEntity;
import com.urlcamera.mod.server.CameraWebServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderTarget;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "urlcamera", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEventHandlers {

    private static final Logger LOGGER = LogManager.getLogger("urlcamera/client-forge");

    // --- FBO-based per-camera rendering ---
    private static final Map<UUID, Long> lastFboCapture = new ConcurrentHashMap<>();
    /** How often to render each camera's FBO (ms). Higher = cheaper. */
    private static final long FBO_INTERVAL_MS = 500; // ~2 fps per camera
    private static final int CAPTURE_W = 640;
    private static final int CAPTURE_H = 360;
    /** Single shared off-screen render target reused across cameras. */
    private static RenderTarget cameraFBO = null;

    // --- Fallback: capture the main framebuffer ---
    private static long lastFallbackCapture = 0;
    private static final long FALLBACK_INTERVAL_MS = 100; // 10 fps

    // For registering / unregistering cameras in the web server
    private static final List<UUID> registeredCameraIds = new ArrayList<>();

    // -------------------------------------------------------------------------
    // ClientTickEvent – keep the web-server's camera list in sync
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

            if (!registeredCameraIds.contains(uuid)) {
                registeredCameraIds.add(uuid);
            }
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
    // RenderTickEvent – two phases
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (CameraWebServer.getRegisteredCameraIds().isEmpty()) return;

        if (event.phase == TickEvent.Phase.START) {
            // ---- Phase START: FBO renders BEFORE the main scene ----
            // We bind our own FBO, call renderLevel() with camera entity as the viewpoint,
            // then restore the main render target so the normal game render is unaffected.
            long now = System.currentTimeMillis();

            // Snapshot the entity list to avoid CME
            List<CameraEntity> cameras = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof CameraEntity cam) cameras.add(cam);
            }

            for (CameraEntity camera : cameras) {
                UUID id = camera.getUUID();
                if (now - lastFboCapture.getOrDefault(id, 0L) < FBO_INTERVAL_MS) continue;
                lastFboCapture.put(id, now);
                renderCameraToFBO(mc, camera, id);
            }

            // Always restore the main render target before the game renders its frame
            mc.getMainRenderTarget().bindWrite(true);

        } else if (event.phase == TickEvent.Phase.END) {
            // ---- Phase END: fallback – send the current game view ----
            // Cameras that still have no FBO frame receive the player's current view
            // so the stream is never empty.
            long now = System.currentTimeMillis();
            if (now - lastFallbackCapture < FALLBACK_INTERVAL_MS) return;
            lastFallbackCapture = now;

            try {
                byte[] jpeg = captureFromTarget(mc.getMainRenderTarget());
                if (jpeg == null) return;

                UUID activeId = CameraViewManager.getActiveCameraId();
                if (activeId != null) {
                    // Player entered camera-view mode → current render IS the camera view
                    CameraWebServer.updateFrame(activeId, jpeg);
                } else {
                    // No camera-view mode – push player view to every camera that has no
                    // FBO frame yet, so the stream starts immediately on page load.
                    for (UUID id : new ArrayList<>(CameraWebServer.getRegisteredCameraIds())) {
                        if (!CameraWebServer.hasFrame(id)) {
                            CameraWebServer.updateFrame(id, jpeg);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[URL Camera] Fallback capture error: {}", e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // FBO rendering: render the world from the camera entity's perspective
    // -------------------------------------------------------------------------
    private static void renderCameraToFBO(Minecraft mc, CameraEntity camera, UUID cameraId) {
        try {
            // Lazily create a shared off-screen render target
            if (cameraFBO == null) {
                cameraFBO = new RenderTarget(true);
                cameraFBO.createBuffers(CAPTURE_W, CAPTURE_H, Minecraft.ON_OSX);
            }

            Entity prevCamera = mc.getCameraEntity();
            RenderTarget mainTarget = mc.getMainRenderTarget();

            // Point the engine camera at this camera entity
            mc.setCameraEntity(camera);

            // Bind our off-screen FBO and clear it
            cameraFBO.bindWrite(true);
            RenderSystem.clearColor(0.1f, 0.1f, 0.1f, 1.0f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            // Render the world to our FBO from the camera's viewpoint.
            // partialTick = 0 (full-tick accuracy is fine for a surveillance feed).
            // Give it a generous deadline so it doesn't cut corners.
            mc.gameRenderer.renderLevel(0f, System.nanoTime() + 100_000_000L, new PoseStack());

            // Download pixels and encode as JPEG
            byte[] jpeg = captureFromTarget(cameraFBO);
            if (jpeg != null) {
                CameraWebServer.updateFrame(cameraId, jpeg);
            }

            // Restore the engine camera and main render target
            mc.setCameraEntity(prevCamera);
            mainTarget.bindWrite(true);

        } catch (Exception e) {
            // FBO render can fail (e.g. during world load); log at debug level only
            LOGGER.debug("[URL Camera] FBO render failed for {}: {}", cameraId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Pixel capture: download a render target's colour texture and encode JPEG
    // -------------------------------------------------------------------------
    private static byte[] captureFromTarget(RenderTarget target) {
        if (target == null) return null;
        int srcW = target.width;
        int srcH = target.height;
        if (srcW <= 0 || srcH <= 0) return null;

        try (NativeImage img = new NativeImage(srcW, srcH, false)) {
            // downloadTexture reads from the texture currently bound to GL_TEXTURE_2D
            RenderSystem.bindTexture(target.getColorTextureId());
            img.downloadTexture(0, false);
            // OpenGL stores textures with Y=0 at the bottom; flip so Y=0 is the top
            img.flipY();

            BufferedImage out = new BufferedImage(CAPTURE_W, CAPTURE_H, BufferedImage.TYPE_INT_RGB);
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
                    out.setRGB(px, py, (r << 16) | (g << 8) | b);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
            ImageIO.write(out, "jpg", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            LOGGER.debug("[URL Camera] captureFromTarget error: {}", e.getMessage());
            return null;
        }
    }
}
