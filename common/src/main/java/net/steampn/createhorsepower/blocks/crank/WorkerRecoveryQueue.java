package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.steampn.createhorsepower.CHPConstants;
import net.steampn.createhorsepower.utils.CHPDiagnostics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Defers worker recovery until vanilla has completed at least one entity tick. */
public final class WorkerRecoveryQueue {
    private static final String RECOVERY_STATE_KEY = CHPConstants.MODID + ":recovery";
    private static final String RECOVERY_STARTED_GAME_TIME_KEY = "StartedGameTime";

    private record Pending(Mob mob, ServerLevel level, int initialTickCount) {}
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

    public static boolean isRecoveryExpired(long recoveryStartedGameTime, long gameTime) {
        return gameTime - recoveryStartedGameTime >= RECOVERY_TIMEOUT_TICKS;
    }

    /**
     * Queue state is intentionally transient, but the timeout clock is stored
     * on the worker so chunk unload/reload cannot reset orphan recovery age.
     */
    public static void enqueue(Mob mob, ServerLevel level) {
        if (!WorkerAttachmentControl.hasMarker(mob) && !WorkerActivityControl.hasMarker(mob)) {
            cancelRecovery(mob);
            return;
        }
        long started = ensureRecoveryStartedGameTime(mob, level.getGameTime());
        PENDING.put(mob.getUUID(), new Pending(mob, level, mob.tickCount));
        NEXT_DEFERRED_LOG.put(mob.getUUID(), 0L);
        CHPDiagnostics.event("recovery_enqueued", level, WorkerAttachmentControl.markerCrankPos(mob),
                WorkerAttachmentControl.markerCrankUuid(mob), mob,
                "tick=" + mob.tickCount + " recovery_started=" + started);
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
                // The durable recovery clock stays on the worker's persistent
                // data. A later EntityJoinLevel only re-schedules execution.
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
            long recoveryStarted = ensureRecoveryStartedGameTime(mob, now);
            if (deferred && isRecoveryExpired(recoveryStarted, now)) {
                BlockPos recoveryPos = WorkerAttachmentControl.markerCrankPos(mob) != null
                        ? WorkerAttachmentControl.markerCrankPos(mob)
                        : activityCrankPos;
                UUID recoveryCrank = WorkerAttachmentControl.markerCrankUuid(mob) != null
                        ? WorkerAttachmentControl.markerCrankUuid(mob)
                        : WorkerActivityControl.markerCrankUuid(mob);

                // No old-crank block/chunk lookup is allowed below this point.
                // When an attachment marker still exists, recoverAfterTimeout()
                // releases only activity state owned by that exact attachment
                // identity. Any activity marker left afterwards belongs to a
                // different/newer crank and must survive. With no attachment
                // marker, this is an activity-only timeout and the remaining
                // marker is the state this queue entry is explicitly recovering.
                boolean hadAttachmentMarker = WorkerAttachmentControl.hasMarker(mob);
                WorkerAttachmentControl.recoverAfterTimeout(mob, level);
                if (!hadAttachmentMarker && WorkerActivityControl.hasMarker(mob)) {
                    WorkerActivityControl.releaseFromMarker(mob);
                }
                CHPDiagnostics.event("recovery_timeout", level, recoveryPos, recoveryCrank, mob,
                        "age_ticks=" + Math.max(0L, now - recoveryStarted) + " old_chunk_force_loaded=false");
                clearRecoveryClock(mob);
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
                                    + " timeout_ticks=" + RECOVERY_TIMEOUT_TICKS
                                    + " age_ticks=" + Math.max(0L, now - recoveryStarted));
                    NEXT_DEFERRED_LOG.put(entry.getKey(), nextDeferredReminder(now));
                }
            } else {
                CHPDiagnostics.event("recovery_complete", level, null, null, mob, "result=" + attachment);
                clearRecoveryClock(mob);
                it.remove();
                NEXT_DEFERRED_LOG.remove(entry.getKey());
            }
        }
    }

    /** Clears only the durable timeout clock; safe while iterating the queue. */
    public static void clearRecoveryClock(Mob mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (persistent != null) {
            persistent.remove(RECOVERY_STATE_KEY);
        }
    }

    /** A successful new attachment supersedes any queued or durable recovery. */
    public static void cancelRecovery(Mob mob) {
        clearRecoveryClock(mob);
        PENDING.remove(mob.getUUID());
        NEXT_DEFERRED_LOG.remove(mob.getUUID());
    }

    /**
     * Drop only process-local queue state. Persistent recovery clocks stay on
     * the mobs so a later world load resumes the same bounded recovery age.
     * Loader server-stopped hooks call this to avoid retaining an integrated
     * server's ServerLevel/entity graph from static maps after returning to menu.
     */
    public static void clearTransientState() {
        PENDING.clear();
        NEXT_DEFERRED_LOG.clear();
    }

    private static long ensureRecoveryStartedGameTime(Mob mob, long now) {
        CompoundTag persistent = mob.getPersistentData();
        CompoundTag recovery = persistent.getCompound(RECOVERY_STATE_KEY);
        if (recovery.contains(RECOVERY_STARTED_GAME_TIME_KEY)) {
            long started = recovery.getLong(RECOVERY_STARTED_GAME_TIME_KEY);
            // A restored backup may move game time backwards. Rebase rather
            // than treating a negative age as an effectively infinite defer.
            if (started <= now) {
                return started;
            }
        }

        recovery = new CompoundTag();
        recovery.putLong(RECOVERY_STARTED_GAME_TIME_KEY, now);
        persistent.put(RECOVERY_STATE_KEY, recovery);
        return now;
    }

    private static void setRecoveryStartedGameTime(Mob mob, long started) {
        CompoundTag recovery = new CompoundTag();
        recovery.putLong(RECOVERY_STARTED_GAME_TIME_KEY, started);
        mob.getPersistentData().put(RECOVERY_STATE_KEY, recovery);
    }

    public static boolean isPendingForTesting(UUID workerUuid) {
        return PENDING.containsKey(workerUuid);
    }

    public static long recoveryAgeForTesting(Mob mob, long gameTime) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(RECOVERY_STATE_KEY)) {
            return 0L;
        }
        CompoundTag recovery = persistent.getCompound(RECOVERY_STATE_KEY);
        if (!recovery.contains(RECOVERY_STARTED_GAME_TIME_KEY)) {
            return 0L;
        }
        return Math.max(0L, gameTime - recovery.getLong(RECOVERY_STARTED_GAME_TIME_KEY));
    }

    /** Adds durable recovery age without advancing the world clock. */
    public static void advanceRecoveryAgeForTesting(Mob mob, long ticks) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        long started = ensureRecoveryStartedGameTime(mob, now);
        long adjusted = started - Math.max(0L, ticks);
        setRecoveryStartedGameTime(mob, adjusted);
    }

    /** Moves one queued entry to the timeout boundary without advancing the world clock. */
    public static void expireForTesting(UUID workerUuid) {
        Pending pending = PENDING.get(workerUuid);
        if (pending != null) {
            setRecoveryStartedGameTime(
                    pending.mob(),
                    pending.level().getGameTime() - RECOVERY_TIMEOUT_TICKS
            );
        }
    }
}
