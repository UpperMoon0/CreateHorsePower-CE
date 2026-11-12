package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.world.entity.Mob;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared worker orbit movement and Minecraft-facing conversion. */
public final class WorkerOrbitMovement {
    private static final double MIN_MOVEMENT_SQUARED = 1.0e-8D;

    public record Snapshot(
            double oldX,
            double oldZ,
            double targetX,
            double targetZ,
            double actualX,
            double actualZ,
            double currentAngle,
            double newAngle,
            double deltaX,
            double deltaZ,
            float requestedYaw,
            float actualYaw,
            boolean moved,
            boolean eatingSuppressed
    ) {}

    private WorkerOrbitMovement() {}

    public static Snapshot move(Mob mob, double centerX, double centerZ, float radius, double angularDelta) {
        double currentAngle = angleFromPosition(mob.getX(), mob.getZ(), centerX, centerZ);
        return moveToAngle(mob, centerX, centerZ, radius, currentAngle, currentAngle + angularDelta);
    }

    /** Validate the next landing and full travelled arc without moving the mob. */
    public static boolean canMoveToAngle(Mob mob, double centerX, double centerZ,
                                         float radius, double newAngle) {
        double oldX = mob.getX();
        double oldZ = mob.getZ();
        return isTravelSafe(mob, oldX, oldZ,
                centerX + radius * Math.cos(newAngle),
                centerZ + radius * Math.sin(newAngle), centerX, centerZ, radius);
    }

    private static boolean isTravelSafe(Mob mob, double oldX, double oldZ,
                                        double targetX, double targetZ,
                                        double centerX, double centerZ, float radius) {
        double deltaX = targetX - oldX;
        double deltaZ = targetZ - oldZ;
        double radialDistance = Math.hypot(oldX - centerX, oldZ - centerZ);
        boolean joiningRing = Math.abs(radialDistance - radius) > 0.125D;
        double distance = Math.hypot(deltaX, deltaZ);
        // Always sweep the worker's actual bounding box over every part of
        // the displacement, not only its final landing. A destination-only
        // test permits teleporting through walls, fences or other workers.
        // Short sweeps follow the circular orbit without over-rejecting
        // unrelated blocks in the axis-aligned box of a long diagonal step.
        int steps = Math.max(1, (int) Math.ceil(distance * 4.0D));
        double stepX = deltaX / steps;
        double stepZ = deltaZ / steps;
        // Workers may attach while partly overlapping the central crank.
        // Only the initial escape from that *pre-existing* penetration skips
        // sweeping; otherwise a valid worker could never join the ring. We
        // still require a collision-free landing before any further sweeps.
        double crankClearance = 0.5D + mob.getBbWidth() * 0.5D;
        boolean escapingCrank = joiningRing
                && Math.abs(oldX - centerX) < crankClearance
                && Math.abs(oldZ - centerZ) < crankClearance
                && !mob.level().noCollision(mob, mob.getBoundingBox());
        for (int step = 1; step <= steps; step++) {
            double x = oldX + stepX * step;
            double z = oldZ + stepZ * step;
            // Joining workers may cross an unpaved interior, but they must
            // still avoid collisions for the whole relocation.
            if ((!joiningRing || step == steps) &&
                    !PathEvaluator.supportsWorkerFootprint(mob.level(), x, mob.getY(), z, mob.getBbWidth())) {
                return false;
            }
            if (escapingCrank) {
                AABB sample = mob.getBoundingBox().move(stepX * step, 0.0D, stepZ * step);
                if (!mob.level().noCollision(mob, sample)) {
                    // Forgive penetration of the central crank block only.
                    // Any collision with an adjacent wall, overhead block or
                    // other obstruction must stop this attempted relocation.
                    if (step == steps || !collisionConfinedToCrank(mob, sample, centerX, centerZ)) {
                        return false;
                    }
                    continue;
                }
                escapingCrank = false;
                continue;
            }
            if (!mob.level().noCollision(mob, mob.getBoundingBox()
                    .move(stepX * (step - 1), 0.0D, stepZ * (step - 1))
                    .expandTowards(stepX, 0.0D, stepZ))) {
                return false;
            }
        }
        return true;
    }



