package net.steampn.createhorsepower.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.platform.DeferredDetachStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        return cleanUpLeash(level, pos, null, dropLead);
    }

    /**
     * Clears the leash owned by a crank. When a worker UUID is known, resolve
     * that exact entity through the server index first so cleanup does not
     * depend on the worker still being inside the crank's fallback search box.
     *
     * The direct release is deliberately guarded by the leash holder's anchor
     * position. A delayed/stale crank cleanup must never detach a worker that
     * has already been attached to a different crank.
     */
    public static InteractionResult cleanUpLeash(Level level, BlockPos pos, UUID workerUuid, boolean dropLead) {
        if (level == null || level.isClientSide()) return InteractionResult.PASS;

        if (workerUuid != null && level instanceof ServerLevel serverLevel) {
            reconcileDurableDetachPolicy(serverLevel, pos, workerUuid, dropLead);
            Entity entity = serverLevel.getEntity(workerUuid);
            if (entity instanceof Mob mob && isLeashedToKnotAt(mob, pos)) {
                CHPDiagnostics.event("leash_cleanup_uuid", level, pos, null, mob, "dropLead=" + dropLead);
                mob.dropLeash(true, dropLead);
            }
        }

        getKnot(level, pos).ifPresent(knot -> {
            List<Mob> mobs = level.getEntitiesOfClass(
                    Mob.class,
                    new AABB(pos).inflate(WorkerStats.MAX_MOVEMENT_RADIUS + 4.0D),
                    mob -> mob.getLeashHolder() == knot
            );
            for (Mob mob : mobs) {
                CHPDiagnostics.event("leash_cleanup_fallback", level, pos, null, mob, "dropLead=" + dropLead);
                mob.dropLeash(true, dropLead);
            }
            CHPDiagnostics.event("leash_knot_discarded", level, pos, null, null, "fallback_workers=" + mobs.size());
            knot.discard();
        });
        return InteractionResult.SUCCESS;
    }

    /** True only when the loaded mob is currently attached to a knot at {@code pos}. */
    public static boolean isLeashedToKnotAt(Mob mob, BlockPos pos) {
        if (!mob.isAlive() || !mob.isLeashed()) return false;
        Entity holder = mob.getLeashHolder();
        return holder instanceof LeashFenceKnotEntity knot && knot.blockPosition().equals(pos);
    }

    /**
     * Persist unloaded-worker detach semantics at level scope. This runs while
     * the owning crank chunk is already loaded, so reading the crank instance
     * UUID here never force-loads anything. A loaded worker is cleaned
     * immediately; only a durable intent owned by this exact crank instance is
     * consumed. A stale record belonging to some other crank must survive for
     * that owner/recovery path to resolve.
     */
    private static void reconcileDurableDetachPolicy(
            ServerLevel level,
            BlockPos crankPos,
            UUID workerUuid,
            boolean dropLead
    ) {
        if (!(level.getBlockEntity(crankPos) instanceof AbstractHorseCrankBlockEntity crank)) {
            return;
        }

        UUID crankUuid = crank.engine().crankInstanceUuid();
        Entity loaded = level.getEntity(workerUuid);
        if (loaded instanceof Mob) {
            DeferredDetachStore.Entry existing = CHPApi.deferredDetaches().get(level, workerUuid);
            if (existing != null && existing.matches(crankPos, crankUuid)) {
                CHPApi.deferredDetaches().remove(level, workerUuid);
            }
            return;
        }

        CHPApi.deferredDetaches().put(
                level,
                workerUuid,
                new DeferredDetachStore.Entry(crankPos, crankUuid, dropLead)
        );
        CHPDiagnostics.event("deferred_detach_persisted", level, crankPos, crankUuid, null,
                "worker_uuid=" + workerUuid + " dropLead=" + dropLead);
    }

    public static InteractionResult killLeashEntity(Level level, BlockPos pos) {
        return cleanUpLeash(level, pos, true);
    }
}
