package com.example.tendonaris.entity.goal;

import com.example.tendonaris.entity.TendonArisEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Follows the player who last right-clicked the creature with stone.
 *
 * <p>Starts outside 4 blocks, stops inside 3 blocks. This hysteresis prevents
 * the navigation from starting and stopping every tick while the player stands
 * still. The goal is inactive while the creature is angry.</p>
 */
public class FollowPlayerGoal extends Goal {
    private final TendonArisEntity mob;
    private final double speedModifier;

    @Nullable
    private Player target;

    private int recalculatePathTicks;

    public FollowPlayerGoal(TendonArisEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isAngry()) {
            return false;
        }

        Player player = this.mob.getFollowingPlayer();
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || player.level() != this.mob.level()) {
            return false;
        }

        if (this.mob.distanceToSqr(player) < 4.0D * 4.0D) {
            return false;
        }

        this.target = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.isAngry() || this.target == null || !this.target.isAlive()) {
            return false;
        }
        if (this.target.level() != this.mob.level() || this.target.isSpectator()) {
            return false;
        }
        return this.mob.distanceToSqr(this.target) > 3.0D * 3.0D;
    }

    @Override
    public void start() {
        this.recalculatePathTicks = 0;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }

        this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (--this.recalculatePathTicks <= 0) {
            this.recalculatePathTicks = 10;
            this.mob.getNavigation().moveTo(this.target, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.mob.getNavigation().stop();
    }
}