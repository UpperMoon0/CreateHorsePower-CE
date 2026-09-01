package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Temporarily prevents vanilla mob AI from competing with crank-driven movement. */
public final class WorkerActivityControl {
    private WorkerActivityControl() {}

    /** Returns true only when the crank changed the mob's AI state and therefore owns restoration. */
    public static boolean acquire(Mob mob) {
        boolean acquired = !mob.isNoAi();
        maintain(mob);
        return acquired;
    }

    public static void maintain(Mob mob) {
        mob.setNoAi(true);
        mob.getNavigation().stop();
        clearHorizontalVelocity(mob);
    }

    public static void release(Mob mob, boolean ownedByCrank) {
        if (ownedByCrank) {
            mob.setNoAi(false);
        }
        mob.getNavigation().stop();
        clearHorizontalVelocity(mob);
    }

    public static void clearHorizontalVelocity(Mob mob) {
        Vec3 movement = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, movement.y, 0.0D);
    }
}
