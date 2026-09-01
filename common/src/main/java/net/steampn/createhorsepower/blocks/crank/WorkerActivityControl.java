package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import net.steampn.createhorsepower.CHPConstants;

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
 *         mob had before the crank touched it, plus the crank's block position.
 *         Any future interaction with that mob can use {@link #releaseFromMarker}
 *         to restore the original state without needing the original crank.</li>
 * </ul>
 */
public final class WorkerActivityControl {
    /** Tag namespace for the suppression marker. */
    public static final String MARKER_KEY = CHPConstants.MODID + ":crank_ai_suppression";
    /** Inner key for the previous NoAI flag inside the marker. */
    public static final String PREVIOUS_NO_AI_KEY = "PreviousNoAi";
    /** Inner key for the crank's BlockPos inside the marker. */
    public static final String CRANK_POS_KEY = "CrankPos";
    /** Inner key for the owning crank's UUID (BE UUID) inside the marker. */
    public static final String CRANK_UUID_KEY = "CrankUuid";

    private WorkerActivityControl() {}

    /**
     * Acquire crank control of a worker's AI. Writes a persistent marker so the
     * original {@code NoAI} state is recoverable even if the crank BE later
     * disappears before {@link #release} runs.
     *
     * @return {@code true} only if the crank actually changed the mob's
     *         {@code NoAI} state (i.e. owns the suppression and must restore it).
     */
    public static boolean acquire(Mob mob, @Nullable BlockPos crankPos, @Nullable UUID crankUuid) {
        boolean ownsSuppression = !mob.isNoAi();
        writeMarker(mob, ownsSuppression ? false : mob.isNoAi(), crankPos, crankUuid);
        maintain(mob);
        return ownsSuppression;
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
        boolean restoreTo = marker.getBoolean(PREVIOUS_NO_AI_KEY);
        mob.setNoAi(restoreTo);
        clearMarker(mob);
        mob.getNavigation().stop();
        clearHorizontalVelocity(mob);
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
     * Returns {@code true} if this mob already carries a CHP AI suppression
     * marker that was not created by {@code expectedCrankUuid} (or any, if
     * {@code expectedCrankUuid} is {@code null}). Used to detect a stale marker
     * left by a previous crank that has since disappeared.
     */
    public static boolean hasForeignMarker(Mob mob, @Nullable UUID expectedCrankUuid) {
        CompoundTag tag = mob.getPersistentData();
        if (tag == null || !tag.contains(MARKER_KEY)) {
            return false;
        }
        if (expectedCrankUuid == null) {
            return true;
        }
        CompoundTag marker = tag.getCompound(MARKER_KEY);
        return marker.hasUUID(CRANK_UUID_KEY) && !marker.getUUID(CRANK_UUID_KEY).equals(expectedCrankUuid);
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
