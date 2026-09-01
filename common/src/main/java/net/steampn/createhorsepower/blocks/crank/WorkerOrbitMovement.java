package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.Horse;

/** Shared worker orbit movement and Minecraft-facing conversion. */
public final class WorkerOrbitMovement {
    private static final double MIN_MOVEMENT_SQUARED = 1.0e-8D;

    private WorkerOrbitMovement() {}

    public static void move(Mob mob, double centerX, double centerZ, float radius, double angularDelta) {
        double oldX = mob.getX();
        double oldZ = mob.getZ();
        double currentAngle = Math.atan2(oldZ - centerZ, oldX - centerX);
        double newAngle = currentAngle + angularDelta;
        double targetX = centerX + radius * Math.cos(newAngle);
        double targetZ = centerZ + radius * Math.sin(newAngle);
        double deltaX = targetX - oldX;
        double deltaZ = targetZ - oldZ;

        mob.teleportTo(targetX, mob.getY(), targetZ);

        if (deltaX * deltaX + deltaZ * deltaZ > MIN_MOVEMENT_SQUARED) {
            float yaw = yawFromMovement(deltaX, deltaZ);
            mob.setYRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
        }

        if (mob instanceof Horse horse && horse.isEating()) {
            horse.setEating(false);
        }
    }

    public static float yawFromMovement(double deltaX, double deltaZ) {
        return (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
    }
}
