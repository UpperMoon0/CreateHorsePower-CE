package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.steampn.createhorsepower.CHPConstants;
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
    }

    public static void clearIfOwnedBy(Mob mob, UUID crankUuid) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null || !tag.contains(MARKER_KEY)) return;
        CompoundTag marker = tag.getCompound(MARKER_KEY);
        if (marker.hasUUID(CRANK_UUID_KEY) && crankUuid.equals(marker.getUUID(CRANK_UUID_KEY))) {
            tag.remove(MARKER_KEY);
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
     * Recovers a persisted leash after vanilla has restored its delayed knot reference.
     * Never force-loads the old crank chunk and never drops a leash that now belongs
     * to a player or a different knot.
     */
    public static RecoveryResult recoverIfOrphaned(Mob mob, ServerLevel level) {
        if (!hasMarker(mob)) return RecoveryResult.NONE;

        BlockPos crankPos = markerCrankPos(mob);
        UUID crankUuid = markerCrankUuid(mob);
        if (crankPos == null || crankUuid == null) {
            mob.getPersistentData().remove(MARKER_KEY);
            return RecoveryResult.RECOVERED;
        }

        // Never force-load the old crank solely to resolve recovery state.
        if (!level.hasChunkAt(crankPos)) {
            return RecoveryResult.DEFERRED;
        }

        if (level.getBlockEntity(crankPos) instanceof AbstractHorseCrankBlockEntity crank
                && crankUuid.equals(crank.engine().crankInstanceUuid())
                && crank.engine().isAssignedWorker(mob.getUUID())) {
            return RecoveryResult.VALID;
        }

        Entity holder = mob.getLeashHolder();
        if (holder instanceof LeashFenceKnotEntity knot && knot.blockPosition().equals(crankPos)) {
            // The original detach could not drop this lead while the worker was
            // unloaded, so recovery returns it now.
            mob.dropLeash(true, true);
            if (knot.isAlive() && !CHPUtils.hasAttachedWorker(level, crankPos)) {
                knot.discard();
            }
        }

        mob.getPersistentData().remove(MARKER_KEY);
        return RecoveryResult.RECOVERED;
    }
}
