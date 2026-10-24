package com.example.tendonaris.registry;

import com.example.tendonaris.TendonArisMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TendonArisMod.MOD_ID);

    // Placeholder egg colors: pale tendon / dark red tendon strand.
    public static final RegistryObject<Item> TENDON_ARIS_SPAWN_EGG =
            ITEMS.register("tendon_aris_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntities.TENDON_ARIS,
                    0xD8C7B0,
                    0x8E2F2F,
                    new Item.Properties()));

    private ModItems() {
    }
}