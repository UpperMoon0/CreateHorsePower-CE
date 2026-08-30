package net.steampn.createhorsepower.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.content.stats.WorkerStats;

import java.util.List;
import java.util.Optional;

public class CHPUtils {

    public enum WorkerTier {
        NONE,
        SMALL,
        MEDIUM,
        LARGE;

        public boolean isValid() {
            return this != NONE;
        }
    }

    public static WorkerTier getWorkerTier(EntityType<?> type) {
        if (type == null) return WorkerTier.NONE;
        String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (type.is(CHPTags.Entities.WORKERS_SMALL) || type.is(CHPTags.Entities.SMALL_WORKER_TAG)
                || net.steampn.createhorsepower.platform.CHPApi.config().smallCreatures().contains(key)) {
            return WorkerTier.SMALL;
        }
        if (type.is(CHPTags.Entities.WORKERS_MEDIUM) || type.is(CHPTags.Entities.MEDIUM_WORKER_TAG)
                || net.steampn.createhorsepower.platform.CHPApi.config().mediumCreatures().contains(key)) {
            return WorkerTier.MEDIUM;
        }
        if (type.is(CHPTags.Entities.WORKERS_LARGE) || type.is(CHPTags.Entities.LARGE_WORKER_TAG)
                || net.steampn.createhorsepower.platform.CHPApi.config().largeCreatures().contains(key)) {
            return WorkerTier.LARGE;
        }
        return WorkerTier.NONE;
    }

    public static Optional<LeashFenceKnotEntity> getKnot(Level level, BlockPos pos) {
        if (level == null) return Optional.empty();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        List<LeashFenceKnotEntity> knots = level.getEntitiesOfClass(
                LeashFenceKnotEntity.class,
                new AABB(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)
        );
        for (LeashFenceKnotEntity knot : knots) {
            if (knot.blockPosition().equals(pos)) {
                return Optional.of(knot);
            }
        }
        return Optional.empty();
    }

    /** Returns true only when a living mob is actually leashed to this crank's knot. */
    public static boolean hasAttachedWorker(Level level, BlockPos pos) {
        Optional<LeashFenceKnotEntity> knot = getKnot(level, pos);
        if (knot.isEmpty()) return false;

        return !level.getEntitiesOfClass(
                Mob.class,
                new AABB(pos).inflate(WorkerStats.MAX_MOVEMENT_RADIUS + 4.0D),
                mob -> mob.isAlive() && mob.isLeashed() && mob.getLeashHolder() == knot.get()
        ).isEmpty();
    }

    public static InteractionResult cleanUpLeash(Level level, BlockPos pos, boolean dropLead) {
        if (level == null || level.isClientSide()) return InteractionResult.PASS;
        getKnot(level, pos).ifPresent(knot -> {
            List<Mob> mobs = level.getEntitiesOfClass(
                    Mob.class,
                    new AABB(pos).inflate(WorkerStats.MAX_MOVEMENT_RADIUS + 4.0D),
                    mob -> mob.getLeashHolder() == knot
            );
            for (Mob mob : mobs) {
                mob.dropLeash(true, dropLead);
            }
            knot.discard();
        });
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult killLeashEntity(Level level, BlockPos pos) {
        return cleanUpLeash(level, pos, true);
    }
}
