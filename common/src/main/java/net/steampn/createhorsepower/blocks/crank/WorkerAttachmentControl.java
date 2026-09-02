package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.steampn.createhorsepower.CHPConstants;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.platform.DeferredDetachStore;
import net.steampn.createhorsepower.utils.CHPDiagnostics;
import net.steampn.createhorsepower.utils.CHPUtils;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Persistent crank attachment ownership, independent from temporary AI suppression. */
public final class WorkerAttachmentControl {
    public static final String MARKER_KEY = CHPConstants.MODID + ":crank_attachment";
    private static final String CRANK_POS_KEY = "CrankPos";
    private static final String CRANK_UUID_KEY = "CrankUuid";

    public enum RecoveryResult {
        NONE,
        VALID,
        RECOVERED,
        DEFERRED
    }

    private WorkerAttachmentControl() {}

    public static boolean hasMarker(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag != null && tag.contains(MARKER_KEY);
    }

    public static void markAttached(Mob mob, BlockPos crankPos, UUID crankUuid) {
        CompoundTag marker = new CompoundTag();
        marker.putLong(CRANK_POS_KEY, crankPos.asLong());
        marker.putUUID(CRANK_UUID_KEY, crankUuid);
        mob.getPersistentData().put(MARKER_KEY, marker);
        CHPDiagnostics.event("attachment_marker_written", mob.level(), crankPos, crankUuid, mob, "");
    }

