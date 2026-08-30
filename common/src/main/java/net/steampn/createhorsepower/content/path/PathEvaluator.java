package net.steampn.createhorsepower.content.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.platform.CHPApi;

import java.util.Optional;

public class PathEvaluator {

    public record Result(
            boolean isValid,
            float speedMultiplier,
            float stressMultiplier,
            int validBlocks,
            int invalidBlocks,
            int totalBlocks,
            int efficiencyPercent
    ) {
        public static final Result INVALID = new Result(false, 0.0f, 0.0f, 0, 0, 0, 0);
    }

    public static Result evaluate(Level level, BlockPos centerPos, BlockPos[] offsets) {
        if (level == null || centerPos == null || offsets == null || offsets.length == 0) {
            return Result.INVALID;
        }

        int total = offsets.length;
        int validCount = 0;
        int invalidCount = 0;

        float totalSpeedMultiplier = 0.0f;
        float totalStressMultiplier = 0.0f;
        float minSpeedMultiplier = Float.MAX_VALUE;
        float minStressMultiplier = Float.MAX_VALUE;

        boolean hasPoor = false;
        boolean allGreat = true;

        for (BlockPos offset : offsets) {
            BlockPos targetPos = centerPos.offset(offset);
            if (!level.hasChunkAt(targetPos)) {
                invalidCount++;
                allGreat = false;
                continue;
            }
            BlockState state = level.getBlockState(targetPos);
            Optional<PathStats> statsOpt = getPathStats(state.getBlock());

            if (statsOpt.isPresent()) {
                PathStats stats = statsOpt.get();
                validCount++;
                totalSpeedMultiplier += stats.speedMultiplier();
                totalStressMultiplier += stats.stressMultiplier();

                if (stats.speedMultiplier() < minSpeedMultiplier) {
                    minSpeedMultiplier = stats.speedMultiplier();
                }
                if (stats.stressMultiplier() < minStressMultiplier) {
                    minStressMultiplier = stats.stressMultiplier();
                }

                if (stats.speedMultiplier() < 1.0f) {
                    hasPoor = true;
                }
                if (stats.speedMultiplier() < 1.2f) {
                    allGreat = false;
                }
            } else {
                invalidCount++;
                allGreat = false;
            }
        }

        double coverage = (double) validCount / (double) total;
        if (coverage < CHPApi.config().minimumPathCoverage() || validCount == 0) {
            return new Result(false, 0.0f, 0.0f, validCount, invalidCount, total, 0);
        }

        var mode = CHPApi.config().pathEvaluationMode();
        float finalSpeed;
        float finalStress;

        switch (mode) {
            case WORST_BLOCK -> {
                finalSpeed = (minSpeedMultiplier == Float.MAX_VALUE) ? 1.0f : minSpeedMultiplier;
                finalStress = (minStressMultiplier == Float.MAX_VALUE) ? 1.0f : minStressMultiplier;
            }
            case LEGACY -> {
                if (hasPoor) {
                    finalSpeed = (float) CHPApi.config().poorMultiplier();
                    finalStress = 0.90f;
                } else if (allGreat && invalidCount == 0) {
                    finalSpeed = (float) CHPApi.config().greatMultiplier();
                    finalStress = 1.10f;
                } else {
                    finalSpeed = (float) CHPApi.config().normalMultiplier();
                    finalStress = 1.00f;
                }
            }
            case WEIGHTED_AVERAGE -> {
                finalSpeed = totalSpeedMultiplier / validCount;
                finalStress = totalStressMultiplier / validCount;
            }
            default -> {
                finalSpeed = totalSpeedMultiplier / validCount;
                finalStress = totalStressMultiplier / validCount;
            }
        }

        int efficiency = Math.round(finalSpeed * 100.0f);
        return new Result(true, finalSpeed, finalStress, validCount, invalidCount, total, efficiency);
    }

    public static Optional<PathStats> getPathStats(Block block) {
        Optional<PathStats> kjsStats = KubeJSProfileRegistry.getPathBlock(block);
        if (kjsStats.isPresent()) {
            return kjsStats;
        }

        Optional<PathStats> platformStats = CHPApi.config().lookupPathStats(block);
        if (platformStats.isPresent()) {
            return platformStats;
        }

        String blockKey = blockBuiltInKey(block);
        if (CHPApi.config().greatPath().contains(blockKey)) {
            return Optional.of(new PathStats((float) CHPApi.config().greatMultiplier(), 1.10f));
        }
        if (CHPApi.config().normalPath().contains(blockKey)) {
            return Optional.of(new PathStats((float) CHPApi.config().normalMultiplier(), 1.00f));
        }
        if (CHPApi.config().poorPath().contains(blockKey)) {
            return Optional.of(new PathStats((float) CHPApi.config().poorMultiplier(), 0.90f));
        }

        return Optional.empty();
    }

    private static String blockBuiltInKey(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
