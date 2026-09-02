package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.steampn.createhorsepower.utils.CHPDiagnostics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Defers worker recovery until vanilla has completed at least one entity tick. */
public final class WorkerRecoveryQueue {
    private record Pending(Mob mob, ServerLevel level, int initialTickCount, long enqueuedGameTime) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> NEXT_DEFERRED_LOG = new HashMap<>();
    private static final long DEFERRED_REMINDER_TICKS = 1200L;
    public static final long RECOVERY_TIMEOUT_TICKS = 1200L;

    private WorkerRecoveryQueue() {}

    public static boolean shouldLogDeferred(long gameTime, long nextLogTick) {
        return gameTime >= nextLogTick;
    }

    public static long nextDeferredReminder(long gameTime) {
        return gameTime + DEFERRED_REMINDER_TICKS;
    }

    public static boolean isRecoveryExpired(long enqueuedGameTime, long gameTime) {
        return gameTime - enqueuedGameTime >= RECOVERY_TIMEOUT_TICKS;
    }

    public static void enqueue(Mob mob, ServerLevel level) {
        if (!WorkerAttachmentControl.hasMarker(mob) && !WorkerActivityControl.hasMarker(mob)) return;
        PENDING.put(mob.getUUID(), new Pending(mob, level, mob.tickCount, level.getGameTime()));
        NEXT_DEFERRED_LOG.put(mob.getUUID(), 0L);
        CHPDiagnostics.event("recovery_enqueued", level, WorkerAttachmentControl.markerCrankPos(mob),
                WorkerAttachmentControl.markerCrankUuid(mob), mob, "tick=" + mob.tickCount);
    }

    /** Called from the loader's level-tick POST/END event. */
    public static void process(ServerLevel level) {
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending pending = entry.getValue();
            if (pending.level() != level) continue;

            Mob mob = pending.mob();
            if (level.getEntity(entry.getKey()) != mob) {
                // If it unloads again before recovery, the next join will enqueue it again.
                it.remove();
                NEXT_DEFERRED_LOG.remove(entry.getKey());
                continue;
            }
            // EntityJoinLevel can occur after this entity's tick slot. Requiring
            // tickCount to advance proves vanilla's delayed leash restoration ran.
            if (mob.tickCount <= pending.initialTickCount()) {
                continue;
            }

            WorkerAttachmentControl.RecoveryResult attachment =
                    WorkerAttachmentControl.recoverIfOrphaned(mob, level);
            WorkerActivityControl.recoverIfOrphaned(mob, level);

            BlockPos activityCrankPos = WorkerActivityControl.markerCrankPos(mob);
            boolean activityDeferred = WorkerActivityControl.hasMarker(mob)
                    && activityCrankPos != null
                    && !level.hasChunkAt(activityCrankPos);

            boolean deferred = attachment == WorkerAttachmentControl.RecoveryResult.DEFERRED || activityDeferred;
            long now = level.getGameTime();
            if (deferred && isRecoveryExpired(pending.enqueuedGameTime(), now)) {
                BlockPos recoveryPos = WorkerAttachmentControl.markerCrankPos(mob) != null
                        ? WorkerAttachmentControl.markerCrankPos(mob)
                        : activityCrankPos;
                UUID recoveryCrank = WorkerAttachmentControl.markerCrankUuid(mob) != null
                        ? WorkerAttachmentControl.markerCrankUuid(mob)
                        : WorkerActivityControl.markerCrankUuid(mob);

                // No old-crank block/chunk lookup is allowed below this point.
                WorkerAttachmentControl.recoverAfterTimeout(mob, level);
                if (WorkerActivityControl.hasMarker(mob)) {
                    WorkerActivityControl.releaseFromMarker(mob);
                }
                CHPDiagnostics.event("recovery_timeout", level, recoveryPos, recoveryCrank, mob,
                        "age_ticks=" + (now - pending.enqueuedGameTime()) + " old_chunk_force_loaded=false");
                it.remove();
                NEXT_DEFERRED_LOG.remove(entry.getKey());
                continue;
            }

            if (deferred) {
                long next = NEXT_DEFERRED_LOG.getOrDefault(entry.getKey(), 0L);
                if (shouldLogDeferred(now, next)) {
                    CHPDiagnostics.event("recovery_deferred", level,
                            WorkerAttachmentControl.markerCrankPos(mob) != null
                                    ? WorkerAttachmentControl.markerCrankPos(mob)
                                    : activityCrankPos,
                            WorkerAttachmentControl.markerCrankUuid(mob), mob,
                            "next_reminder_ticks=" + DEFERRED_REMINDER_TICKS
                                    + " timeout_ticks=" + RECOVERY_TIMEOUT_TICKS);
                    NEXT_DEFERRED_LOG.put(entry.getKey(), nextDeferredReminder(now));
                }
            } else {
                CHPDiagnostics.event("recovery_complete", level, null, null, mob, "result=" + attachment);
                it.remove();
                NEXT_DEFERRED_LOG.remove(entry.getKey());
            }
        }
    }

    public static boolean isPendingForTesting(UUID workerUuid) {
        return PENDING.containsKey(workerUuid);
    }

    /** Moves one queued entry to the timeout boundary without advancing the world clock. */
    public static void expireForTesting(UUID workerUuid) {
        Pending pending = PENDING.get(workerUuid);
        if (pending != null) {
            PENDING.put(workerUuid, new Pending(
                    pending.mob(),
                    pending.level(),
                    pending.initialTickCount(),
                    pending.level().getGameTime() - RECOVERY_TIMEOUT_TICKS
            ));
        }
    }
}
