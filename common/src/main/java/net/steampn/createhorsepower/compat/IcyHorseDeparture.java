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
        if (pos != null && owner != null && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity crank
                && owner.equals(crank.engine().crankInstanceUuid())
                && horse.getUUID().equals(crank.engine().getWorkerUuid())) {
            crank.detachWorker(true);
        } else {
            // The marker contains everything needed to release without loading
            // a chunk, recreating a horse, or touching a replacement crank.
            WorkerAttachmentControl.recoverAfterTimeout(horse, level);
        }
        // A conflicting activity marker is owned by another crank and survives.
        if (WorkerActivityControl.isOwnedBy(horse, pos, owner)) {
            WorkerActivityControl.releaseFromMarker(horse);
        }
        WorkerRecoveryQueue.cancelRecovery(horse);
    }
}
