package com.urlcamera.mod.init;

import com.urlcamera.mod.item.CameraItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "urlcamera");

    public static final RegistryObject<Item> CAMERA_ITEM =
            ITEMS.register("camera_item",
                    () -> new CameraItem(new Item.Properties().stacksTo(16)));
}
