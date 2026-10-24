package com.example.tendonaris.client;

import com.example.tendonaris.TendonArisMod;
import com.example.tendonaris.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only registration. This class is annotated with Dist.CLIENT, so it is
 * never loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(
        modid = TendonArisMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TendonArisModel.LAYER_LOCATION, TendonArisModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TENDON_ARIS.get(), TendonArisRenderer::new);
    }

    private ClientSetup() {
    }
}