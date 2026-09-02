package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.phys.Vec3;

import net.steampn.createhorsepower.CHPConstants;
import net.steampn.createhorsepower.utils.CHPUtils;
import net.steampn.createhorsepower.utils.CHPDiagnostics;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Temporarily prevents vanilla mob AI from competing with crank-driven movement.
 *
 * <p>Suppression is two-sided so the original {@code NoAI} state is recoverable even
 * if the crank block entity disappears (block broken, chunk unloaded, etc.):
 * <ul>
 *     <li>The crank tracks ownership in its own state for the fast happy path.</li>
 *     <li>A small persistent marker is also written to the mob's
 *         {@linkplain Mob#getPersistentData() persistent data} under
 *         {@link CHPConstants#MODID}. The marker records the {@code NoAI} state the
 *         mob had before the crank touched it, plus the crank's block position
 *         and the crank's instance UUID.</li>
 * </ul>
 *
 * <p>Marker invariant: once the marker records a worker's original {@code NoAI}
 * state, that value must not be overwritten while the marker remains, even if
 * {@link #acquire} is called again from the same crank identity. Ownership is
 * the composite {@code (BlockPos, instance UUID)} rather than UUID alone so a
 * vanilla/NBT-cloned crank cannot impersonate the original owner.
 */
public final class WorkerActivityControl {
    /** Tag namespace for the suppression marker. */
    public static final String MARKER_KEY = CHPConstants.MODID + ":crank_ai_suppression";
    /** Inner key for the previous NoAI flag inside the marker. */
    public static final String PREVIOUS_NO_AI_KEY = "PreviousNoAi";
    /** Inner key for the crank's BlockPos inside the marker. */
    public static final String CRANK_POS_KEY = "CrankPos";
    /** Inner key for the owning crank's instance UUID inside the marker. */
    public static final String CRANK_UUID_KEY = "CrankUuid";

    private WorkerActivityControl() {}

    /**
     * @return {@code true} when this mob already carries a CHP AI suppression marker.
     */
    public static boolean hasMarker(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag != null && tag.contains(MARKER_KEY);
    }

    /** UUID component of the crank identity that issued the marker, or {@code null} if missing/malformed. */
    @Nullable
    public static UUID markerCrankUuid(Mob mob) {
        if (!hasMarker(mob)) {
            return null;
        }
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        return marker.hasUUID(CRANK_UUID_KEY) ? marker.getUUID(CRANK_UUID_KEY) : null;
    }

    /** Position component of the crank identity that issued the marker, or {@code null} if missing/malformed. */
    @Nullable
    public static BlockPos markerCrankPos(Mob mob) {
        if (!hasMarker(mob)) {
            return null;
        }
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        return marker.contains(CRANK_POS_KEY) ? BlockPos.of(marker.getLong(CRANK_POS_KEY)) : null;
    }

    /**
     * Returns whether the current marker is owned by this exact crank identity.
     * Current CE markers require both position and UUID to match. A caller that
     * intentionally passes a null position gets the legacy UUID-only comparison.
     */
    public static boolean isOwnedBy(
            Mob mob,
            @Nullable BlockPos expectedCrankPos,
            @Nullable UUID expectedCrankUuid
    ) {
        if (!hasMarker(mob) || expectedCrankUuid == null) {
            return false;
        }
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        if (!marker.hasUUID(CRANK_UUID_KEY)
                || !expectedCrankUuid.equals(marker.getUUID(CRANK_UUID_KEY))) {
            return false;
        }
        if (expectedCrankPos == null) {
            return true;
        }
        return marker.contains(CRANK_POS_KEY)
                && expectedCrankPos.equals(BlockPos.of(marker.getLong(CRANK_POS_KEY)));
    }

    /**
     * Acquire crank control of a worker's AI. Writes a persistent marker so the
     * original {@code NoAI} state is recoverable even if the crank BE later
     * disappears before {@link #release} runs.
     *
     * <p>If a marker already exists for this exact crank identity, the previously
     * recorded {@code NoAI} state is preserved exactly: this call records what
     * the marker already says, not the mob's current {@code NoAI}. A marker from
     * a different position and/or UUID is recovered first, then the live state
     * becomes the new baseline.
     *
     * @return {@code true} only if the crank actually changed the mob's
     *         {@code NoAI} state (i.e. owns the suppression and must restore it).
     */
    public static boolean acquire(
            Mob mob,
            @Nullable BlockPos crankPos,
            @Nullable UUID crankUuid
    ) {
        boolean previousNoAi;

        if (hasMarker(mob)) {
            CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
            previousNoAi = marker.getBoolean(PREVIOUS_NO_AI_KEY);

            // Do not silently steal a marker from another crank. UUID alone is
            // insufficient because vanilla /clone and NBT tooling can duplicate
            // a block entity's persisted instance UUID at a new position.
            if (crankUuid != null && hasForeignMarker(mob, crankPos, crankUuid)) {
                releaseFromMarker(mob);
                previousNoAi = mob.isNoAi();
            }
        } else {
            previousNoAi = mob.isNoAi();
        }

        writeMarker(mob, previousNoAi, crankPos, crankUuid);
        maintain(mob);
        CHPDiagnostics.event("ai_suppression_acquired", mob.level(), crankPos, crankUuid, mob,
                "previous_no_ai=" + previousNoAi + " owns_change=" + (!previousNoAi));

        return !previousNoAi;
    }

    /** Same as {@link #acquire(Mob, BlockPos, UUID)} without positional metadata. */
    public static boolean acquire(Mob mob) {
        return acquire(mob, null, null);
    }

    public static void maintain(Mob mob) {
        mob.setNoAi(true);
        mob.getNavigation().stop();
        clearHorizontalVelocity(mob);
    }

    /**
     * Release crank AI control. The mob is only restored to its previous state
     * when {@code ownedByCrank} is {@code true}. In every case the navigation is
     * stopped and the horizontal velocity is cleared so the mob does not
     * continue flying along its last orbit vector.
     */
    public static void release(Mob mob, boolean ownedByCrank) {
        BlockPos crankPos = markerCrankPos(mob);
        UUID crankUuid = markerCrankUuid(mob);
        if (ownedByCrank) {
            // Prefer the marker: the BE-level state can disagree with the
            // mob's persistent marker if the worker was reassigned or
            // recovered from a stale marker on attach.
            boolean restoreTo = readPreviousNoAi(mob);
            mob.setNoAi(restoreTo);
        }
        clearMarker(mob);
        mob.getNavigation().stop();
        clearHorizontalVelocity(mob);
        CHPDiagnostics.event("ai_suppression_released", mob.level(), crankPos, crankUuid, mob,
                "owned_by_crank=" + ownedByCrank);
    }

    /**
     * Restore the worker's previous {@code NoAI} state using only the marker
     * left on the mob by a previous {@link #acquire}. Returns {@code true} if
     * a marker was present and consumed.
     *
     * <p>Used as a last-resort recovery path: the original crank block entity
     * has disappeared (block broken, chunk unloaded, etc.) and we still want
     * to make sure the mob does not stay {@code NoAI} forever.
     */
    public static boolean releaseFromMarker(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null || !tag.contains(MARKER_KEY)) {
            return false;
        }
        CompoundTag marker = tag.getCompound(MARKER_KEY);
        BlockPos crankPos = marker.contains(CRANK_POS_KEY) ? BlockPos.of(marker.getLong(CRANK_POS_KEY)) : null;
        UUID crankUuid = marker.hasUUID(CRANK_UUID_KEY) ? marker.getUUID(CRANK_UUID_KEY) : null;
        boolean restoreTo = marker.getBoolean(PREVIOUS_NO_AI_KEY);
        mob.setNoAi(restoreTo);
        clearMarker(mob);
        mob.getNavigation().stop();
        clearHorizontalVelocity(mob);
        CHPDiagnostics.event("ai_orphan_recovered", mob.level(), crankPos, crankUuid, mob,
                "restored_no_ai=" + restoreTo);
        return true;
    }

    /** Returns the previous {@code NoAI} state recorded on the worker, or its current one if no marker exists. */
    public static boolean readPreviousNoAi(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null || !tag.contains(MARKER_KEY)) {
            return mob.isNoAi();
        }
        return tag.getCompound(MARKER_KEY).getBoolean(PREVIOUS_NO_AI_KEY);
    }

    /**
     * Returns {@code true} if this mob carries a marker owned by another crank
     * identity. UUID mismatch is always foreign. When both sides have a position,
     * a position mismatch is also foreign even if an NBT clone duplicated the UUID.
     * Missing legacy position data remains recoverable through the UUID-only path.
     */
    public static boolean hasForeignMarker(
            Mob mob,
            @Nullable BlockPos expectedCrankPos,
            @Nullable UUID expectedCrankUuid
    ) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null || !tag.contains(MARKER_KEY)) {
            return false;
        }
        if (expectedCrankUuid == null) {
            return true;
        }
        CompoundTag marker = tag.getCompound(MARKER_KEY);
        if (!marker.hasUUID(CRANK_UUID_KEY)) {
            return false;
        }
        if (!marker.getUUID(CRANK_UUID_KEY).equals(expectedCrankUuid)) {
            return true;
        }
        return expectedCrankPos != null
                && marker.contains(CRANK_POS_KEY)
                && !expectedCrankPos.equals(BlockPos.of(marker.getLong(CRANK_POS_KEY)));
    }

    /** Legacy UUID-only overload retained for compatibility with existing integrations. */
    public static boolean hasForeignMarker(Mob mob, @Nullable UUID expectedCrankUuid) {
        return hasForeignMarker(mob, null, expectedCrankUuid);
    }

    /**
     * Decide whether a marker-bearing worker is orphaned (the crank that
     * issued the marker is gone or no longer claims this mob) and, if so,
     * recover the worker by restoring its original {@code NoAI} state and
     * removing the marker.
     *
     * <p>This is intentionally conservative:
     * <ul>
     *   <li>It never force-loads chunks.</li>
     *   <li>If the original crank still exists at the recorded position with
     *       a matching instance UUID and still considers this mob its worker,
     *       the marker is left in place so the live crank can finish suppression.</li>
     *   <li>If the marker is missing, the function returns {@code false} and
     *       touches nothing.</li>
     * </ul>
     *
     * @return {@code true} if the marker was consumed to recover the worker.
     */
    public static boolean recoverIfOrphaned(Mob mob, ServerLevel level) {
        if (!hasMarker(mob)) {
            return false;
        }

        BlockPos crankPos = markerCrankPos(mob);
        UUID expectedUuid = markerCrankUuid(mob);

        // Malformed/legacy marker: safest option is recovery.
        if (crankPos == null || expectedUuid == null) {
            return releaseFromMarker(mob);
        }

        // Never force-load chunks just to inspect a marker.
        if (!level.hasChunkAt(crankPos)) {
            return false;
        }

        AbstractHorseCrankBlockEntity liveCrank =
                level.getBlockEntity(crankPos) instanceof AbstractHorseCrankBlockEntity crank ? crank : null;

        boolean stillOwned = liveCrank != null
                && expectedUuid.equals(liveCrank.engine().crankInstanceUuid())
                && liveCrank.engine().isAssignedWorker(mob.getUUID());
        if (stillOwned) {
            return false;
        }

        // CE.2 and older workers have no dedicated attachment marker. When
        // this recovery runs post-tick, the AI marker's crank position is
        // enough to clean a stale persisted leash without hard-coding TFC.
        Entity holder = mob.getLeashHolder();
        if (holder instanceof LeashFenceKnotEntity knot && knot.blockPosition().equals(crankPos)) {
            mob.dropLeash(true, true);
            if (knot.isAlive() && !CHPUtils.hasAttachedWorker(level, crankPos)) {
                knot.discard();
            }
        }

        return releaseFromMarker(mob);
    }

    public static void clearHorizontalVelocity(Mob mob) {
        Vec3 movement = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, movement.y, 0.0D);
    }

    private static void writeMarker(Mob mob, boolean previousNoAi, @Nullable BlockPos crankPos, @Nullable UUID crankUuid) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null) {
            return;
        }
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(PREVIOUS_NO_AI_KEY, previousNoAi);
        if (crankPos != null) {
            marker.putLong(CRANK_POS_KEY, crankPos.asLong());
        }
        if (crankUuid != null) {
            marker.putUUID(CRANK_UUID_KEY, crankUuid);
        }
        tag.put(MARKER_KEY, marker);
    }

    private static void clearMarker(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (tag != null) {
            tag.remove(MARKER_KEY);
        }
    }
}
