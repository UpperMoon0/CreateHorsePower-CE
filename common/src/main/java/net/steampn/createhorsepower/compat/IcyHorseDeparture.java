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
                || (!WorkerAttachmentControl.hasMarker(horse) && !WorkerActivityControl.hasMarker(horse))) return;
        boolean attachment = WorkerAttachmentControl.hasMarker(horse);
        BlockPos pos = attachment ? WorkerAttachmentControl.markerCrankPos(horse) : WorkerActivityControl.markerCrankPos(horse);
        UUID owner = attachment ? WorkerAttachmentControl.markerCrankUuid(horse) : WorkerActivityControl.markerCrankUuid(horse);
        String sourceDimension = horse.getPersistentData().getCompound(attachment
                ? WorkerAttachmentControl.MARKER_KEY : WorkerActivityControl.MARKER_KEY).getString("CrankDimension");
        AbstractHorseCrankBlockEntity source = null;
        ServerLevel sourceLevel = null;
        boolean dropAttachment = true;
        if (pos != null && owner != null) {
            for (ServerLevel candidate : level.getServer().getAllLevels()) {
                if (!sourceDimension.isEmpty() && !sourceDimension.equals(candidate.dimension().location().toString())) continue;
                if (candidate != level && !sourceDimension.isEmpty()) {
                    var existing = net.steampn.createhorsepower.platform.CHPApi.deferredDetaches().get(candidate, horse.getUUID());
                    // An older settled no-drop policy applies to this recalled
                    // snapshot too. The guard we write below protects a stale
                    // original body only, so it must not suppress the first refund.
                    dropAttachment = net.steampn.createhorsepower.platform.DeferredDetachStore.effectiveDropPolicy(
                            existing, pos, owner, dropAttachment);
                    if (existing == null || existing.matches(pos, owner)) {
                        net.steampn.createhorsepower.platform.CHPApi.deferredDetaches().put(candidate, horse.getUUID(),
                                new net.steampn.createhorsepower.platform.DeferredDetachStore.Entry(pos, owner, false));
                    }
                }
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
            if (sourceLevel != level && sourceDimension.isEmpty()) {
                // A legacy dimensionless marker can still prove its source
                // when exactly one loaded crank owns this reservation.
                dropAttachment = net.steampn.createhorsepower.platform.DeferredDetachStore.effectiveDropPolicy(
                        net.steampn.createhorsepower.platform.CHPApi.deferredDetaches().get(sourceLevel, horse.getUUID()),
                        pos, owner, dropAttachment);
            }
            // A source in another dimension cannot resolve this registered body.
            // Preserve a no-drop policy for any stale original body; return the
            // one attachment from the actual recalled body below instead.
            source.detachWorker(sourceLevel == level && dropAttachment);
        }
        if (WorkerAttachmentControl.hasMarker(horse)) {
            WorkerAttachmentControl.recoverAfterTimeout(horse, level, dropAttachment);
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
