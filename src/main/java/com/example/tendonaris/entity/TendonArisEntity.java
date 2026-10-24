package com.example.tendonaris.entity;

import com.example.tendonaris.entity.goal.FollowPlayerGoal;
import com.example.tendonaris.entity.goal.TendonArisAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Tendon_Aris: a neutral creature that trades, follows a player holding stone,
 * and retaliates against a player that hurts it.
 *
 * <p>The entity implements vanilla {@link Merchant}; therefore the trade screen
 * is the real 1.20.1 merchant menu (with real slot consumption and server/client
 * synchronisation), not a decorative custom screen.</p>
 */
public class TendonArisEntity extends PathfinderMob implements Merchant {
    /** Anger lasts 60 seconds after the last hit from a player. */
    public static final int ANGER_DURATION_TICKS = 20 * 60;

    /** Players must stay within this distance (in blocks) of the trader. */
    public static final double TRADE_DISTANCE = 8.0D;

    /**
     * Attacks per second. 2.5 gives ceil(20 / 2.5) = 8 ticks (0.4 s) between
     * hits, noticeably faster than a vanilla sword (1.6). Change only this
     * constant to retune the retaliation cadence.
     */
    public static final double ATTACK_SPEED = 2.5D;

    private static final String TAG_ANGER_TICKS = "AngerTicks";
    private static final String TAG_ANGRY_AT = "AngryAt";
    private static final String TAG_FOLLOW_TARGET = "FollowTarget";

    /** UUID of the player this creature is currently angry with, if any. */
    @Nullable
    private UUID angryAt;

    /** Remaining anger time in ticks; this is the authoritative hostile flag. */
    private int angerTicks;

    /** UUID of the player this creature follows after a stone interaction. */
    @Nullable
    private UUID followTarget;

    /** Merchant state. Only used on the logical server. */
    @Nullable
    private Player tradingPlayer;

    @Nullable
    private MerchantOffers offers;

    public TendonArisEntity(EntityType<? extends TendonArisEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                // Requirement: exactly 1 point (half a heart) of retaliation damage.
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                // 2.5 attacks/second = an 8-tick (0.4 s) cooldown; faster than
                // the vanilla sword's 1.6 attacks/second.
                .add(Attributes.ATTACK_SPEED, ATTACK_SPEED)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TendonArisAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new FollowPlayerGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /**
     * Spawn predicate used by {@code SpawnPlacementRegisterEvent}.
     *
     * <p>Natural spawns require a solid floor, two empty blocks of space and a
     * light level above 8. Spawn eggs, dispensers and spawners bypass those
     * checks so the egg remains usable anywhere.</p>
     */
    public static boolean checkTendonArisSpawnRules(EntityType<TendonArisEntity> type,
                                                    ServerLevelAccessor level,
                                                    MobSpawnType spawnType,
                                                    BlockPos pos,
                                                    RandomSource random) {
        if (spawnType == MobSpawnType.SPAWN_EGG
                || spawnType == MobSpawnType.DISPENSER
                || spawnType == MobSpawnType.SPAWNER) {
            return true;
        }

        BlockPos floorPos = pos.below();
        BlockState floor = level.getBlockState(floorPos);
        return level.isEmptyBlock(pos)
                && level.isEmptyBlock(pos.above())
                && floor.isFaceSturdy(level, floorPos, Direction.UP)
                && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }

        boolean holdingStone = player.getMainHandItem().is(Items.STONE)
                || player.getOffhandItem().is(Items.STONE);

        if (!this.level().isClientSide) {
            // A hostile Tendon_Aris refuses both trading and following.
            if (this.isAngry()) {
                return InteractionResult.PASS;
            }

            if (holdingStone) {
                this.startFollowing(player);
                return InteractionResult.CONSUME;
            }

            // Do not re-open the screen if the off-hand interaction runs after
            // the main-hand interaction in the same tick.
            if (this.getTradingPlayer() != player) {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, Component.translatable("entity.tendon_aris.trade"), 0);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Starts (or switches) following. The message is sent only to the player
     * involved and only from this interaction handler, never from a per-tick
     * loop, so holding right-click cannot spam the chat.
     */
    private void startFollowing(Player player) {
        this.followTarget = player.getUUID();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.literal("看看"), false);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);

        if (damaged && !this.level().isClientSide && source.getEntity() instanceof Player player) {
            this.becomeAngryAt(player);
        }

        return damaged;
    }

