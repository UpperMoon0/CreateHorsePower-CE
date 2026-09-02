package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Defers worker recovery until vanilla has completed at least one entity tick. */
public final class WorkerRecoveryQueue {
    private record Pending(Mob mob, ServerLevel level, int initialTickCount) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private WorkerRecoveryQueue() {}

    public static void enqueue(Mob mob, ServerLevel level) {
        if (!WorkerAttachmentControl.hasMarker(mob) && !WorkerActivityControl.hasMarker(mob)) return;
        PENDING.put(mob.getUUID(), new Pending(mob, level, mob.tickCount));
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

            if (attachment != WorkerAttachmentControl.RecoveryResult.DEFERRED && !activityDeferred) {
                it.remove();
            }
        }
    }
}
