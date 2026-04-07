package com.urlcamera.mod.init;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.registries.Registries;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "urlcamera");

    public static final RegistryObject<CreativeModeTab> URL_CAMERA_TAB =
            CREATIVE_MODE_TABS.register("url_camera",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.urlcamera.url_camera"))
                            .icon(() -> new ItemStack(ModItems.CAMERA_ITEM.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.CAMERA_ITEM.get());
                            })
                            .build());
}
