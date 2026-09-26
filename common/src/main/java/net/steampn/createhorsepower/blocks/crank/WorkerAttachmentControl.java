package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.steampn.createhorsepower.content.attachment.AttachmentMode;
import net.steampn.createhorsepower.content.attachment.AttachmentProfile;
import net.steampn.createhorsepower.content.machine.AnimalPowerAccess;
import net.steampn.createhorsepower.CHPConstants;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.platform.DeferredDetachStore;
import net.steampn.createhorsepower.utils.CHPDiagnostics;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Persistent crank attachment ownership, independent from temporary AI suppression. */
public final class WorkerAttachmentControl {
    public static final String MARKER_KEY = CHPConstants.MODID + ":crank_attachment";
    private static final String CRANK_POS_KEY = "CrankPos";
    private static final String CRANK_UUID_KEY = "CrankUuid";
    private static final String MODE_KEY = "Mode";
    private static final String PROFILE_KEY = "Profile";
    private static final String ATTACHMENT_ITEM_KEY = "AttachmentItem";
    private static final String CONSUMED_KEY = "Consumed";
    private static final String DROP_ON_DETACH_KEY = "DropOnDetach";

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
        markAttached(mob, crankPos, crankUuid, null, ItemStack.EMPTY);
    }

    public static void markAttached(Mob mob, BlockPos crankPos, UUID crankUuid,
                                    @Nullable AttachmentProfile profile, ItemStack attachmentStack) {
        // A successful new attachment supersedes every older detach/recovery
        // intent for this worker. Clear both the durable level record and the
        // legacy BE-local migration record before writing new ownership.
        if (mob.level() instanceof ServerLevel serverLevel) {
            DeferredDetachStore.Entry stale = CHPApi.deferredDetaches().remove(serverLevel, mob.getUUID());
            // A marker can be restored from stale worker data before its old
            // crank chunk is available. Never load that chunk merely to clean
            // up the legacy BE-local detach policy.
            if (serverLevel.hasChunkAt(crankPos)
                    && serverLevel.getBlockEntity(crankPos) instanceof AnimalPowerAccess machine
                    && crankUuid.equals(machine.animalPowerEngine().crankInstanceUuid())) {
                machine.animalPowerEngine().consumeDeferredDetachPolicy(mob.getUUID());
            }
            WorkerRecoveryQueue.cancelRecovery(mob);
            if (stale != null) {
                CHPDiagnostics.event("deferred_detach_superseded", serverLevel, crankPos, crankUuid, mob,
                        "old_crank=" + stale.crankUuid() + " old_dropLead=" + stale.dropLead());
            }
        }

        CompoundTag marker = new CompoundTag();
        marker.putLong(CRANK_POS_KEY, crankPos.asLong());
        marker.putUUID(CRANK_UUID_KEY, crankUuid);
        AttachmentMode mode = profile == null ? AttachmentMode.VANILLA_LEASH : profile.mode();
        marker.putString(MODE_KEY, mode.serializedName());
        if (profile != null) {
            marker.putString(PROFILE_KEY, profile.id().toString());
            marker.putBoolean(CONSUMED_KEY, profile.consumeOnAttach());
            marker.putBoolean(DROP_ON_DETACH_KEY, profile.dropOnDetach());
            if (!attachmentStack.isEmpty()) {
                marker.putString(ATTACHMENT_ITEM_KEY, BuiltInRegistries.ITEM.getKey(attachmentStack.getItem()).toString());
            }
        }
        mob.getPersistentData().put(MARKER_KEY, marker);
        CHPDiagnostics.event("attachment_marker_written", mob.level(), crankPos, crankUuid, mob, "");
    }

    /**
     * Returns whether the persistent attachment marker belongs to this exact
     * crank identity. Position is part of the identity because vanilla /clone
     * and NBT tooling can duplicate a block entity's persisted instance UUID.
     */
    public static boolean isOwnedBy(Mob mob, @Nullable BlockPos crankPos, @Nullable UUID crankUuid) {
        if (!hasMarker(mob) || crankPos == null || crankUuid == null) {
            return false;
        }
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        return marker.contains(CRANK_POS_KEY)
                && crankPos.equals(BlockPos.of(marker.getLong(CRANK_POS_KEY)))
                && marker.hasUUID(CRANK_UUID_KEY)
                && crankUuid.equals(marker.getUUID(CRANK_UUID_KEY));
    }

    /** Clear an attachment marker only when both crank position and UUID match. */
    public static void clearIfOwnedBy(Mob mob, BlockPos crankPos, UUID crankUuid) {
        if (!isOwnedBy(mob, crankPos, crankUuid)) {
            return;
        }
        mob.getPersistentData().remove(MARKER_KEY);
        CHPDiagnostics.event("attachment_marker_cleared", mob.level(), crankPos, crankUuid, mob, "");
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
        DeferredDetachStore.Entry durable = CHPApi.deferredDetaches().get(level, mob.getUUID());
        if (crankPos == null || crankUuid == null) {
            CHPDiagnostics.warnInvariant("malformed_attachment_marker", level, crankPos,
                    "worker=" + mob.getUUID());

            // A level-scoped durable detach has complete ownership/drop policy
            // even when an old/corrupt worker marker does not. Honor that
            // stronger record rather than silently losing detach(false).
            if (durable != null) {
                releasePersistedAttachment(mob, level, durable.crankPos(), durable.dropLead());
                releaseActivityIfOwnedBy(mob, durable.crankPos(), durable.crankUuid());
                CHPApi.deferredDetaches().remove(level, mob.getUUID());
            }
            mob.getPersistentData().remove(MARKER_KEY);
            return RecoveryResult.RECOVERED;
        }

        if (durable != null && durable.matches(crankPos, crankUuid)) {
            releasePersistedAttachment(mob, level, crankPos, durable.dropLead());
            releaseActivityIfOwnedBy(mob, crankPos, crankUuid);
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

        if (level.getBlockEntity(crankPos) instanceof AnimalPowerAccess machine
                && crankUuid.equals(machine.animalPowerEngine().crankInstanceUuid())
                && machine.animalPowerEngine().isAssignedWorker(mob.getUUID())) {
            CHPDiagnostics.event("attachment_recovery_valid", level, crankPos, crankUuid, mob, "owner_still_live=true");
            return RecoveryResult.VALID;
        }

        // Backward-compatible migration path for worlds written before the
        // level SavedData store existed. New detach requests are persisted at level scope.
        boolean dropLead = true;
        boolean hasLegacyDeferredPolicy = level.getBlockEntity(crankPos) instanceof AnimalPowerAccess machine
                && crankUuid.equals(machine.animalPowerEngine().crankInstanceUuid())
                && machine.animalPowerEngine().hasDeferredDetachPolicy(mob.getUUID());
        if (hasLegacyDeferredPolicy) {
            AnimalPowerAccess machine = (AnimalPowerAccess) level.getBlockEntity(crankPos);
            dropLead = machine.animalPowerEngine().deferredDetachDropLead(mob.getUUID());
        }

        releasePersistedAttachment(mob, level, crankPos, dropLead);

        if (hasLegacyDeferredPolicy) {
            AnimalPowerAccess machine = (AnimalPowerAccess) level.getBlockEntity(crankPos);
            machine.animalPowerEngine().consumeDeferredDetachPolicy(mob.getUUID());
        }
        removeDurableIfOwnedBy(level, mob.getUUID(), crankPos, crankUuid);
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
            // Activity-only recovery may coexist with a durable record from a
            // different crank. Consume it only when the remaining activity
            // marker proves ownership.
            removeDurableIfOwnedBy(
                    level,
                    mob.getUUID(),
                    WorkerActivityControl.markerCrankPos(mob),
                    WorkerActivityControl.markerCrankUuid(mob)
            );
            return;
        }

        BlockPos crankPos = markerCrankPos(mob);
        UUID crankUuid = markerCrankUuid(mob);
        if (crankPos != null) {
            releasePersistedAttachment(mob, level, crankPos, true);
        }
        if (crankPos != null && crankUuid != null) {
            releaseActivityIfOwnedBy(mob, crankPos, crankUuid);
        }
        removeDurableIfOwnedBy(level, mob.getUUID(), crankPos, crankUuid);
        mob.getPersistentData().remove(MARKER_KEY);
        CHPDiagnostics.event("attachment_recovery_timeout", level, crankPos, crankUuid, mob,
                "dropLead=true old_chunk_force_loaded=false");
    }

    private static void releaseActivityIfOwnedBy(Mob mob, BlockPos crankPos, UUID crankUuid) {
        if (WorkerActivityControl.isOwnedBy(mob, crankPos, crankUuid)) {
            WorkerActivityControl.releaseFromMarker(mob);
        }
    }

    private static void removeDurableIfOwnedBy(
            ServerLevel level,
            UUID workerUuid,
            @Nullable BlockPos crankPos,
            @Nullable UUID crankUuid
    ) {
        if (crankPos == null || crankUuid == null) {
            return;
        }
        DeferredDetachStore.Entry durable = CHPApi.deferredDetaches().get(level, workerUuid);
        if (durable != null && durable.matches(crankPos, crankUuid)) {
            CHPApi.deferredDetaches().remove(level, workerUuid);
        }
    }

    public static AttachmentMode markerMode(Mob mob) {
        if (!hasMarker(mob)) return AttachmentMode.VANILLA_LEASH;
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        if (!marker.contains(MODE_KEY)) return AttachmentMode.VANILLA_LEASH;
        try {
            return AttachmentMode.parse(marker.getString(MODE_KEY));
        } catch (IllegalArgumentException ignored) {
            return AttachmentMode.VANILLA_LEASH;
        }
    }

    @Nullable
    public static String markerProfileId(Mob mob) {
        if (!hasMarker(mob)) return null;
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        return marker.contains(PROFILE_KEY) ? marker.getString(PROFILE_KEY) : null;
    }

    public static boolean isBackendAttached(Mob mob, BlockPos crankPos, UUID crankUuid, AttachmentMode mode) {
        if (!isOwnedBy(mob, crankPos, crankUuid)) return false;
        if (mode == AttachmentMode.VANILLA_LEASH) {
            Entity holder = mob.getLeashHolder();
            return holder instanceof LeashFenceKnotEntity knot && knot.blockPosition().equals(crankPos);
        }
        return mob.isAlive();
    }

    /**
     * Releases only the backend recorded in CHP's attachment marker. The profile
     * item lifecycle is backend-independent: a consumed harness/yoke item must be
     * returned according to drop_on_detach even when the physical backend is the
     * vanilla leash/fence-knot implementation.
     */
    public static void releasePersistedAttachment(Mob mob, ServerLevel level, BlockPos crankPos, boolean dropRequested) {
        AttachmentMode mode = markerMode(mob);
        if (mode == AttachmentMode.VANILLA_LEASH) {
            releasePersistedCrankLeash(mob, level, crankPos, dropRequested);
        }
        dropConsumedAttachmentIfRequested(mob, dropRequested);
    }

    private static void dropConsumedAttachmentIfRequested(Mob mob, boolean dropRequested) {
        if (!hasMarker(mob)) return;
        CompoundTag marker = mob.getPersistentData().getCompound(MARKER_KEY);
        if (dropRequested && marker.getBoolean(CONSUMED_KEY) && marker.getBoolean(DROP_ON_DETACH_KEY)
                && marker.contains(ATTACHMENT_ITEM_KEY)) {
            ResourceLocation itemId = parseResourceId(marker.getString(ATTACHMENT_ITEM_KEY));
            if (itemId != null) {
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> mob.spawnAtLocation(new ItemStack(item)));
            }
        }
    }

    @Nullable
    private static ResourceLocation parseResourceId(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) return null;
        try {
            return CHPApi.id(value.substring(0, colon), value.substring(colon + 1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
    private static void releasePersistedCrankLeash(
            Mob mob,
            ServerLevel level,
            BlockPos crankPos,
            boolean dropLead
    ) {
        Entity holder = mob.getLeashHolder();
        if (holder instanceof LeashFenceKnotEntity knot && knot.blockPosition().equals(crankPos)) {
            mob.dropLeash(true, dropLead);

            // Vanilla may have recreated this exact knot from the worker's
            // persisted BlockPos leash immediately before recovery. The knot
            // object is already loaded, so checking its loaded leash users and
            // discarding it when unused does not inspect or force-load the old
            // crank block/chunk. This closes the final ghost-knot path without
            // stealing a live knot that another loaded mob still uses.
            if (knot.isAlive() && !hasLoadedMobAttachedToKnot(level, knot)) {
                knot.discard();
            }
            return;
        }

        if (holder == null && hasPersistedCrankKnotLeash(mob, crankPos)) {
            // Vanilla can keep an unresolved entity-UUID leash pending for many
            // ticks. That leash is foreign to the crank and must survive CHP
            // recovery. Only synthesize a temporary holder when serialization
            // proves the pending leash itself is the old crank's exact knot
            // BlockPos. 1.20.1 stores that as Leash{X,Y,Z}; 1.21.1 stores it as
            // the lowercase leash int[3] BlockPos representation.
            mob.setLeashedTo(mob, false);
            mob.dropLeash(true, dropLead);
        }
        // A non-matching live holder or delayed foreign UUID holder belongs to
        // someone/something else; preserve it.
    }

    private static boolean hasPersistedCrankKnotLeash(Mob mob, BlockPos crankPos) {
        CompoundTag snapshot = new CompoundTag();
        mob.saveWithoutId(snapshot);

        // Minecraft 1.20.1: fence-knot leash is a compound with X/Y/Z, while
        // entity leashes use a UUID field. Never consume the UUID form here.
        if (snapshot.contains("Leash")) {
            CompoundTag leash = snapshot.getCompound("Leash");
            if (leash.contains("X") && leash.contains("Y") && leash.contains("Z")) {
                return crankPos.equals(new BlockPos(
                        leash.getInt("X"),
                        leash.getInt("Y"),
                        leash.getInt("Z")
                ));
            }
        }

        // Minecraft 1.21.1: BlockPos leash data is int[3]. Entity UUID leashes
        // are compounds, so the type check prevents treating them as crank data.
        if (snapshot.contains("leash", 11)) {
            int[] leashPos = snapshot.getIntArray("leash");
            return leashPos.length == 3
                    && crankPos.getX() == leashPos[0]
                    && crankPos.getY() == leashPos[1]
                    && crankPos.getZ() == leashPos[2];
        }

        return false;
    }

    private static boolean hasLoadedMobAttachedToKnot(ServerLevel level, LeashFenceKnotEntity knot) {
        // This query only visits currently loaded entities. The generous radius
        // is intentionally larger than a normal vanilla leash can remain intact,
        // while still avoiding any block/chunk lookup at the old crank position.
        return !level.getEntitiesOfClass(
                Mob.class,
                knot.getBoundingBox().inflate(32.0D),
                candidate -> candidate.isAlive()
                        && candidate.isLeashed()
                        && candidate.getLeashHolder() == knot
        ).isEmpty();
    }
}
