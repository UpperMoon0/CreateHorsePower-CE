package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Horse;

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
