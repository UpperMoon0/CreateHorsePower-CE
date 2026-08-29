package net.steampn.createhorsepower.content.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.registry.CHPDataMaps;

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
        if (coverage < Config.MINIMUM_PATH_COVERAGE.get() || validCount == 0) {
            return new Result(false, 0.0f, 0.0f, validCount, invalidCount, total, 0);
        }

        PathEvaluationMode mode = Config.PATH_EVALUATION_MODE.get();
        float finalSpeed;
        float finalStress;

        switch (mode) {
            case WORST_BLOCK -> {
                finalSpeed = (minSpeedMultiplier == Float.MAX_VALUE) ? 1.0f : minSpeedMultiplier;
                finalStress = (minStressMultiplier == Float.MAX_VALUE) ? 1.0f : minStressMultiplier;
            }
            case LEGACY -> {
                if (hasPoor) {
                    finalSpeed = Config.POOR_MULTIPLIER.get().floatValue();
                    finalStress = 0.90f;
                } else if (allGreat && invalidCount == 0) {
                    finalSpeed = Config.GREAT_MULTIPLIER.get().floatValue();
                    finalStress = 1.10f;
                } else {
                    finalSpeed = Config.NORMAL_MULTIPLIER.get().floatValue();
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
        Optional<PathStats> kjsStats = net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry.getPathBlock(block);
        if (kjsStats.isPresent()) {
            return kjsStats;
        }

        Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
        PathStats dataMapStats = holder.getData(CHPDataMaps.PATH_STATS);
        if (dataMapStats != null) {
            return Optional.of(dataMapStats);
        }

        String blockKey = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (Config.GREAT_PATH.get().contains(blockKey)) {
            return Optional.of(new PathStats(Config.GREAT_MULTIPLIER.get().floatValue(), 1.10f));
        }
        if (Config.NORMAL_PATH.get().contains(blockKey)) {
            return Optional.of(new PathStats(Config.NORMAL_MULTIPLIER.get().floatValue(), 1.00f));
        }
        if (Config.POOR_PATH.get().contains(blockKey)) {
            return Optional.of(new PathStats(Config.POOR_MULTIPLIER.get().floatValue(), 0.90f));
        }

        return Optional.empty();
    }
}
