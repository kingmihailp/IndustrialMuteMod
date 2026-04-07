package com.urlcamera.mod.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.urlcamera.mod.entity.CameraEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class CameraEntityModel extends EntityModel<CameraEntity> {

    private final ModelPart body;
    private final ModelPart lens;

    public CameraEntityModel(ModelPart root) {
        this.body = root.getChild("body");
        this.lens = root.getChild("lens");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDef = new MeshDefinition();
        PartDefinition partDef = meshDef.getRoot();

        // Body: 8x5x6 cube, centered
        partDef.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0f, -2.5f, -3.0f, 8, 5, 6, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // Lens: 4x4x3 cube, sticking out front (negative Z direction in model space)
        partDef.addOrReplaceChild("lens",
                CubeListBuilder.create()
                        .texOffs(28, 0)
                        .addBox(-2.0f, -2.0f, -6.0f, 4, 4, 3, new CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        return LayerDefinition.create(meshDef, 64, 32);
    }

    @Override
    public void setupAnim(CameraEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // No animation needed for static camera
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        lens.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
