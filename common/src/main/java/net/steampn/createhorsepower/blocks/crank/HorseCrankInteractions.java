package net.steampn.createhorsepower.blocks.crank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.utils.CHPUtils;

import java.util.List;

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
                level.setBlock(pos, state.setValue(CrankProperties.HAS_WORKER, false)
                        .setValue(CrankProperties.SMALL_WORKER_STATE, false)
                        .setValue(CrankProperties.MEDIUM_WORKER_STATE, false)
                        .setValue(CrankProperties.LARGE_WORKER_STATE, false), 3);
                CHPUtils.cleanUpLeash(level, pos, true);
            }
        }
        return Outcome.SUCCESS;
    }

    /** Runs the full attach flow for a leashed worker near the player. */
    public static Outcome attachAt(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return Outcome.SUCCESS;
        }

        if (hasWorker(level, pos, state)) {
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.alreadyHasWorker"), true);
            return Outcome.SUCCESS;
        }

        List<Mob> mobsNearPlayer = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(7.0D), mob -> mob.isLeashed() && mob.getLeashHolder() == player);

        if (mobsNearPlayer.isEmpty()) {
            return Outcome.PASS;
        }

        if (mobsNearPlayer.size() > 1) {
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.maximumMobs"), true);
            return Outcome.SUCCESS;
        }

        Mob mob = mobsNearPlayer.get(0);
        WorkerResolver.ResolvedWorker profile = WorkerResolver.resolve(mob);
        if (!profile.isValid()) {
            player.displayClientMessage(Component.translatable("tooltip.createhorsepower.horse_crank.notValidWorker"), true);
            return Outcome.SUCCESS;
        }

        if (!CHPApi.scripts().fireBeforeAttach(player, mob, pos, level, profile)) {
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
