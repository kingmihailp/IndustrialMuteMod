package com.urlcamera.mod.init;

import com.urlcamera.mod.entity.CameraEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "urlcamera");

    public static final RegistryObject<EntityType<CameraEntity>> CAMERA =
            ENTITY_TYPES.register("camera",
                    () -> EntityType.Builder.<CameraEntity>of(CameraEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .build("urlcamera:camera"));
}
