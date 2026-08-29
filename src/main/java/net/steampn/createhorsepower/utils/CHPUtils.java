package net.steampn.createhorsepower.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.config.Config;

import java.util.List;

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
        String key = EntityType.getKey(type).toString();
        if (type.is(CHPTags.Entities.WORKERS_SMALL) || type.is(CHPTags.Entities.SMALL_WORKER_TAG) || Config.SMALL_CREATURES.get().contains(key)) {
            return WorkerTier.SMALL;
        }
        if (type.is(CHPTags.Entities.WORKERS_MEDIUM) || type.is(CHPTags.Entities.MEDIUM_WORKER_TAG) || Config.MEDIUM_CREATURES.get().contains(key)) {
            return WorkerTier.MEDIUM;
        }
        if (type.is(CHPTags.Entities.WORKERS_LARGE) || type.is(CHPTags.Entities.LARGE_WORKER_TAG) || Config.LARGE_CREATURES.get().contains(key)) {
            return WorkerTier.LARGE;
        }
        return WorkerTier.NONE;
    }

    public static java.util.Optional<LeashFenceKnotEntity> getKnot(Level level, BlockPos pos) {
        if (level == null) return java.util.Optional.empty();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        List<LeashFenceKnotEntity> knots = level.getEntitiesOfClass(
                LeashFenceKnotEntity.class,
                new AABB(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)
        );
        for (LeashFenceKnotEntity knot : knots) {
            if (knot.getPos().equals(pos)) {
                return java.util.Optional.of(knot);
            }
        }
        return java.util.Optional.empty();
    }

    public static InteractionResult cleanUpLeash(Level level, BlockPos pos, boolean dropLead) {
        if (level == null || level.isClientSide()) return InteractionResult.PASS;
        getKnot(level, pos).ifPresent(knot -> {
            List<Mob> mobs = level.getEntitiesOfClass(
                    Mob.class,
                    new AABB(pos).inflate(36.0D),
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
