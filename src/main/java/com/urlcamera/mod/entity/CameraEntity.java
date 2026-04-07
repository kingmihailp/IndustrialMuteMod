package com.urlcamera.mod.entity;

import com.urlcamera.mod.init.ModEntities;
import com.urlcamera.mod.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CameraEntity extends Entity {

    public static final int MODE_STATIONARY = 0;
    public static final int MODE_ROTATING   = 1;

    private static final EntityDataAccessor<Integer> ROTATION_MODE =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CAMERA_YAW =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CAMERA_PITCH =
            SynchedEntityData.defineId(CameraEntity.class, EntityDataSerializers.FLOAT);

    public CameraEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public CameraEntity(Level level) {
        this(ModEntities.CAMERA.get(), level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ROTATION_MODE, MODE_STATIONARY);
        this.entityData.define(CAMERA_YAW,    0.0f);
        this.entityData.define(CAMERA_PITCH,  0.0f);
    }

    // -------------------------------------------------------------------------
    // Tick: advance rotation and keep yRot in sync for renderers
    // -------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getRotationMode() == MODE_ROTATING) {
            float yaw = getCameraYaw() + 1.0f;
            if (yaw >= 360.0f) yaw -= 360.0f;
            setCameraYaw(yaw);
            this.setYRot(yaw); // synced to clients via entity-tracking packets
        }
    }

    // -------------------------------------------------------------------------
    // Chunk force-loading: keep this camera's chunk ticking on the server
    // even when no player is nearby.
    // -------------------------------------------------------------------------
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            ChunkPos cp = new ChunkPos(blockPosition());
            ((ServerChunkCache) serverLevel.getChunkSource()).updateChunkForced(cp, true);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            ChunkPos cp = new ChunkPos(blockPosition());
            ((ServerChunkCache) serverLevel.getChunkSource()).updateChunkForced(cp, false);
        }
    }

    // -------------------------------------------------------------------------
    // Player interaction
    // -------------------------------------------------------------------------
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        // Sneak + right-click: remove camera and drop it
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                dropItem();
                this.discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // Right-click with camera item: toggle rotation mode
        if (heldItem.getItem() == ModItems.CAMERA_ITEM.get()) {
            if (!level().isClientSide) {
                int newMode = getRotationMode() == MODE_STATIONARY ? MODE_ROTATING : MODE_STATIONARY;
                setRotationMode(newMode);
                String modeStr = newMode == MODE_ROTATING ? "ROTATING" : "STATIONARY";
                String url = "http://localhost:8765/camera/" + this.getStringUUID();
                player.sendSystemMessage(
                        Component.literal("[URL Camera] Mode: " + modeStr + " | ")
                                .append(Component.literal(url)
                                        .setStyle(Style.EMPTY
                                                .withUnderlined(true)
                                                .withColor(net.minecraft.ChatFormatting.AQUA)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))))
                );
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // Right-click with empty hand: re-show the URL
        if (!level().isClientSide) {
            String url = "http://localhost:8765/camera/" + this.getStringUUID();
            player.sendSystemMessage(
                    Component.literal("[URL Camera] Stream: ")
                            .append(Component.literal(url)
                                    .setStyle(Style.EMPTY
                                            .withUnderlined(true)
                                            .withColor(net.minecraft.ChatFormatting.AQUA)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))))
            );
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !this.isRemoved()) {
            dropItem();
            this.discard();
        }
        return true;
    }

    private void dropItem() {
        Vec3 pos = this.position();
        level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                level(), pos.x, pos.y, pos.z, new ItemStack(ModItems.CAMERA_ITEM.get())));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRotationMode(tag.getInt("rotationMode"));
        setCameraYaw(tag.getFloat("cameraYaw"));
        setCameraPitch(tag.getFloat("cameraPitch"));
        this.setYRot(getCameraYaw());
        this.setXRot(getCameraPitch());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("rotationMode", getRotationMode());
        tag.putFloat("cameraYaw",  getCameraYaw());
        tag.putFloat("cameraPitch", getCameraPitch());
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) { return 0.0f; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) { return distSq < 4096.0; }

    // Synced-data accessors
    public int getRotationMode()  { return this.entityData.get(ROTATION_MODE); }
    public void setRotationMode(int mode) { this.entityData.set(ROTATION_MODE, mode); }
    public float getCameraYaw()   { return this.entityData.get(CAMERA_YAW); }
    public void setCameraYaw(float yaw)   { this.entityData.set(CAMERA_YAW, yaw); }
    public float getCameraPitch() { return this.entityData.get(CAMERA_PITCH); }
    public void setCameraPitch(float pitch) { this.entityData.set(CAMERA_PITCH, pitch); }
}