    private void becomeAngryAt(Player player) {
        this.angryAt = player.getUUID();
        this.angerTicks = ANGER_DURATION_TICKS;
        this.followTarget = null; // hostility overrides following
        this.setTarget(player);
        this.setTradingPlayer(null);
        this.getNavigation().stop();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);

        // One message per successful hit, and only to the player that was hit.
        if (hit && !this.level().isClientSide && target instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.literal("有无mc"), false);
        }

        return hit;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            return;
        }

        if (this.angerTicks > 0) {
            --this.angerTicks;

            if (this.angerTicks <= 0) {
                this.stopBeingAngry();
            } else {
                this.refreshAngryTarget();
            }
        }

        if (this.followTarget != null) {
            Player follower = this.getFollowingPlayer();
            if (follower == null || follower.level() != this.level()) {
                // Player logged out or changed dimension: forget the follow target.
                this.followTarget = null;
            }
        }
    }

    private void refreshAngryTarget() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && target.level() == this.level()) {
            return;
        }

        Player player = this.getAngryPlayer();
        if (player != null && player.isAlive() && player.level() == this.level()) {
            this.setTarget(player);
        } else {
            this.stopBeingAngry();
        }
    }

    private void stopBeingAngry() {
        this.angerTicks = 0;
        this.angryAt = null;
        this.setTarget(null);
    }

    public boolean isAngry() {
        return this.angerTicks > 0;
    }

    @Nullable
    public Player getAngryPlayer() {
        if (this.angryAt == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(this.angryAt);
    }

    @Nullable
    public Player getFollowingPlayer() {
        if (this.followTarget == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(this.followTarget);
    }

    /**
     * 1.20.1's player attack speed is expressed as attacks per second. A mob has
     * no attack-strength meter, so we convert the same ATTACK_SPEED attribute to
     * a tick interval: ceil(20 / attacksPerSecond). At 2.5 this is 8 ticks.
     */
    public int getAttackCooldownTicks() {
        double attacksPerSecond = this.getAttributeValue(Attributes.ATTACK_SPEED);
        if (attacksPerSecond <= 0.0D) {
            return 20;
        }
        return Math.max(1, Mth.ceil(20.0D / attacksPerSecond));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_ANGER_TICKS, this.angerTicks);
        if (this.angryAt != null) {
            tag.putUUID(TAG_ANGRY_AT, this.angryAt);
        }
        if (this.followTarget != null) {
            tag.putUUID(TAG_FOLLOW_TARGET, this.followTarget);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        this.angerTicks = Math.max(0, tag.getInt(TAG_ANGER_TICKS));
        this.angryAt = tag.hasUUID(TAG_ANGRY_AT) ? tag.getUUID(TAG_ANGRY_AT) : null;
        this.followTarget = tag.hasUUID(TAG_FOLLOW_TARGET) ? tag.getUUID(TAG_FOLLOW_TARGET) : null;

        if (this.angerTicks == 0) {
            this.angryAt = null;
        }
    }

    // ---------------------------------------------------------------------
    // Merchant implementation
    // ---------------------------------------------------------------------

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
        if (player != null) {
            this.getNavigation().stop();
        }
    }

    @Override
    @Nullable
    public Player getTradingPlayer() {
        if (this.tradingPlayer == null) {
            return null;
        }

        // Returning null makes MerchantMenu.stillValid(...) fail on the server,
        // so ServerPlayer closes the menu automatically when the player walks
        // away, dies, changes dimension, or the entity dies.
        if (!this.tradingPlayer.isAlive()
                || this.tradingPlayer.level() != this.level()
                || this.distanceToSqr(this.tradingPlayer) > TRADE_DISTANCE * TRADE_DISTANCE
                || !this.isAlive()) {
            return null;
        }

        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = TendonArisTrades.createOffers();
        }
        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        // The vanilla MerchantMenu handles the preview/output synchronisation.
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
        // This merchant does not level up.
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean canRestock() {
        return false;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    // ---------------------------------------------------------------------
    // Ambience / persistence
    // ---------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // cow-like persistence
    }
}