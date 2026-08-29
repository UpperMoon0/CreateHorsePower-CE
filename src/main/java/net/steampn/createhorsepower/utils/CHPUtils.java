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

    public static InteractionResult cleanUpLeash(Level level, BlockPos pos, boolean dropLead) {
        if (level == null) return InteractionResult.PASS;
        List<LeashFenceKnotEntity> knots = level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(pos).inflate(0.5D));
        for (LeashFenceKnotEntity knot : knots) {
            if (!level.isClientSide()) {
                List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(10.0D), mob -> mob.getLeashHolder() == knot);
                for (Mob mob : mobs) {
                    mob.dropLeash(true, dropLead);
                }
            }
            knot.discard();
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult killLeashEntity(Level level, BlockPos pos) {
        return cleanUpLeash(level, pos, true);
    }
}
