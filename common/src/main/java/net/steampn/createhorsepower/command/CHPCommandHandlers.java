package net.steampn.createhorsepower.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.steampn.createhorsepower.blocks.crank.HorseCrankAccess;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.content.stats.WorkerStats;

import java.util.Optional;

/**
 * Shared command diagnostics. Platform command classes resolve their
 * version-specific arguments (entity/block ids) and hand the resolved
 * objects to these handlers, so both versions print identical output.
 */
public final class CHPCommandHandlers {

    private CHPCommandHandlers() {}

    public static int inspectTargetCrank(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be executed by a player looking at a Horse Crank."));
            return 0;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f).scale(10.0);
        Vec3 endPos = eyePos.add(lookVec);
        BlockHitResult hit = player.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("No block in line of sight (range 10 blocks)."));
            return 0;
        }

        BlockPos pos = hit.getBlockPos();
        if (!(player.level().getBlockEntity(pos) instanceof HorseCrankAccess crank)) {
            source.sendFailure(Component.literal("Target block at " + pos.toShortString() + " is not a Horse Crank."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Horse Crank Diagnostics (" + pos.toShortString() + ") ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Redstone Mode: ").withStyle(ChatFormatting.YELLOW)
                .append(crank.getRedstoneMode().getDisplayName())
                .append(" (Stopped: " + crank.isStoppedByRedstone() + ")"), false);

        if (!crank.getCachedWorkerName().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Worker: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(crank.getCachedWorkerName()).withStyle(ChatFormatting.WHITE)), false);
            source.sendSuccess(() -> Component.literal(String.format("  Effective Base RPM: %.2f", crank.getEffectiveBaseRpm())), false);
            source.sendSuccess(() -> Component.literal(String.format("  Effective Base Stress: %.2f SU", crank.getEffectiveBaseStress())), false);
            if (crank.getSpeedBonusPercent() != 0) {
                source.sendSuccess(() -> Component.literal(String.format("  Speed Bonus: %+.1f%%", crank.getSpeedBonusPercent())).withStyle(ChatFormatting.AQUA), false);
            }
            if (crank.getHealthBonusPercent() != 0) {
                source.sendSuccess(() -> Component.literal(String.format("  Health Bonus: %+.1f%%", crank.getHealthBonusPercent())).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("Worker: None attached").withStyle(ChatFormatting.GRAY), false);
        }

        source.sendSuccess(() -> Component.literal(String.format("Path Efficiency: %d%% (Valid: %s, Invalid Tiles: %d)",
                crank.getEfficiencyPercent(), crank.hasValidWorkingBlocks(), crank.getInvalidBlockCount())).withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal(String.format("Generated Speed: %.2f RPM", crank.getGeneratedSpeed())).withStyle(ChatFormatting.BLUE), false);

        return 1;
    }

    public static int inspectWorker(CommandSourceStack source, EntityType<?> type, ResourceLocation id) {
        Optional<WorkerStats> statsOpt = WorkerResolver.getBaseStats(type);
        if (statsOpt.isEmpty()) {
            source.sendFailure(Component.literal("No WorkerStats profile found for " + id));
            return 0;
        }

        WorkerStats stats = statsOpt.get();
        source.sendSuccess(() -> Component.literal("=== Worker Profile: " + id + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format("Base RPM: %.2f", stats.baseRpm())), false);
        source.sendSuccess(() -> Component.literal(String.format("Base Stress: %.2f SU", stats.stressCapacity())), false);
        source.sendSuccess(() -> Component.literal(String.format("Movement Radius: %.2f blocks", stats.movementRadius())), false);
        source.sendSuccess(() -> Component.literal(String.format("Speed Scaling: %.2f", stats.speedScaling())), false);
        source.sendSuccess(() -> Component.literal(String.format("Health Scaling: %.2f", stats.healthScaling())), false);
        source.sendSuccess(() -> Component.literal("Requires Tamed: " + stats.requiresTamed()), false);
        source.sendSuccess(() -> Component.literal("Allow Baby: " + stats.allowBaby()), false);

        return 1;
    }

    public static int inspectPath(CommandSourceStack source, Block block, ResourceLocation id) {
        Optional<PathStats> statsOpt = PathEvaluator.getPathStats(block);
        if (statsOpt.isEmpty()) {
            source.sendFailure(Component.literal("Block " + id + " is not a valid Horse Crank path material."));
            return 0;
        }

        PathStats stats = statsOpt.get();
        source.sendSuccess(() -> Component.literal("=== Path Material: " + id + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format("Speed Multiplier: %.2fx (%d%% efficiency)", stats.speedMultiplier(), Math.round(stats.speedMultiplier() * 100))), false);
        source.sendSuccess(() -> Component.literal(String.format("Stress Multiplier: %.2fx", stats.stressMultiplier())), false);

        return 1;
    }
}
