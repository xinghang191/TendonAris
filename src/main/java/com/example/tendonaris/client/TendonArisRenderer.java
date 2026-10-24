package com.example.tendonaris.client;

import com.example.tendonaris.TendonArisMod;
import com.example.tendonaris.entity.TendonArisEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TendonArisRenderer extends MobRenderer<TendonArisEntity, TendonArisModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TendonArisMod.MOD_ID, "textures/entity/tendon_aris.png");

    public TendonArisRenderer(EntityRendererProvider.Context context) {
        super(context, new TendonArisModel(context.bakeLayer(TendonArisModel.LAYER_LOCATION)), 0.55F);
    }

    @Override
    public ResourceLocation getTextureLocation(TendonArisEntity entity) {
        return TEXTURE;
    }
}
