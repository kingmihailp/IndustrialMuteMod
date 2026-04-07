package com.urlcamera.mod.item;

import com.urlcamera.mod.entity.CameraEntity;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class CameraItem extends Item {

    public CameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        Direction face = context.getClickedFace();
        BlockPos blockPos = context.getClickedPos();

        double x = blockPos.getX() + 0.5 + face.getStepX() * 0.35;
        double y = blockPos.getY() + 0.5 + face.getStepY() * 0.35;
        double z = blockPos.getZ() + 0.5 + face.getStepZ() * 0.35;

        // Camera looks in the same direction the player is facing when placing it
        float yaw   = player.getYRot();
        float pitch = player.getXRot();

        if (!level.isClientSide) {
            CameraEntity camera = new CameraEntity(level);
            camera.setPos(x, y, z);
            camera.setCameraYaw(yaw);
            camera.setCameraPitch(pitch);
            camera.setYRot(yaw);
            camera.setXRot(pitch);

            level.addFreshEntity(camera);

            String url = "http://localhost:8765/camera/" + camera.getStringUUID();

            Component message = Component.literal("[URL Camera] Camera placed! View at: ")
                    .append(
                            Component.literal(url)
                                    .setStyle(Style.EMPTY
                                            .withUnderlined(true)
                                            .withColor(net.minecraft.ChatFormatting.AQUA)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                    )
                    );

            player.sendSystemMessage(message);

            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
