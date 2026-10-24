package com.example.tendonaris.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * All trade numbers are kept here so the offer can be changed without touching
 * the entity or the GUI code.
 *
 * <p>Default: one netherite ingot buys one stone. The offer has 9999 uses,
 * which is effectively unlimited for normal play, and its price multiplier is
 * zero so the displayed price never drifts.</p>
 */
public final class TendonArisTrades {
    public static final int NETHERITE_COST = 1;
    public static final int STONE_REWARD = 1;
    public static final int MAX_USES = 9999;
    public static final int TRADE_XP = 0;
    public static final float PRICE_MULTIPLIER = 0.0F;

    public static MerchantOffers createOffers() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(
                new ItemStack(Items.NETHERITE_INGOT, NETHERITE_COST),
                new ItemStack(Items.STONE, STONE_REWARD),
                MAX_USES,
                TRADE_XP,
                PRICE_MULTIPLIER));
        return offers;
    }

    private TendonArisTrades() {
    }
}