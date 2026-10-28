package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;

import java.util.UUID;

/** A successful Icy recall/send-home releases CHP control before the next crank tick. */
public final class IcyHorseDeparture {
    private IcyHorseDeparture() {}

    public static void release(AbstractHorse horse) {
        if (!(horse.level() instanceof ServerLevel level)
                || level.getEntity(horse.getUUID()) != horse
                || !WorkerAttachmentControl.hasMarker(horse)) return;
        BlockPos pos = WorkerAttachmentControl.markerCrankPos(horse);
        UUID owner = WorkerAttachmentControl.markerCrankUuid(horse);
        String sourceDimension = horse.getPersistentData().getCompound(WorkerAttachmentControl.MARKER_KEY)
                .getString("CrankDimension");
        AbstractHorseCrankBlockEntity source = null;
        ServerLevel sourceLevel = null;
        if (pos != null && owner != null) {
            for (ServerLevel candidate : level.getServer().getAllLevels()) {
                if (!sourceDimension.isEmpty() && !sourceDimension.equals(candidate.dimension().location().toString())) continue;
                if (!candidate.hasChunkAt(pos)) continue;
                if (candidate.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity crank
                        && owner.equals(crank.engine().crankInstanceUuid())
                        && horse.getUUID().equals(crank.engine().getWorkerUuid())) {
                    // Legacy markers have no dimension. Ambiguous cloned crank
                    // identities must never let a recall mutate multiple machines.
                    if (source != null) { source = null; sourceLevel = null; break; }
                    source = crank;
                    sourceLevel = candidate;
                }
            }
        }
        if (source != null) {
            // A source in another dimension cannot resolve this registered body.
            // Preserve a no-drop policy for any stale original body; return the
            // one attachment from the actual recalled body below instead.
            source.detachWorker(sourceLevel == level);
        }
        if (WorkerAttachmentControl.hasMarker(horse)) {
            WorkerAttachmentControl.recoverAfterTimeout(horse, level);
        }
        // A conflicting activity marker is owned by another crank and survives.
        if (WorkerActivityControl.isOwnedBy(horse, pos, owner)) {
            WorkerActivityControl.releaseFromMarker(horse);
        }
        if (WorkerAttachmentControl.hasMarker(horse) || WorkerActivityControl.hasMarker(horse)) {
            WorkerRecoveryQueue.enqueue(horse, level);
        } else {
            WorkerRecoveryQueue.cancelRecovery(horse);
        }
    }
}
