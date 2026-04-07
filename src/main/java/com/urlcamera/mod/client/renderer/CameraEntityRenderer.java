package com.urlcamera.mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.urlcamera.mod.client.model.CameraEntityModel;
import com.urlcamera.mod.entity.CameraEntity;
import com.urlcamera.mod.init.ModModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CameraEntityRenderer extends EntityRenderer<CameraEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("urlcamera", "textures/entity/camera.png");

    private final CameraEntityModel model;

    public CameraEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new CameraEntityModel(context.bakeLayer(ModModelLayers.CAMERA));
    }

    @Override
    public void render(CameraEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Apply yaw rotation (Y axis)
        float yaw = entity.getCameraYaw();
        if (entity.getRotationMode() == CameraEntity.MODE_ROTATING) {
            yaw = Mth.lerp(partialTick, entity.getCameraYaw() - 1.0f, entity.getCameraYaw());
        }

        // Translate to center, rotate, then render
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(entity.getCameraPitch()));

        // Scale down to fit within the 0.5x0.5 bounding box
        float scale = 1.0f / 16.0f;
        poseStack.scale(scale, scale, scale);

        var buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CameraEntity entity) {
        return TEXTURE;
    }
}
