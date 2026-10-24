package com.example.tendonaris;

import com.example.tendonaris.registry.ModEntities;
import com.example.tendonaris.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Main entry point for the Tendon_Aris mod.
 *
 * <p>All content is registered through Forge's DeferredRegister system. Client-only
 * rendering code lives in the {@code client} package and is never loaded on a
 * dedicated server.</p>
 */
@Mod(TendonArisMod.MOD_ID)
public final class TendonArisMod {
    public static final String MOD_ID = "tendon_aris";

    public TendonArisMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
    }
}