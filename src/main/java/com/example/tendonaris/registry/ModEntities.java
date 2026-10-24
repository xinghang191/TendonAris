package com.example.tendonaris.registry;

import com.example.tendonaris.TendonArisMod;
import com.example.tendonaris.entity.TendonArisEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TendonArisMod.MOD_ID);

    /**
     * The entity id is {@code tendon_aris:tendon_aris}. A CREATURE category keeps
     * its spawn cap and persistence behavior close to cows and sheep.
     */
    public static final RegistryObject<EntityType<TendonArisEntity>> TENDON_ARIS =
            ENTITY_TYPES.register("tendon_aris", () -> EntityType.Builder
                    .of(TendonArisEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.8F)
                    .clientTrackingRange(10)
                    .build(ResourceLocation.fromNamespaceAndPath(TendonArisMod.MOD_ID, "tendon_aris").toString()));

    private ModEntities() {
    }
}
