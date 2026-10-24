package com.example.tendonaris.entity.goal;

import com.example.tendonaris.entity.TendonArisEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * A 1.20.1 melee goal that uses the entity's ATTACK_SPEED attribute instead of
 * the hard-coded 20-tick interval used by vanilla {@code MeleeAttackGoal}.
 *
 * <p>The goal only runs while the entity is angry, chases the recorded attacker,
 * and performs one attack each cooldown. {@link TendonArisEntity#doHurtTarget}
 * sends the hit message, so a miss cannot print it.</p>
 */
public class TendonArisAttackGoal extends Goal {
    private final TendonArisEntity mob;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;

    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;

    public TendonArisAttackGoal(TendonArisEntity mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isAngry()) {
            return false;
        }

        long gameTime = this.mob.level().getGameTime();
        if (gameTime - this.lastCanUseCheck < 20L) {
            return false;
        }
        this.lastCanUseCheck = gameTime;

        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }

        this.path = this.mob.getNavigation().createPath(target, 0);
        return this.path != null
                || this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ())
                <= this.getAttackReachSqr(target);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mob.isAngry()) {
            return false;
        }

        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        }
        return this.mob.isWithinRestriction(target.blockPosition());
    }

    @Override
    public void start() {
        if (this.path != null) {
            this.mob.getNavigation().moveTo(this.path, this.speedModifier);
        } else if (this.mob.getTarget() != null) {
            this.mob.getNavigation().moveTo(this.mob.getTarget(), this.speedModifier);
        }
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = this.mob.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
            this.mob.setTarget(null);
        }
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distanceSqr = this.mob.getPerceivedTargetDistanceSquareForMeleeAttack(target);

        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        if ((this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(target))
                && this.ticksUntilNextPathRecalculation <= 0) {
            boolean targetMoved = this.pathedTargetX == 0.0D
                    && this.pathedTargetY == 0.0D
                    && this.pathedTargetZ == 0.0D
                    || target.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0D
                    || this.mob.getRandom().nextFloat() < 0.05F;

            if (targetMoved) {
                this.pathedTargetX = target.getX();
                this.pathedTargetY = target.getY();
                this.pathedTargetZ = target.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);

                if (distanceSqr > 1024.0D) {
                    this.ticksUntilNextPathRecalculation += 10;
                } else if (distanceSqr > 256.0D) {
                    this.ticksUntilNextPathRecalculation += 5;
                }

                if (!this.mob.getNavigation().moveTo(target, this.speedModifier)) {
                    this.ticksUntilNextPathRecalculation += 15;
                }

                this.ticksUntilNextPathRecalculation =
                        this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
            }
        }

        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);

        if (distanceSqr <= this.getAttackReachSqr(target) && this.ticksUntilNextAttack <= 0) {
            // Reset first: if the target is briefly invulnerable, the creature
            // waits a full cooldown instead of spamming attack attempts.
            this.ticksUntilNextAttack = this.adjustedTickDelay(this.mob.getAttackCooldownTicks());
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }
    }

    private double getAttackReachSqr(LivingEntity target) {
        return (double) (this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth());
    }
}