    /**
     * Ignore only the collision shape owned by the original crank block.
     * Its model rises above the block cell, so a generic 1x1x1 exclusion
     * can falsely reject safe onboarding; excluding a larger box could let
     * workers pass through a separate wall or block stacked above the crank.
     */
    private static boolean collisionConfinedToCrank(Mob mob, AABB box,
                                                     double centerX, double centerZ) {
        var level = mob.level();
        BlockPos crank = BlockPos.containing(centerX, mob.getY(), centerZ);
        if (!(level.getBlockState(crank).getBlock() instanceof AbstractHorseCrankBlock)) {
            crank = crank.below();
        }
        if (!(level.getBlockState(crank).getBlock() instanceof AbstractHorseCrankBlock)
                || !level.getWorldBorder().isWithinBounds(box)
                || !level.getEntities(mob, box,
                        other -> other.canBeCollidedWith() && !other.isSpectator()).isEmpty()) {
            return false;
        }

        CollisionContext context = CollisionContext.of(mob);
        boolean hitCrank = false;
        for (BlockPos sample : BlockPos.betweenClosed(
                Mth.floor(box.minX) - 1, Mth.floor(box.minY) - 1, Mth.floor(box.minZ) - 1,
                Mth.floor(box.maxX) + 1, Mth.floor(box.maxY) + 1, Mth.floor(box.maxZ) + 1)) {
            BlockState state = level.getBlockState(sample);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, sample, context);
            if (shape.isEmpty() || !Shapes.joinIsNotEmpty(shape,
                    Shapes.create(box.move(-sample.getX(), -sample.getY(), -sample.getZ())),
                    BooleanOp.AND)) continue;
            if (!sample.equals(crank)) return false;
            hitCrank = true;
        }
        return hitCrank;
    }

    public static Snapshot moveToAngle(
            Mob mob,
            double centerX,
            double centerZ,
            float radius,
            double currentAngle,
            double newAngle
    ) {
        double oldX = mob.getX();
        double oldZ = mob.getZ();
        double targetX = centerX + radius * Math.cos(newAngle);
        double targetZ = centerZ + radius * Math.sin(newAngle);
        double deltaX = targetX - oldX;
        double deltaZ = targetZ - oldZ;

        boolean moved = deltaX * deltaX + deltaZ * deltaZ > MIN_MOVEMENT_SQUARED;
        float requestedYaw = moved ? yawFromMovement(deltaX, deltaZ) : mob.getYRot();

        // Both the blocked-state probe and actual motion use identical
        // physical support/collision rules, so a persistent obstruction
        // cannot create an RPM pulse just to discover the same blocked step.
        if (!isTravelSafe(mob, oldX, oldZ, targetX, targetZ, centerX, centerZ, radius)) {
            return new Snapshot(oldX, oldZ, targetX, targetZ, mob.getX(), mob.getZ(),
                    currentAngle, newAngle, deltaX, deltaZ, requestedYaw,
                    mob.getYRot(), false, false);
        }

        mob.teleportTo(targetX, mob.getY(), targetZ);

        if (moved) {
            mob.setYRot(requestedYaw);
            mob.setYHeadRot(requestedYaw);
            mob.setYBodyRot(requestedYaw);
        }

        boolean eatingSuppressed = mob instanceof Horse horse && horse.isEating();
        if (eatingSuppressed) {
            Horse horse = (Horse) mob;
            horse.setEating(false);
        }

        return new Snapshot(
                oldX, oldZ,
                targetX, targetZ,
                mob.getX(), mob.getZ(),
                currentAngle, newAngle,
                deltaX, deltaZ,
                requestedYaw, mob.getYRot(),
                moved, eatingSuppressed
        );
    }


    /** Convert the mob movement-speed attribute into a bounded visual ground speed. */
    public static double groundSpeedBlocksPerSecond(double movementAttribute, double scale, double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        double raw = movementAttribute * scale;
        if (!Double.isFinite(raw)) raw = low;
        return Math.max(low, Math.min(high, raw));
    }

    /**
     * Convert a linear ground speed to an angular step. Because angular speed
     * is derived from v/r, equal ground speeds remain equal at every radius.
     */
    public static double angularDeltaPerTick(double groundSpeedBlocksPerSecond, double radius, double direction) {
        if (!Double.isFinite(groundSpeedBlocksPerSecond) || !Double.isFinite(radius) || radius <= 0.0D) return 0.0D;
        return (groundSpeedBlocksPerSecond / radius) / 20.0D * Math.signum(direction);
    }

    public static double linearDistancePerTick(double radius, double angularDelta) {
        return Math.abs(radius * angularDelta);
    }

    public static double angleFromPosition(double x, double z, double centerX, double centerZ) {
        return Math.atan2(z - centerZ, x - centerX);
    }

    public static double normalizeAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    public static float yawFromMovement(double deltaX, double deltaZ) {
        return (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
    }
}
