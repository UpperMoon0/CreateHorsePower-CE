package net.steampn.createhorsepower.content.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.BuiltinProfiles;
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

        PathStats[] resolvedStats = new PathStats[offsets.length];
        boolean safeOrbit = true;
        for (int i = 0; i < offsets.length; i++) {
            BlockPos targetPos = centerPos.offset(offsets[i]);
            if (!isWalkableTile(level, targetPos)) {
                safeOrbit = false;
            }
            if (!level.hasChunkAt(targetPos)) {
                continue;
            }
            resolvedStats[i] = getPathStats(level.getBlockState(targetPos).getBlock()).orElse(null);
        }

        // The annulus supplies coarse validity; movement independently checks
        // the actual continuous orbit and its immediate support footprint.
        PathEvaluator.Result computed = evaluateResolvedPathStats(
                resolvedStats,
                CHPApi.config().pathEvaluationMode(),
                CHPApi.config().minimumPathCoverage(),
                (float) CHPApi.config().poorMultiplier(),
                (float) CHPApi.config().normalMultiplier(),
                (float) CHPApi.config().greatMultiplier(),
                CHPApi.config().enablePathStressScaling()
        );
        return safeOrbit ? computed : new Result(false, 0.0f, 0.0f,
                computed.validBlocks(), computed.invalidBlocks(), computed.totalBlocks(), 0);
    }

    /** Walkability is independent from a block having a configured efficiency profile. */
    public static boolean isWalkableTile(Level level, BlockPos floorPos) {
        if (level == null || !level.hasChunkAt(floorPos)
                || !level.hasChunkAt(floorPos.above(2))) return false;
        BlockState floor = level.getBlockState(floorPos);
        VoxelShape collision = floor.getCollisionShape(level, floorPos);
        // Only a broad, continuous top surface supports a worker. The old
        // maxY check accepted a thin iron bar because its collision reached Y=1.
        // Dirt paths and farmland have a lower but still broad, level top.
        if (floor.getFluidState().isEmpty() && !isHazardousFloor(floor) && !collision.isEmpty()) {
            double top = collision.max(Direction.Axis.Y);
            if (top >= 0.8125D && top <= 1.0D) {
                VoxelShape hoofArea = Shapes.box(0.125D, top - 0.015625D, 0.125D,
                        0.875D, top, 0.875D);
                if (!Shapes.joinIsNotEmpty(hoofArea, collision, BooleanOp.ONLY_FIRST)) {
                    for (int dy = 1; dy <= 2; dy++) {
                        BlockPos above = floorPos.above(dy);
                        BlockState overhead = level.getBlockState(above);
                        if (!overhead.getFluidState().isEmpty()
                                || isHazardousFloor(overhead)
                                || !overhead.getCollisionShape(level, above).isEmpty()) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Material hazards cannot become safe merely by being added to a path profile. */
    private static boolean isHazardousFloor(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE);
    }

    /**
     * Require a walkable block under the worker's center and broad support
     * beneath its actual footprint. An orbit made from the standard 28-tile
     * annulus supports a horse even at corners where its hitbox slightly
     * overhangs the outside edge. Requiring every bounding-box corner to be
     * paved incorrectly stops normal horses for more than half the orbit.
     *
     * We measure the overlap area with safe *full-top* path tiles, not the
     * number of sampled points. A worker supported by a thin central pillar,
     * one isolated block, or a large gap cannot pass. The threshold permits
     * modest edge overhang just as vanilla entity physics does, while still
     * checking hazardous blocks and unobstructed headroom for the central
     * support and counting those tiles as missing from the usable area.
     */
    public static boolean supportsWorkerFootprint(Level level, double x, double feetY,
                                                   double z, float width) {
        if (level == null || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(feetY) || !Float.isFinite(width) || width <= 0.0f) {
            return false;
        }
        int floorY = BlockPos.containing(x, feetY - 0.125D, z).getY();
        if (!isWalkableTile(level, BlockPos.containing(x, feetY - 0.125D, z))) {
            return false;
        }

        // Slightly inset to represent hoof-bearing contact, rather than
        // demanding support for the entire broad collision/interaction box.
        double half = Math.max(0.125D, width * 0.5D - 0.0625D);
        double minX = x - half;
        double maxX = x + half;
        double minZ = z - half;
        double maxZ = z + half;
        double supportedArea = 0.0D;
        for (int bx = (int) Math.floor(minX); bx < maxX; bx++) {
            double overlapX = Math.min(maxX, bx + 1.0D) - Math.max(minX, bx);
            if (overlapX <= 0.0D) continue;
            for (int bz = (int) Math.floor(minZ); bz < maxZ; bz++) {
                double overlapZ = Math.min(maxZ, bz + 1.0D) - Math.max(minZ, bz);
                if (overlapZ <= 0.0D) continue;
                if (isWalkableTile(level, new BlockPos(bx, floorY, bz))) {
                    supportedArea += overlapX * overlapZ;
                }
            }
        }
        double area = (maxX - minX) * (maxZ - minZ);
        return supportedArea >= area * 0.80D - 1.0e-8D;
    }

    /**
     * Aggregates already-resolved path profiles. Keeping the arithmetic here
     * makes evaluation-mode and policy behavior testable without mutating a
     * live loader config file; the world-facing evaluator above remains the
     * sole owner of block/profile resolution and runtime config lookup.
     */
    static Result evaluateResolvedPathStats(
            PathStats[] resolvedStats,
            PathEvaluationMode mode,
            double minimumCoverage,
            float poorMultiplier,
            float normalMultiplier,
            float greatMultiplier,
            boolean enableStressScaling
    ) {
        if (resolvedStats == null || resolvedStats.length == 0) {
            return Result.INVALID;
        }

        int total = resolvedStats.length;
        int validCount = 0;
        int invalidCount = 0;

        float totalSpeedMultiplier = 0.0f;
        float totalStressMultiplier = 0.0f;
        float minSpeedMultiplier = Float.MAX_VALUE;
        float minStressMultiplier = Float.MAX_VALUE;

        boolean hasPoor = false;
        boolean allGreat = true;

        for (PathStats stats : resolvedStats) {
            if (stats == null) {
                invalidCount++;
                allGreat = false;
                continue;
            }

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
        }

        double coverage = (double) validCount / (double) total;
        if (coverage < minimumCoverage || validCount == 0) {
            return new Result(false, 0.0f, 0.0f, validCount, invalidCount, total, 0);
        }

        float finalSpeed;
        float finalStress;

        switch (mode) {
            case WORST_BLOCK -> {
                // Unrecognized but physically safe tiles contribute zero;
                // the worst tile of an incomplete orbit is therefore zero.
                finalSpeed = invalidCount > 0 ? 0.0f : minSpeedMultiplier;
                finalStress = invalidCount > 0 ? 0.0f : minStressMultiplier;
            }
            case LEGACY -> {
                if (hasPoor) {
                    finalSpeed = poorMultiplier;
                    finalStress = 0.90f;
                } else if (allGreat && invalidCount == 0) {
                    finalSpeed = greatMultiplier;
                    finalStress = 1.10f;
                } else {
                    finalSpeed = normalMultiplier;
                    finalStress = 1.00f;
                }
                // Legacy keeps its material-tier classification, but gaps
                // scale its output by recognized coverage, not a free full RPM.
                finalSpeed *= (float) coverage;
                finalStress *= (float) coverage;
            }
            case WEIGHTED_AVERAGE -> {
                // Every track position contributes to output. Unrecognized or
                // missing blocks contribute zero instead of receiving a second
                // penalty or inheriting the average quality of recognized tiles.
                finalSpeed = totalSpeedMultiplier / total;
                finalStress = totalStressMultiplier / total;
            }
            default -> {
                finalSpeed = totalSpeedMultiplier / validCount;
                finalStress = totalStressMultiplier / validCount;
            }
        }

        finalStress = applyPathStressScalingPolicy(finalStress, enableStressScaling);

        int efficiency = Math.round(finalSpeed * 100.0f);
        return new Result(true, finalSpeed, finalStress, validCount, invalidCount, total, efficiency);
    }

    static float applyPathStressScalingPolicy(float pathStressMultiplier, boolean enabled) {
        return enabled ? pathStressMultiplier : 1.0f;
    }

    public static Optional<PathStats> getPathStats(Block block) {
        Optional<PathStats> kjsStats = KubeJSProfileRegistry.getPathBlock(block);
        if (kjsStats.isPresent()) {
            return kjsStats;
        }

        String blockKey = blockBuiltInKey(block);
        Optional<PathStats> platformStats = CHPApi.config().lookupPathStats(block);
        Optional<PathStats> legacyStats = legacyPathStats(blockKey);
        Optional<PathStats> builtinStats = BuiltinProfiles.path(block);
        return resolveFallbackPathStats(platformStats, legacyStats, builtinStats);
    }

    /**
     * Cross-loader fallback precedence after KubeJS: explicit platform-native
     * profile (NeoForge Data Map), then pack/server legacy path lists, then CE's
     * bundled defaults. This lets modpacks override bundled exact/family data
     * while keeping weighted mixed-path evaluation unchanged.
     */
    static Optional<PathStats> resolveFallbackPathStats(
            Optional<PathStats> platformStats,
            Optional<PathStats> legacyStats,
            Optional<PathStats> builtinStats
    ) {
        if (platformStats.isPresent()) {
            return platformStats;
        }
        if (legacyStats.isPresent()) {
            return legacyStats;
        }
        return builtinStats;
    }

    private static Optional<PathStats> legacyPathStats(String blockKey) {
        return legacyPathStats(
                blockKey,
                CHPApi.config().poorPath(), CHPApi.config().normalPath(), CHPApi.config().greatPath(),
                CHPApi.config().poorMultiplier(), CHPApi.config().normalMultiplier(), CHPApi.config().greatMultiplier());
    }

    static Optional<PathStats> legacyPathStats(
            String blockKey,
            java.util.List<? extends String> poorPath,
            java.util.List<? extends String> normalPath,
            java.util.List<? extends String> greatPath,
            double poorMultiplier,
            double normalMultiplier,
            double greatMultiplier
    ) {
        if (greatPath.contains(blockKey)) {
            return Optional.of(new PathStats((float) greatMultiplier, 1.10f));
        }
        if (normalPath.contains(blockKey)) {
            return Optional.of(new PathStats((float) normalMultiplier, 1.00f));
        }
        if (poorPath.contains(blockKey)) {
            return Optional.of(new PathStats((float) poorMultiplier, 0.90f));
        }
        return Optional.empty();
    }

    private static String blockBuiltInKey(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
