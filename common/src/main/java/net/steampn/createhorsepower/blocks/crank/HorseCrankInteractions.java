package net.steampn.createhorsepower.blocks.crank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.utils.CHPUtils;
import net.steampn.createhorsepower.utils.CHPDiagnostics;

import java.util.List;
import java.util.UUID;

/**
 * Shared attach/detach/wrench semantics. Minecraft decides how an interaction
 * is invoked (1.20.1 single {@code use} vs 1.21.1 {@code useItemOn}/
 * {@code useWithoutItem}); this class decides what the interaction means.
 * Platform blocks map the returned {@link Outcome} onto their interaction types.
 */
public final class HorseCrankInteractions {

    public enum Outcome {
        PASS,
        SUCCESS,
        FAIL,
        SKIP_DEFAULT
    }

    private HorseCrankInteractions() {}

    public static boolean hasWorker(Level level, BlockPos pos, BlockState state) {
        return state.getValue(CrankProperties.HAS_WORKER) || CHPUtils.getKnot(level, pos).isPresent();
    }

    /** Detaches the current worker (server side) and cleans up leash/knot state. */
    public static Outcome detachAt(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be) {
                be.detachWorker(true);
            } else {
                CHPUtils.cleanUpLeash(level, pos, true);
            }

            // Treat the block state as a cache of lifecycle state, not an
            // independent source of ownership. In particular, 1.21.1 can
            // otherwise leave a ghost HAS_WORKER bit after the real leash and
            // engine assignment are gone, causing every lead-click to reject
            // with "already has a worker".
            clearWorkerState(level, pos);
        }
        return Outcome.SUCCESS;
    }

    /**
     * Repairs a stale loaded assignment before an attach interaction. A worker
     * that is genuinely unloaded remains reserved; a loaded assigned worker
     * that is no longer attached to this crank is stale and can be replaced
     * immediately on the same click.
     */
    public static boolean repairStaleAssignmentBeforeAttach(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !hasWorker(level, pos, state)) {
            return false;
        }
        if (hasAuthoritativeWorkerReservation(level, pos)) {
            return false;
        }

        UUID crankUuid = null;
        UUID assignedWorker = null;
        if (level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be) {
            crankUuid = be.engine().crankInstanceUuid();
            assignedWorker = be.engine().getWorkerUuid();
        }
        CHPDiagnostics.event("stale_assignment_repaired", level, pos, crankUuid, null,
                "assigned_worker=" + assignedWorker);
        detachAt(level, pos, state);
        return true;
    }

    private static boolean hasAuthoritativeWorkerReservation(Level level, BlockPos pos) {
        // A real loaded leash wins regardless of BE cache state.
        if (CHPUtils.hasAttachedWorker(level, pos)) {
            return true;
        }

        if (!(level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be)) {
            return false;
        }

        UUID assigned = be.engine().getWorkerUuid();
        if (assigned == null) {
            return false;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        Entity loaded = serverLevel.getEntity(assigned);
        if (loaded == null) {
            // Do not steal a crank from a worker whose entity chunk is merely
            // unloaded. The engine UUID is the durable reservation in that case.
            return true;
        }

        return loaded instanceof Mob mob && CHPUtils.isLeashedToKnotAt(mob, pos);
    }

    private static void clearWorkerState(Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!current.hasProperty(CrankProperties.HAS_WORKER)) {
            return;
        }

        BlockState cleared = current
                .setValue(CrankProperties.HAS_WORKER, false)
                .setValue(CrankProperties.SMALL_WORKER_STATE, false)
                .setValue(CrankProperties.MEDIUM_WORKER_STATE, false)
                .setValue(CrankProperties.LARGE_WORKER_STATE, false);
        if (!cleared.equals(current)) {
            level.setBlock(pos, cleared, 3);
        }
    }

    /** Runs the full attach flow for a leashed worker near the player. */
    public static Outcome attachAt(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide()) {
            return Outcome.SUCCESS;
        }

        // Repair stale cache state before occupancy rejection. This is stricter
        // than merely checking for a knot: an unloaded assigned worker remains
        // reserved, while a loaded worker that has been detached/re-leashed is
        // immediately recognized as stale.
        if (hasWorker(level, pos, state)) {
            repairStaleAssignmentBeforeAttach(level, pos, state);
            state = level.getBlockState(pos);
        }

        if (hasWorker(level, pos, state)) {
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.alreadyHasWorker"), true);
            return Outcome.SUCCESS;
        }

        List<Mob> mobsNearPlayer = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(7.0D), mob -> mob.isLeashed() && mob.getLeashHolder() == player);

        if (mobsNearPlayer.isEmpty()) {
            CHPDiagnostics.event("attach_rejected", level, pos,
                    level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be ? be.engine().crankInstanceUuid() : null,
                    null, "reason=no_player_leashed_worker");
            return Outcome.PASS;
        }

        if (mobsNearPlayer.size() > 1) {
            CHPDiagnostics.event("attach_rejected", level, pos,
                    level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be ? be.engine().crankInstanceUuid() : null,
                    null, "reason=multiple_workers count=" + mobsNearPlayer.size());
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.maximumMobs"), true);
            return Outcome.SUCCESS;
        }

        Mob mob = mobsNearPlayer.get(0);
        WorkerResolver.ResolvedWorker profile = WorkerResolver.resolve(mob);
        if (!profile.isValid()) {
            CHPDiagnostics.event("attach_rejected", level, pos,
                    level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be ? be.engine().crankInstanceUuid() : null,
                    mob, "reason=invalid_worker");
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.notValidWorker"), true);
            return Outcome.SUCCESS;
        }

        if (!CHPApi.scripts().fireBeforeAttach(player, mob, pos, level, profile)) {
            CHPDiagnostics.event("attach_rejected", level, pos,
                    level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be ? be.engine().crankInstanceUuid() : null,
                    mob, "reason=script_veto");
            return Outcome.FAIL;
        }

        LeadItem.bindPlayerMobs(player, level, pos);
        if (level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be) {
            be.attachWorker(mob, profile);
        } else {
            level.setBlock(pos, state.setValue(CrankProperties.HAS_WORKER, true), 3);
        }
        player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.attached"), true);

        return Outcome.SUCCESS;
    }

    /** Cycles the redstone mode with the wrench. */
    public static Outcome wrench(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be) {
            RedstoneMode newMode = be.cycleRedstoneMode();
            if (player != null) {
                player.displayClientMessage(Component.translatable("tooltip.createhorsepower.redstone_mode.changed", newMode.getDisplayName()), true);
            }
            IWrenchable.playRotateSound(level, pos);
            return Outcome.SUCCESS;
        }

        return Outcome.PASS;
    }

    /** Redstone comparator output derived from path efficiency. */
    public static int comparatorOutput(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AbstractHorseCrankBlockEntity be) {
            if (be.isStoppedByRedstone() || !be.hasValidWorkingBlocks() || be.getGeneratedSpeed() == 0) {
                return 0;
            }
            float efficiency = be.getEfficiencyPercent();
            return Math.min(15, Math.max(1, Math.round((efficiency / 100.0f) * 10.0f)));
        }
        return 0;
    }
}