    public static void clearIfOwnedBy(Mob mob, UUID crankUuid) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null || !tag.contains(MARKER_KEY)) return;
        CompoundTag marker = tag.getCompound(MARKER_KEY);
        if (marker.hasUUID(CRANK_UUID_KEY) && crankUuid.equals(marker.getUUID(CRANK_UUID_KEY))) {
            BlockPos crankPos = marker.contains(CRANK_POS_KEY) ? BlockPos.of(marker.getLong(CRANK_POS_KEY)) : null;
            tag.remove(MARKER_KEY);
            CHPDiagnostics.event("attachment_marker_cleared", mob.level(), crankPos, crankUuid, mob, "");
        }
    }

    @Nullable
    public static BlockPos markerCrankPos(Mob mob) {
        if (!hasMarker(mob)) return null;
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        return marker.contains(CRANK_POS_KEY) ? BlockPos.of(marker.getLong(CRANK_POS_KEY)) : null;
    }

    @Nullable
    public static UUID markerCrankUuid(Mob mob) {
        if (!hasMarker(mob)) return null;
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        return marker.hasUUID(CRANK_UUID_KEY) ? marker.getUUID(CRANK_UUID_KEY) : null;
    }

    /**
     * Recovers a persisted leash after vanilla has had at least one entity tick
     * to restore delayed leash data. Explicit unloaded-detach records are level
     * SavedData and can be resolved without the old crank chunk. Ambiguous
     * orphan state is deferred so a still-valid owner gets a chance to load.
     */
    public static RecoveryResult recoverIfOrphaned(Mob mob, ServerLevel level) {
        if (!hasMarker(mob)) return RecoveryResult.NONE;

        BlockPos crankPos = markerCrankPos(mob);
        UUID crankUuid = markerCrankUuid(mob);
        if (crankPos == null || crankUuid == null) {
            CHPDiagnostics.warnInvariant("malformed_attachment_marker", level, crankPos,
                    "worker=" + mob.getUUID());
            mob.getPersistentData().remove(MARKER_KEY);
            CHPApi.deferredDetaches().remove(level, mob.getUUID());
            return RecoveryResult.RECOVERED;
        }

        DeferredDetachStore.Entry durable = CHPApi.deferredDetaches().get(level, mob.getUUID());
        if (durable != null && durable.matches(crankPos, crankUuid)) {
            releasePersistedCrankLeash(mob, level, crankPos, durable.dropLead(), false);
            releaseActivityIfOwnedBy(mob, crankUuid);
            CHPApi.deferredDetaches().remove(level, mob.getUUID());
            mob.getPersistentData().remove(MARKER_KEY);
            CHPDiagnostics.event("attachment_recovered", level, crankPos, crankUuid, mob,
                    "dropLead=" + durable.dropLead() + " durable_policy=true");
            return RecoveryResult.RECOVERED;
        }

        // Never force-load the old crank solely to resolve ambiguous recovery state.
        if (!level.hasChunkAt(crankPos)) {
            return RecoveryResult.DEFERRED;
        }

        if (level.getBlockEntity(crankPos) instanceof AbstractHorseCrankBlockEntity crank
                && crankUuid.equals(crank.engine().crankInstanceUuid())
                && crank.engine().isAssignedWorker(mob.getUUID())) {
            CHPDiagnostics.event("attachment_recovery_valid", level, crankPos, crankUuid, mob, "owner_still_live=true");
            return RecoveryResult.VALID;
        }

        // Backward-compatible migration path for worlds written before the
        // level SavedData store existed. New detach requests are persisted at level scope.
        boolean dropLead = true;
        boolean hasLegacyDeferredPolicy = level.getBlockEntity(crankPos) instanceof AbstractHorseCrankBlockEntity crank
                && crankUuid.equals(crank.engine().crankInstanceUuid())
                && crank.engine().hasDeferredDetachPolicy(mob.getUUID());
        if (hasLegacyDeferredPolicy) {
            AbstractHorseCrankBlockEntity crank = (AbstractHorseCrankBlockEntity) level.getBlockEntity(crankPos);
            dropLead = crank.engine().deferredDetachDropLead(mob.getUUID());
        }

        releasePersistedCrankLeash(mob, level, crankPos, dropLead, true);

        if (hasLegacyDeferredPolicy) {
            AbstractHorseCrankBlockEntity crank = (AbstractHorseCrankBlockEntity) level.getBlockEntity(crankPos);
            crank.engine().consumeDeferredDetachPolicy(mob.getUUID());
        }
        CHPApi.deferredDetaches().remove(level, mob.getUUID());
        CHPDiagnostics.event("attachment_recovered", level, crankPos, crankUuid, mob,
                "dropLead=" + dropLead + " legacy_deferred_policy=" + hasLegacyDeferredPolicy);
        mob.getPersistentData().remove(MARKER_KEY);
        return RecoveryResult.RECOVERED;
    }

    /**
     * Bounded orphan fallback. It deliberately performs no block-entity lookup
     * and therefore cannot force-load the old crank chunk. A foreign/current
     * leash is preserved; otherwise CHP's persisted crank leash is relinquished
     * with the documented orphan default of dropping the lead.
     */
    public static void recoverAfterTimeout(Mob mob, ServerLevel level) {
        if (!hasMarker(mob)) {
            CHPApi.deferredDetaches().remove(level, mob.getUUID());
            return;
        }

        BlockPos crankPos = markerCrankPos(mob);
        UUID crankUuid = markerCrankUuid(mob);
        if (crankPos != null) {
            releasePersistedCrankLeash(mob, level, crankPos, true, false);
        }
        if (crankUuid != null) {
            releaseActivityIfOwnedBy(mob, crankUuid);
        }
        CHPApi.deferredDetaches().remove(level, mob.getUUID());
        mob.getPersistentData().remove(MARKER_KEY);
        CHPDiagnostics.event("attachment_recovery_timeout", level, crankPos, crankUuid, mob,
                "dropLead=true old_chunk_force_loaded=false");
    }

    private static void releaseActivityIfOwnedBy(Mob mob, UUID crankUuid) {
        if (crankUuid.equals(WorkerActivityControl.markerCrankUuid(mob))) {
            WorkerActivityControl.releaseFromMarker(mob);
        }
    }

    private static void releasePersistedCrankLeash(
            Mob mob,
            ServerLevel level,
            BlockPos crankPos,
            boolean dropLead,
            boolean mayInspectOldChunk
    ) {
        Entity holder = mob.getLeashHolder();
        if (holder instanceof LeashFenceKnotEntity knot && knot.blockPosition().equals(crankPos)) {
            mob.dropLeash(true, dropLead);
            if (mayInspectOldChunk && knot.isAlive() && !CHPUtils.hasAttachedWorker(level, crankPos)) {
                knot.discard();
            }
            return;
        }

        if (holder == null) {
            // In both supported vanilla lines a persisted BlockPos leash can be
            // present while getLeashHolder() is still null. Replacing it with a
            // non-networked temporary holder before dropLeash clears that delayed
            // leash state without resolving or force-loading the old knot chunk.
            mob.setLeashedTo(mob, false);
            mob.dropLeash(true, dropLead);
        }
        // A non-matching live holder belongs to someone/something else; preserve it.
    }
}
