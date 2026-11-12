package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.world.entity.Mob;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.phys.AABB;

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
     * A worker can spawn partly inside the central crank. During its first
     * escape, only the volume of that one block is exempt from collision.
     * Test all six outside slabs of the swept bounding box, rather than
     * skipping collisions until the worker happens to find a clear landing.
     * This rejects a wall touching the crank without rejecting valid onboarding.
     */
    private static boolean collisionConfinedToCrank(Mob mob, AABB box,
                                                     double centerX, double centerZ) {
        double x0 = Math.floor(centerX);
        double x1 = x0 + 1.0D;
        double z0 = Math.floor(centerZ);
        double z1 = z0 + 1.0D;
        double y0 = Math.floor(mob.getY());
        double y1 = y0 + 1.0D;
        double insideX0 = Math.max(box.minX, x0);
        double insideX1 = Math.min(box.maxX, x1);
        double insideZ0 = Math.max(box.minZ, z0);
        double insideZ1 = Math.min(box.maxZ, z1);
        if (insideX0 >= insideX1 || insideZ0 >= insideZ1
                || box.maxY <= y0 || box.minY >= y1) {
            return false;
        }
        if (!clearSlab(mob, box.minX, box.minY, box.minZ,
                Math.min(box.maxX, x0), box.maxY, box.maxZ)) return false;
        if (!clearSlab(mob, Math.max(box.minX, x1), box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ)) return false;
        if (!clearSlab(mob, insideX0, box.minY, box.minZ,
                insideX1, box.maxY, Math.min(box.maxZ, z0))) return false;
        if (!clearSlab(mob, insideX0, box.minY, Math.max(box.minZ, z1),
                insideX1, box.maxY, box.maxZ)) return false;
        if (!clearSlab(mob, insideX0, box.minY, insideZ0,
                insideX1, Math.min(box.maxY, y0), insideZ1)) return false;
        return clearSlab(mob, insideX0, Math.max(box.minY, y1), insideZ0,
                insideX1, box.maxY, insideZ1);
    }

    private static boolean clearSlab(Mob mob, double x0, double y0, double z0,
                                     double x1, double y1, double z1) {
        return x1 <= x0 || y1 <= y0 || z1 <= z0
                || mob.level().noCollision(mob, new AABB(x0, y0, z0, x1, y1, z1));
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
