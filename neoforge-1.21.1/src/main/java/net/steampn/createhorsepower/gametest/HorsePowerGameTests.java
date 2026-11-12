package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.HorseCrankInteractions;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.blocks.crank.WorkerOrbitMovement;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.content.attachment.AttachmentMode;
import net.steampn.createhorsepower.content.attachment.AttachmentProfile;
import net.steampn.createhorsepower.content.attachment.AttachmentProfileRegistry;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Loader-level smoke test proving the crank registrations survive full server bootstrap. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class HorsePowerGameTests {
    private HorsePowerGameTests() {}


    /**
     * A single recognized tile can power an otherwise solid, walkable orbit
     * with appropriately low efficiency. An air hole must stop the crank and
     * must never become a horizontal teleport destination.
     */
    @GameTest(template = "empty", batch = "chp_air_gap_safe", timeoutTicks = 100)
    public static void safeMixedOrbitStopsAtAirGap(GameTestHelper helper) {
        BlockPos crankPos = new BlockPos(4, 2, 4);
        BlockPos[] offsets = HorseCrankEngine.generateOffsetsForRadius(2.5f);
        for (BlockPos offset : offsets) {
            BlockPos floor = crankPos.offset(offset);
            helper.setBlock(floor, Blocks.STONE);
            // NeoForge's "empty" template includes invisible boundary barriers.
            // A real walking orbit needs two clear blocks above every floor tile.
            helper.setBlock(floor.above(), Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
        }
        helper.setBlock(crankPos.offset(offsets[0]), Blocks.ICE);
        assertWalkableRing(helper, crankPos, offsets);
        helper.setBlock(crankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity blockEntity = requireCrank(helper, crankPos);
        HorseCrankEngine engine = blockEntity.engine();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));

        WorkerStats stats = new WorkerStats(
                16.0f, 32.0f, 2.5f,
                0.0f, WorkerStats.DEFAULT_SPEED_REF,
                0.0f, WorkerStats.DEFAULT_HEALTH_REF,
                false, false);
        WorkerResolver.ResolvedWorker profile = new WorkerResolver.ResolvedWorker(
                stats, 16.0f, 32.0f, 0.0f, 0.0f, true);
        engine.attachWorker(horse, profile);
        helper.assertTrue(engine.hasValidWorkingBlocks,
                "Unsupported solid floor tiles should remain walkable");
        helper.assertTrue(engine.getEfficiencyPercent() > 0
                        && engine.getEfficiencyPercent() < 15,
                "One recognized tile must provide only proportional output");

        double centerX = helper.absolutePos(crankPos).getX() + 0.5D;
        double centerZ = helper.absolutePos(crankPos).getZ() + 0.5D;
        double oldX = horse.getX();
        double oldZ = horse.getZ();
        // At angle zero the orbit destination is 2.5 blocks to the east.
        BlockPos hole = crankPos.offset(3, -1, 0);
        helper.setBlock(hole, Blocks.AIR);
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.evaluate(
                        helper.getLevel(), helper.absolutePos(crankPos), offsets).isValid(),
                "Air gap must invalidate the real world path even with minCoverage=0");
        WorkerOrbitMovement.Snapshot movement = WorkerOrbitMovement.moveToAngle(
                horse, centerX, centerZ, 2.5f, 0.0D, 0.0D);
        helper.assertFalse(movement.moved(), "Worker must not teleport over the air hole");
        helper.assertTrue(Math.abs(horse.getX() - oldX) < 1.0e-5D
                        && Math.abs(horse.getZ() - oldZ) < 1.0e-5D,
                "Worker must remain grounded at its original coordinates");

        // A full-track inspection is intentionally periodic (default 40 ticks).
        // The stepwise guard above rejects the hole immediately; verify that
        // output is withdrawn at the *next scheduled* full-orbit evaluation,
        // irrespective of the game clock when this GameTest was dispatched.
        helper.runAfterDelay(Math.max(1, CHPApi.config().checkIntervalTicks()) + 2, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(hole)).isAir(),
                    "The cleared gap must persist until the scheduled path check");
            engine.afterHostTick();
            helper.assertFalse(engine.hasValidWorkingBlocks,
                    "Crank must stop by the next scheduled full-orbit check");
            helper.assertFalse(engine.canPhysicallyWork(),
                    "Invalid physical orbit must prohibit kinetic generation");
            helper.succeed();
        });
    }

    /** A paved ring must accept workers starting next to the crank, not just on the ring. */
    @GameTest(template = "empty")
    public static void initialWorkerSnapSkipsUnpavedInteriorButValidatesLanding(GameTestHelper helper) {
        BlockPos crankPos = new BlockPos(4, 2, 4);
        clearOrbitHeadroom(helper, crankPos);
        BlockPos[] offsets = HorseCrankEngine.generateOffsetsForRadius(2.5f);
        for (BlockPos offset : offsets) {
            BlockPos floor = crankPos.offset(offset);
            helper.setBlock(floor, Blocks.STONE);
            // NeoForge's "empty" template includes invisible boundary barriers.
            // A real walking orbit needs two clear blocks above every floor tile.
            helper.setBlock(floor.above(), Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
        }
        helper.setBlock(crankPos.offset(offsets[0]), Blocks.ICE);
        assertWalkableRing(helper, crankPos, offsets);
        helper.setBlock(crankPos, BlockRegister.HORSE_CRANK.get());
        HorseCrankEngine engine = requireCrank(helper, crankPos).engine();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 4));
        // Reconciliation requires a physical leash, not only a marker.
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(
                helper.getLevel(), helper.absolutePos(crankPos));
        horse.setLeashedTo(knot, false);
        WorkerStats stats = new WorkerStats(
                16.0f, 32.0f, 2.5f,
                0.0f, WorkerStats.DEFAULT_SPEED_REF,
                0.0f, WorkerStats.DEFAULT_HEALTH_REF,
                false, false);
        WorkerResolver.ResolvedWorker profile = new WorkerResolver.ResolvedWorker(
                stats, 16.0f, 32.0f, 0.0f, 0.0f, true);
        engine.attachWorker(horse, profile);
        helper.assertTrue(engine.hasValidWorkingBlocks,
                "The ring is safe even though its interior is unpaved");
        double centerX = helper.absolutePos(crankPos).getX() + 0.5D;
        double centerZ = helper.absolutePos(crankPos).getZ() + 0.5D;
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.supportsWorkerFootprint(
                        helper.getLevel(), centerX + 1.0D, horse.getY(), centerZ, horse.getBbWidth()),
                "The interior really is unpaved; this test must exercise onboarding");
        engine.afterHostTick();
        helper.assertTrue(engine.isWorking(), "A valid ring must start generating when a worker joins from inside");
        helper.assertTrue(engine.generatedSpeed() > 0.0f, "Crank must generate from the safe ring");
        helper.assertTrue(Math.abs(Math.hypot(horse.getX() - centerX, horse.getZ() - centerZ) - 2.5D)
                        < 0.005D, "The first movement must safely place the worker on the ring");
        double oldX = horse.getX();
        double oldZ = horse.getZ();
        engine.afterHostTick();
        helper.assertTrue(Math.hypot(horse.getX() - oldX, horse.getZ() - oldZ) > 0.0001D,
                "The next tick must walk tangentially instead of repeatedly failing a radial snap");

        // Recovery from outside the ring has the same safe-landing policy.
        // Spawn beyond the 2.5-block orbit but within the cleared fixture;
        // the template boundary past z=8 is an invisible solid barrier.
        Horse outer = helper.spawn(EntityType.HORSE, new BlockPos(4, 2, 7));
        WorkerOrbitMovement.Snapshot snap = WorkerOrbitMovement.moveToAngle(
                outer, centerX, centerZ, 2.5f, Math.PI / 2.0D, Math.PI / 2.0D + 0.01D);
        helper.assertTrue(snap.moved(), "A worker outside the ring should land safely on the track");
        helper.assertTrue(Math.abs(Math.hypot(outer.getX() - centerX, outer.getZ() - centerZ) - 2.5D)
                        < 0.005D, "Outside worker must land on the configured radius");
        engine.detachWorker(false);
        horse.discard();
        outer.discard();
        knot.discard();
        helper.succeed();
    }

    /** Tall panes and damaging blocks must never count as a safe horse floor. */
    @GameTest(template = "empty")
    public static void orbitalFloorRequiresFullSupportAndNoHazards(GameTestHelper helper) {
        BlockPos local = new BlockPos(3, 1, 3);
        BlockPos absolute = helper.absolutePos(local);
        helper.setBlock(local.above(), Blocks.AIR);
        helper.setBlock(local.above(2), Blocks.AIR);
        helper.setBlock(local, Blocks.STONE);
        helper.assertTrue(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                helper.getLevel(), absolute), "Full stone floor must be walkable");
        helper.setBlock(local, Blocks.DIRT_PATH);
        helper.assertTrue(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                helper.getLevel(), absolute), "Dirt path's lower broad top should support workers");
        helper.setBlock(local, Blocks.IRON_BARS);
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                helper.getLevel(), absolute), "Iron bars are too narrow even when their max Y is 1");
        helper.setBlock(local, Blocks.GLASS_PANE);
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                helper.getLevel(), absolute), "A pane cannot support the worker footprint");
        helper.setBlock(local, Blocks.MAGMA_BLOCK);
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                helper.getLevel(), absolute), "Damaging magma is not safe despite a full collision cube");
        helper.setBlock(local, Blocks.STONE);
        helper.setBlock(local.above(), Blocks.FIRE);
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                helper.getLevel(), absolute), "Noncolliding fire above a full floor is still hazardous");
        helper.succeed();
    }


    /** Test the world-facing evaluator, not only its math-only implementation. */
    @GameTest(template = "empty")
    public static void worldMixedRingPenalizesLegacyAndWorstBlockModes(GameTestHelper helper) {
        var original = CHPApi.config();
        var scripts = CHPApi.scripts();
        var detaches = CHPApi.deferredDetaches();
        var stacks = CHPApi.itemStacks();
        BlockPos center = new BlockPos(4, 2, 4);
        BlockPos[] offsets = HorseCrankEngine.generateOffsetsForRadius(2.5f);
        for (BlockPos offset : offsets) {
            BlockPos floor = center.offset(offset);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(), Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
        }
        helper.setBlock(center.offset(offsets[0]), Blocks.ICE);
        try {
            for (net.steampn.createhorsepower.content.path.PathEvaluationMode mode :
                    new net.steampn.createhorsepower.content.path.PathEvaluationMode[] {
                        net.steampn.createhorsepower.content.path.PathEvaluationMode.LEGACY,
                        net.steampn.createhorsepower.content.path.PathEvaluationMode.WORST_BLOCK }) {
                Config policy = new Config() {
                    @Override public net.steampn.createhorsepower.content.path.PathEvaluationMode pathEvaluationMode() { return mode; }
                    @Override public double minimumPathCoverage() { return 0.0D; }
                };
                CHPApi.init(policy, scripts, net.minecraft.resources.ResourceLocation::fromNamespaceAndPath, detaches, stacks);
                var evaluated = net.steampn.createhorsepower.content.path.PathEvaluator.evaluate(
                        helper.getLevel(), helper.absolutePos(center), offsets);
                helper.assertTrue(evaluated.isValid(), "Walkable mixed ring invalid in " + mode);
                helper.assertTrue(evaluated.validBlocks() == 1, "Expected one recognized floor tile");
                if (mode == net.steampn.createhorsepower.content.path.PathEvaluationMode.WORST_BLOCK) {
                    helper.assertTrue(evaluated.speedMultiplier() == 0.0f,
                            "WORST_BLOCK must zero an unrecognized solid tile");
                    // Exercise the actual engine and user-visible goggles, not only evaluator math.
                    helper.setBlock(center, BlockRegister.HORSE_CRANK.get());
                    HorseCrankEngine zeroEngine = requireCrank(helper, center).engine();
                    Horse zeroWorker = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 4));
                    LeashFenceKnotEntity zeroKnot = LeashFenceKnotEntity.getOrCreateKnot(
                            helper.getLevel(), helper.absolutePos(center));
                    zeroWorker.setLeashedTo(zeroKnot, false);
                    WorkerStats zeroStats = new WorkerStats(
                            16.0f, 32.0f, 2.5f, 0.0f, WorkerStats.DEFAULT_SPEED_REF,
                            0.0f, WorkerStats.DEFAULT_HEALTH_REF, false, false);
                    zeroEngine.attachWorker(zeroWorker,
                            new WorkerResolver.ResolvedWorker(zeroStats, 16.0f, 32.0f, 0.0f, 0.0f, true));
                    helper.assertTrue(zeroEngine.hasZeroPathOutput(),
                            "WORST_BLOCK mixed walkable ring must expose its zero-output state");
                    java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
                    zeroEngine.buildGoggleTooltip(tooltip);
                    helper.assertTrue(tooltip.stream().anyMatch(line ->
                                    line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                                            && translated.getKey().equals(
                                                    "tooltip.createhorsepower.goggles.status.zero_output_path")),
                            "Goggles must explain why a physically valid mixed ring produces no RPM");
                    zeroEngine.detachWorker(false);
                    zeroWorker.discard();
                    zeroKnot.discard();
                    helper.setBlock(center, Blocks.AIR);
                } else {
                    helper.assertTrue(evaluated.speedMultiplier() > 0.0f && evaluated.speedMultiplier() < 0.2f,
                            "LEGACY must scale by recognized coverage");
                }
            }
            Config strict = new Config() {
                @Override public double minimumPathCoverage() { return 1.0D; }
            };
            CHPApi.init(strict, scripts, net.minecraft.resources.ResourceLocation::fromNamespaceAndPath, detaches, stacks);
            helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.evaluate(
                    helper.getLevel(), helper.absolutePos(center), offsets).isValid(),
                    "Persisted strict coverage must reject incomplete paths");
            helper.assertTrue(Math.abs(Config.MINIMUM_PATH_COVERAGE.getDefault()) < 1.0e-9D,
                    "Fresh server configurations must default to forgiving coverage");
        } finally {
            CHPApi.init(original, scripts, net.minecraft.resources.ResourceLocation::fromNamespaceAndPath, detaches, stacks);
        }
        helper.succeed();
    }

    /** Initial crank penetration must not authorize passage through a separate wall. */
    @GameTest(template = "empty")
    public static void crankOverlapDoesNotBypassAdjacentWall(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 2, 4);
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 8; z++) {
                BlockPos floor = new BlockPos(x, 1, z);
                helper.setBlock(floor, Blocks.STONE);
                helper.setBlock(floor.above(), Blocks.AIR);
                helper.setBlock(floor.above(2), Blocks.AIR);
            }
        }
        helper.setBlock(center, BlockRegister.HORSE_CRANK.get());
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(4, 2, 4));
        double cx = helper.absolutePos(center).getX() + 0.5D;
        double cz = helper.absolutePos(center).getZ() + 0.5D;
        horse.teleportTo(cx, horse.getY(), cz);
        helper.assertFalse(horse.level().noCollision(horse, horse.getBoundingBox()),
                "Fixture must start with the worker overlapping the crank");

        BlockPos wall = new BlockPos(5, 2, 4);
        helper.setBlock(wall, Blocks.STONE);
        helper.assertFalse(WorkerOrbitMovement.canMoveToAngle(horse, cx, cz, 2.5f, 0.0D),
                "Radial onboarding must reject the adjacent wall even while escaping the crank");
        var blocked = WorkerOrbitMovement.moveToAngle(horse, cx, cz, 2.5f, 0.0D, 0.0D);
        helper.assertFalse(blocked.moved(), "Blocked worker must not teleport through the wall");
        helper.assertTrue(Math.abs(horse.getX() - cx) < 1.0e-5D
                        && Math.abs(horse.getZ() - cz) < 1.0e-5D,
                "Failed preflight must leave the overlapping worker at its original position");

        helper.setBlock(wall, Blocks.AIR);
        helper.assertTrue(WorkerOrbitMovement.canMoveToAngle(horse, cx, cz, 2.5f, 0.0D),
                "Removing the wall must allow a worker to escape the crank onto the safe ring");
        helper.assertTrue(WorkerOrbitMovement.moveToAngle(horse, cx, cz, 2.5f, 0.0D, 0.0D).moved(),
                "Crank overlap alone must not permanently trap a worker");
        horse.discard();
        helper.succeed();
    }

    /** The middle of a teleport must collide even when both endpoints are clear. */
    @GameTest(template = "empty")
    public static void orbitMotionCannotTunnelThroughIntermediateWall(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 2, 4);
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 8; z++) {
                BlockPos floor = new BlockPos(x, 1, z);
                helper.setBlock(floor, Blocks.STONE);
                helper.setBlock(floor.above(), Blocks.AIR);
                helper.setBlock(floor.above(2), Blocks.AIR);
            }
        }
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(7, 2, 4));
        double centerX = helper.absolutePos(center).getX() + 0.5D;
        double centerZ = helper.absolutePos(center).getZ() + 0.5D;
        double startX = centerX + 2.5D;
        double startZ = centerZ;
        horse.teleportTo(startX, horse.getY(), startZ);
        helper.assertTrue(WorkerOrbitMovement.canMoveToAngle(
                horse, centerX, centerZ, 2.5f, Math.PI / 2.0D),
                "A fully paved, unobstructed diagonal should permit walking");

        // This wall is crossed midway through the chord, but not at either
        // endpoint. An endpoint-only collision check incorrectly accepts it.
        BlockPos wall = new BlockPos(6, 2, 6);
        helper.setBlock(wall, Blocks.STONE);
        helper.assertFalse(WorkerOrbitMovement.canMoveToAngle(
                horse, centerX, centerZ, 2.5f, Math.PI / 2.0D),
                "Intermediate block must prevent the entire teleport");
        var stopped = WorkerOrbitMovement.moveToAngle(
                horse, centerX, centerZ, 2.5f, 0.0D, Math.PI / 2.0D);
        helper.assertFalse(stopped.moved(), "Blocked move must not advance");
        helper.assertTrue(Math.abs(horse.getX() - startX) < 1.0e-5D
                        && Math.abs(horse.getZ() - startZ) < 1.0e-5D,
                "Worker may not tunnel to the collision-free endpoint");

        helper.setBlock(wall, Blocks.AIR);
        helper.assertTrue(WorkerOrbitMovement.canMoveToAngle(
                horse, centerX, centerZ, 2.5f, Math.PI / 2.0D),
                "Removing the intermediate wall must unblock the worker");
        helper.assertTrue(WorkerOrbitMovement.moveToAngle(
                horse, centerX, centerZ, 2.5f, 0.0D, Math.PI / 2.0D).moved(),
                "Worker must resume after wall removal");
        horse.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wideWorkerRequiresFullFootprintFloor(GameTestHelper helper) {
        BlockPos local = new BlockPos(3, 1, 3);
        helper.setBlock(local, Blocks.STONE);
        helper.setBlock(local.above(), Blocks.AIR);
        helper.setBlock(local.above(2), Blocks.AIR);
        double x = helper.absolutePos(local).getX() + 0.5D;
        double z = helper.absolutePos(local).getZ() + 0.5D;
        double y = helper.absolutePos(local).getY() + 1.0D;
        helper.assertTrue(net.steampn.createhorsepower.content.path.PathEvaluator.supportsWorkerFootprint(
                helper.getLevel(), x, y, z, 0.6f), "Small worker should fit");
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.supportsWorkerFootprint(
                helper.getLevel(), x, y, z, 2.2f), "Wide worker must reject unsupported edges");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos floor = local.offset(dx, 0, dz);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(), Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
        }
        helper.assertTrue(net.steampn.createhorsepower.content.path.PathEvaluator.supportsWorkerFootprint(
                helper.getLevel(), x, y, z, 2.2f), "Wide worker should pass with supported edges");
        helper.succeed();
    }

    /** NeoForge's empty GameTest template contains invisible barrier boundaries.
     * Clear the movement envelope without paving the deliberately unpaved center.
     */
    private static void clearOrbitHeadroom(GameTestHelper helper, BlockPos center) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                helper.setBlock(center.offset(dx, -1, dz), Blocks.AIR);
                helper.setBlock(center.offset(dx, 0, dz), Blocks.AIR);
                helper.setBlock(center.offset(dx, 1, dz), Blocks.AIR);
            }
        }
    }

    /** The built-in 28-tile ring must support an ordinary horse for the whole orbit. */
    @GameTest(template = "empty")
    public static void standardHorseOrbitKeepsSupportAcrossFullLap(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 2, 4);
        clearOrbitHeadroom(helper, center);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(2.5f)) {
            BlockPos floor = center.offset(offset);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(), Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
        }
        helper.setBlock(center, BlockRegister.HORSE_CRANK.get());
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(7, 2, 4));
        BlockPos worldCenter = helper.absolutePos(center);
        double x = worldCenter.getX() + 0.5D;
        double z = worldCenter.getZ() + 0.5D;
        double y = worldCenter.getY();
        horse.teleportTo(x + 2.5D, y, z);
        for (int angleIndex = 1; angleIndex <= 72; angleIndex++) {
            double oldAngle = (angleIndex - 1) * Math.PI / 36.0D;
            double newAngle = angleIndex * Math.PI / 36.0D;
            var step = WorkerOrbitMovement.moveToAngle(horse, x, z, 2.5f, oldAngle, newAngle);
            helper.assertTrue(Math.hypot(step.targetX() - step.actualX(),
                    step.targetZ() - step.actualZ()) < 1.0e-3D,
                    "Standard paved horse ring must remain traversable through angle " + angleIndex);
        }
        // A wide animal requires substantially more support than the normal horse.
        double diagonal = Math.PI / 4.0D;
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.supportsWorkerFootprint(
                helper.getLevel(), x + 2.5D * Math.cos(diagonal), y,
                z + 2.5D * Math.sin(diagonal), 2.2f),
                "Wide worker must not overhang a thin path ring without enough support");
        helper.setBlock(center.offset(3, -1, 0), Blocks.AIR);
        helper.assertFalse(net.steampn.createhorsepower.content.path.PathEvaluator.supportsWorkerFootprint(
                helper.getLevel(), x + 2.5D, y, z, horse.getBbWidth()),
                "Removing the directly supporting floor must stop the worker");
        horse.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void obstructedWorkerDoesNotPulseGeneration(GameTestHelper helper) {
        BlockPos center = new BlockPos(4, 2, 4);
        BlockPos[] offsets = HorseCrankEngine.generateOffsetsForRadius(2.5f);
        for (BlockPos offset : offsets) {
            BlockPos floor = center.offset(offset);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(), Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
        }
        helper.setBlock(center.offset(offsets[0]), Blocks.ICE);
        helper.setBlock(center, BlockRegister.HORSE_CRANK.get());
        HorseCrankEngine engine = requireCrank(helper, center).engine();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 4));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(
                helper.getLevel(), helper.absolutePos(center));
        horse.setLeashedTo(knot, false);
        WorkerStats stats = new WorkerStats(16.0f, 32.0f, 2.5f, 0.0f,
                WorkerStats.DEFAULT_SPEED_REF, 0.0f, WorkerStats.DEFAULT_HEALTH_REF,
                false, false);
        WorkerResolver.ResolvedWorker profile = new WorkerResolver.ResolvedWorker(
                stats, 16.0f, 32.0f, 0.0f, 0.0f, true);
        engine.attachWorker(horse, profile);
        engine.afterHostTick();
        helper.assertTrue(engine.isWorking(), "Worker should start on walkable ring");
        // A full collision wall, not another pushable mob. Minecraft's
        // physical block sweep must stop the worker without relying on
        // entity-pushing rules that can vary with other nearby animals.
        BlockPos obstacle = new BlockPos(7, 3, 4);
        helper.setBlock(obstacle, Blocks.STONE);
        engine.afterHostTick();
        helper.assertFalse(engine.isWorking(), "Dynamic collision must stop power");
        helper.assertTrue(engine.generatedSpeed() == 0.0f, "Blocked crank must have zero output");
        for (int i = 0; i < 5; i++) {
            engine.afterHostTick();
            helper.assertFalse(engine.isWorking(), "Blocker must not restart crank every tick");
        }
        helper.runAfterDelay(45, () -> {
            engine.afterHostTick();
            helper.assertFalse(engine.isWorking(),
                    "Persistent obstruction must not pulse even after multiple retry windows");
            helper.assertTrue(engine.generatedSpeed() == 0.0f,
                    "Persistent obstruction must keep kinetic output at zero");
            helper.setBlock(obstacle, Blocks.AIR);
            helper.runAfterDelay(23, () -> {
                engine.afterHostTick();
                helper.assertTrue(engine.isWorking(),
                        "Work should recover after the obstacle clears and the retry expires");
                engine.detachWorker(false);
                horse.discard();
                knot.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void freshDefaultPathConfigFallsThroughToBundledProfiles(GameTestHelper helper) {
        helper.assertTrue(Config.POOR_PATH.getDefault().isEmpty(),
                "Fresh 1.2+ poorPathBlock default must not shadow bundled path profiles");
        helper.assertTrue(Config.NORMAL_PATH.getDefault().isEmpty(),
                "Fresh 1.2+ normalPathBlock default must not shadow bundled path profiles");
        helper.assertTrue(Config.GREAT_PATH.getDefault().isEmpty(),
                "Fresh 1.2+ greatPathBlock default must not shadow bundled path profiles");
        helper.assertTrue(Math.abs(net.steampn.createhorsepower.content.stats.BuiltinProfiles.path(Blocks.DIRT)
                        .orElseThrow().speedMultiplier() - 0.70f) < 0.0001f,
                "Bundled dirt profile must remain 0.70 on a fresh install");
        helper.assertTrue(Math.abs(net.steampn.createhorsepower.content.stats.BuiltinProfiles.path(Blocks.GRAVEL)
                        .orElseThrow().speedMultiplier() - 1.10f) < 0.0001f,
                "Bundled gravel profile must remain 1.10 on a fresh install");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void attachmentJsonParserFeedsRealItemResolution(GameTestHelper helper) {
        List<AttachmentProfile> previous = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile parsed = net.steampn.createhorsepower.content.attachment.AttachmentProfileReloadListener.decode(
                    CHPApi.modId("gametest_json_attachment"),
                    com.google.gson.JsonParser.parseString("""
                            {"items":["minecraft:saddle"],"mode":"harness","priority":99,"consume_on_attach":true}
                            """).getAsJsonObject());
            AttachmentProfileRegistry.replace(List.of(parsed));
            AttachmentProfile resolved = AttachmentProfileRegistry.explicit(new ItemStack(Items.SADDLE)).orElseThrow();
            helper.assertTrue(resolved.id().equals(CHPApi.modId("gametest_json_attachment")),
                    "Parsed datapack JSON must resolve through the real item selector registry");
            helper.assertTrue(resolved.mode() == AttachmentMode.HARNESS,
                    "Parsed attachment mode must survive into runtime resolution");
        } finally {
            AttachmentProfileRegistry.replace(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedWorkerJsonAppliesMachineOverrideOnBothLoaders(GameTestHelper helper) {
        try {
            net.steampn.createhorsepower.content.stats.WorkerProfileRegistry.Entry entry =
                    net.steampn.createhorsepower.content.stats.WorkerProfileReloadListener.decode(
                            CHPApi.modId("gametest_json_worker"),
                            com.google.gson.JsonParser.parseString("""
                                    {"entity":"minecraft:horse","rpm":5.0,"stress":600.0,"machines":{"createhorsepower:horse_crank":{"rpm":7.5,"movement_radius":2.0}}}
                                    """).getAsJsonObject());
            net.steampn.createhorsepower.content.stats.WorkerProfileRegistry.replace(List.of(entry));
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 1, 0));
            WorkerResolver.ResolvedWorker resolved = WorkerResolver.resolve(horse, "createhorsepower:horse_crank");
            helper.assertTrue(resolved.isValid(), "Shared JSON worker profile must resolve on this loader");
            helper.assertTrue("datapack_json".equals(resolved.source()),
                    "Shared JSON must be reported as the selected worker-profile source");
            helper.assertTrue(resolved.machineOverrideApplied(), "Machine override must be reported as applied");
            helper.assertTrue(Math.abs(resolved.baseStats().baseRpm() - 7.5f) < 0.0001f,
                    "Machine-specific JSON RPM override must reach runtime resolution");
            helper.assertTrue(Math.abs(resolved.baseStats().movementRadius() - 2.0f) < 0.0001f,
                    "Machine-specific JSON movement radius must reach runtime resolution");
        } finally {
            net.steampn.createhorsepower.content.stats.WorkerProfileRegistry.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crankRegistrations(GameTestHelper helper) {
        BlockRegister.HORSE_CRANK.get();
        TileEntityRegister.HORSE_CRANK.get();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonHorseWorkerUsesSameOrbitControl(GameTestHelper helper) {
        prepareOrbitMovementFloor(helper);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        double radius = 1.0D;
        double centerX = cow.getX() - radius;
        double centerZ = cow.getZ();
        boolean ownsAiSuppression = WorkerActivityControl.acquire(cow);

        helper.assertTrue(ownsAiSuppression, "Crank must suppress AI for non-horse workers");
        helper.assertTrue(cow.isNoAi(), "Working non-horse AI must be suppressed");
        double orbitAngle = WorkerOrbitMovement.angleFromPosition(cow.getX(), cow.getZ(), centerX, centerZ);
        assertOrbitStep(helper, cow, centerX, centerZ, radius, orbitAngle);
        WorkerActivityControl.release(cow, ownsAiSuppression);
        helper.assertTrue(!cow.isNoAi(), "Non-horse AI must be restored after work");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void workerOrbitFacesMovement(GameTestHelper helper) {
        prepareOrbitMovementFloor(helper);
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 2, 3));
        double radius = 1.0D;
        double centerX = horse.getX() - radius;
        double centerZ = horse.getZ();
        horse.setEating(true);
        boolean ownsAiSuppression = WorkerActivityControl.acquire(horse);
        helper.assertTrue(ownsAiSuppression, "Crank must own suppression for an ordinary AI-enabled horse");
        helper.assertTrue(horse.isNoAi(), "Working horse AI must be suppressed");

        double orbitAngle = WorkerOrbitMovement.angleFromPosition(horse.getX(), horse.getZ(), centerX, centerZ);
        orbitAngle = assertOrbitStep(helper, horse, centerX, centerZ, radius, orbitAngle);
        assertOrbitStep(helper, horse, centerX, centerZ, radius, orbitAngle);
        helper.assertTrue(!horse.isEating(), "Working horses must not remain in their eating animation");

        WorkerActivityControl.release(horse, ownsAiSuppression);
        helper.assertTrue(!horse.isNoAi(), "Crank-owned AI suppression must be restored after work");
        horse.setNoAi(true);
        boolean ownsPreexistingSuppression = WorkerActivityControl.acquire(horse);
        helper.assertTrue(!ownsPreexistingSuppression, "Crank must not own a pre-existing NoAI state");
        WorkerActivityControl.release(horse, ownsPreexistingSuppression);
        helper.assertTrue(horse.isNoAi(), "Pre-existing NoAI state must survive crank release");
        helper.succeed();
    }

    /**
     * A worker is acquired by crank A, then crank A is removed (its local
     * ownership record is gone) and the marker is recovered purely from the
     * mob's persistent data. After recovery, an ordinary AI-enabled mob must
     * be back to NoAI=false.
     */
    @GameTest(template = "empty")
    public static void strandedWorkerAiIsRecoveredFromMarker(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        helper.assertTrue(!horse.isNoAi(), "Pre-condition: ordinary horse has AI enabled");

        // Simulate crank A acquiring control, then disappearing.
        UUID crankA = new UUID(0x1111L, 0xAAAAAAAAL);
        boolean ownsAiSuppression = WorkerActivityControl.acquire(horse, new BlockPos(3, 1, 3), crankA);
        helper.assertTrue(ownsAiSuppression, "Crank A must own the suppression for an ordinary AI-enabled horse");
        helper.assertTrue(horse.isNoAi(), "Crank A suppresses the horse's AI");

        // Crank A's BE is gone; we never call release(). Recovery must come
        // from the persistent marker on the worker.
        boolean recovered = WorkerActivityControl.releaseFromMarker(horse);
        helper.assertTrue(recovered, "releaseFromMarker must find the stranded marker");
        helper.assertTrue(!horse.isNoAi(), "Stranded horse must return to NoAI=false after marker recovery");
        helper.succeed();
    }

    /**
     * A worker that was already NoAI=true before the crank touched it must
     * stay that way after both the fast-path release and the marker-only
     * recovery path.
     */
    @GameTest(template = "empty")
    public static void preexistingNoAiSurvivesMarkerRecovery(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        horse.setNoAi(true);

        UUID crankA = new UUID(0x2222L, 0xBBBBBBBBL);
        WorkerActivityControl.acquire(horse, new BlockPos(3, 1, 3), crankA);
        // Crank A disappears without calling release.
        boolean recovered = WorkerActivityControl.releaseFromMarker(horse);
        helper.assertTrue(recovered, "releaseFromMarker must find the stranded marker");
        helper.assertTrue(horse.isNoAi(), "Pre-existing NoAI must survive marker recovery");
        helper.succeed();
    }

    /**
     * If a worker was already under the control of a different crank that
     * later disappeared, the next attach to a fresh crank must clear the
     * stale marker before issuing its own acquisition, so the worker ends up
     * in the correct NoAI state (true) and the new crank ends up owning the
     * suppression.
     */
    @GameTest(template = "empty")
    public static void reassignmentClearsStaleMarker(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        helper.assertTrue(!cow.isNoAi(), "Pre-condition: ordinary cow has AI enabled");

        UUID crankA = new UUID(0x3333L, 0xCCCCCCCCL);
        UUID crankB = new UUID(0x4444L, 0xDDDDDDDDL);

        // Crank A acquires the cow, then disappears.
        WorkerActivityControl.acquire(cow, new BlockPos(1, 1, 1), crankA);
        helper.assertTrue(cow.isNoAi(), "Crank A suppresses the cow's AI");
        helper.assertTrue(WorkerActivityControl.hasForeignMarker(cow, crankB),
                "Crank B must see crank A's marker as foreign");

        // Crank B picks up the cow without crank A having released it.
        WorkerActivityControl.releaseFromMarker(cow);
        helper.assertTrue(!cow.isNoAi(), "Stale marker recovery restores the cow's original AI");

        boolean ownsB = WorkerActivityControl.acquire(cow, new BlockPos(2, 1, 2), crankB);
        helper.assertTrue(ownsB, "Crank B must own the suppression for an AI-enabled cow");
        helper.assertTrue(cow.isNoAi(), "Crank B suppresses the cow's AI");
        helper.assertTrue(!WorkerActivityControl.hasForeignMarker(cow, crankB),
                "Crank B's own marker is not foreign to itself");

        WorkerActivityControl.release(cow, ownsB);
        helper.assertTrue(!cow.isNoAi(), "Crank B's release returns the cow to NoAI=false");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noMarkerMeansNothingToRelease(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        helper.assertTrue(!WorkerActivityControl.releaseFromMarker(cow),
                "releaseFromMarker on a clean worker is a no-op");
        helper.assertTrue(!cow.isNoAi(), "Untouched worker stays at NoAI=false");
        helper.succeed();
    }

    /**
     * Two consecutive {@link WorkerActivityControl#acquire} calls from the
     * same crank must preserve the worker's original {@code NoAI} state.
     * The previous implementation overwrote {@code PreviousNoAi} with the
     * mob's current {@code NoAI} (which is {@code true} after the first
     * acquire), losing the original baseline.
     */
    @GameTest(template = "empty")
    public static void sameOwnerReacquirePreservesOriginalNoAi(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        helper.assertTrue(!horse.isNoAi(), "Pre-condition: ordinary horse has AI enabled");

        UUID crank = new UUID(0x5555L, 0xEEEEEEEEL);
        BlockPos pos = new BlockPos(1, 1, 1);

        boolean first = WorkerActivityControl.acquire(horse, pos, crank);
        helper.assertTrue(first, "First acquire must claim ownership for an AI-enabled horse");
        helper.assertTrue(horse.isNoAi(), "First acquire must suppress AI");
        helper.assertFalse(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must still record original NoAI=false after first acquire");

        boolean second = WorkerActivityControl.acquire(horse, pos, crank);
        helper.assertTrue(second, "Second acquire from the same crank must still own the suppression");
        helper.assertTrue(horse.isNoAi(), "Second acquire must keep the horse NoAI");
        helper.assertFalse(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must STILL record original NoAI=false after second acquire");

        WorkerActivityControl.release(horse, second);
        helper.assertFalse(horse.isNoAi(), "Final release must return the horse to its original NoAI");
        helper.succeed();
    }

    /**
     * A worker that started out with {@code NoAI=true} must stay that way
     * after a same-crank reacquire. The crank must not own a pre-existing
     * NoAI state.
     */
    @GameTest(template = "empty")
    public static void preexistingNoAiSurvivesSameOwnerReacquire(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        horse.setNoAi(true);

        UUID crank = new UUID(0x6666L, 0xFFFFFFFEL);
        BlockPos pos = new BlockPos(2, 1, 2);

        boolean first = WorkerActivityControl.acquire(horse, pos, crank);
        boolean second = WorkerActivityControl.acquire(horse, pos, crank);

        helper.assertFalse(first, "Crank must not own a pre-existing NoAI state");
        helper.assertFalse(second, "Same-crank reacquire must not flip ownership either");
        helper.assertTrue(horse.isNoAi(), "Preexisting NoAI must still hold");
        helper.assertTrue(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must record the original NoAI=true across the reacquire");

        WorkerActivityControl.release(horse, second);
        helper.assertTrue(horse.isNoAi(), "Final release must leave the horse at its original NoAI=true");
        helper.succeed();
    }

    /**
     * Two different crank UUIDs at the same block position must NOT be
     * treated as the same owner. The previous position-derived UUID would
     * have failed this test, allowing a fresh crank to inherit a stale
     * marker from a predecessor.
     */
    @GameTest(template = "empty")
    public static void replacementCrankAtSamePositionIsForeign(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        helper.assertTrue(!cow.isNoAi(), "Pre-condition: ordinary cow has AI enabled");

        UUID crankA = new UUID(0x7777L, 0xAAAAAAAAAAAAL);
        UUID crankB = new UUID(0x8888L, 0xBBBBBBBBBBBBL);
        helper.assertTrue(!crankA.equals(crankB), "Pre-condition: test UUIDs must differ");
        BlockPos samePos = new BlockPos(3, 1, 3);

        boolean first = WorkerActivityControl.acquire(cow, samePos, crankA);
        helper.assertTrue(first, "Crank A must own the suppression");
        helper.assertTrue(cow.isNoAi(), "Crank A suppresses the cow's AI");
        helper.assertTrue(WorkerActivityControl.hasForeignMarker(cow, crankB),
                "Crank B at the same position must see crank A's marker as foreign");
        helper.assertFalse(WorkerActivityControl.hasForeignMarker(cow, crankA),
                "Crank A's own marker is not foreign to itself");

        WorkerActivityControl.release(cow, first);
        helper.succeed();
    }

    /**
     * When a marked mob loads into a server world and the original crank no
     * longer exists at the recorded position, the marker must be consumed
     * and the mob's original {@code NoAI} state restored.
     */
    @GameTest(template = "empty")
    public static void orphanMarkerIsRecoveredOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
        helper.assertTrue(level.getBlockEntity(worldCrankPos) == null,
                "Pre-condition: no crank at the recorded position");

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 3));
        UUID crankA = new UUID(0x9999L, 0xCCCCCCCCCL);
        boolean owns = WorkerActivityControl.acquire(horse, worldCrankPos, crankA);
        helper.assertTrue(owns, "Crank A must own the suppression");
        helper.assertTrue(horse.isNoAi(), "Crank A suppresses the horse's AI");
        helper.assertTrue(WorkerActivityControl.hasMarker(horse), "Marker must be present");

        boolean recovered = WorkerActivityControl.recoverIfOrphaned(horse, level);
        helper.assertTrue(recovered, "recoverIfOrphaned must consume the marker when no live crank exists");
        helper.assertFalse(horse.isNoAi(), "Recovered horse must return to NoAI=false");
        helper.assertFalse(WorkerActivityControl.hasMarker(horse), "Marker must be removed after recovery");
        helper.succeed();
    }

    /**
     * When a marked mob loads and the live crank at the recorded position
     * still has a matching instance UUID and still claims this mob, the
     * marker must be left alone so the live crank can finish its work.
     */
    @GameTest(template = "empty")
    public static void liveCrankWithMatchingUuidKeepsMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());

        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        UUID liveUuid = new UUID(0xAAAA0L, 0xDDDDDDD0L);
        crank.engine().setCrankInstanceUuidForTesting(liveUuid);

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 3));
        boolean owns = WorkerActivityControl.acquire(horse, worldCrankPos, liveUuid);
        helper.assertTrue(owns, "Pre-condition: this acquire must own the suppression");

        crank.engine().setWorkerUuidForTesting(horse.getUUID());
        helper.assertTrue(crank.engine().isAssignedWorker(horse.getUUID()),
                "Pre-condition: live crank claims this horse");
        helper.assertTrue(WorkerActivityControl.markerCrankUuid(horse).equals(liveUuid),
                "Pre-condition: marker must record the live UUID");
        helper.assertTrue(WorkerActivityControl.markerCrankPos(horse).equals(worldCrankPos),
                "Pre-condition: marker must record the live crank position");

        boolean recovered = WorkerActivityControl.recoverIfOrphaned(horse, level);
        helper.assertFalse(recovered, "recoverIfOrphaned must NOT consume a marker that matches a live crank");
        helper.assertTrue(WorkerActivityControl.hasMarker(horse), "Marker must remain on the worker");
        helper.assertTrue(horse.isNoAi(), "Live crank still owns the suppression");

        WorkerActivityControl.release(horse, owns);
        helper.succeed();
    }

    /**
     * When crank A acquires a worker and then disappears, and a brand-new
     * crank B (different UUID) is placed at the same coordinates, the
     * worker's marker must be considered orphaned because crank B is not
     * the recorded owner even though it is at the same block.
     */
    @GameTest(template = "empty")
    public static void foreignReplacementCrankTriggersOrphan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);

        UUID crankA = new UUID(0xCAFE0L, 0x11111111L);
        UUID crankB = new UUID(0xCAFE0L, 0x22222222L);
        helper.assertTrue(!crankA.equals(crankB), "Pre-condition: replacement UUIDs must differ");

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 3));
        boolean owns = WorkerActivityControl.acquire(horse, worldCrankPos, crankA);
        helper.assertTrue(owns, "Crank A must own the suppression initially");
        helper.assertTrue(horse.isNoAi(), "Crank A suppresses the horse's AI");

        // Crank A vanishes (no block entity at the recorded position).
        // A new crank B is placed at the same coordinates.
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity replacement = requireCrank(helper, localCrankPos);
        replacement.engine().setCrankInstanceUuidForTesting(crankB);
        // Crank B does NOT claim the original worker.

        boolean recovered = WorkerActivityControl.recoverIfOrphaned(horse, level);
        helper.assertTrue(recovered,
                "Marker must be recovered when the live crank at the recorded position is a different instance");
        helper.assertFalse(horse.isNoAi(), "Recovered horse must return to NoAI=false");
        helper.assertFalse(WorkerActivityControl.hasMarker(horse), "Marker must be removed after recovery");
        helper.succeed();
    }

    /**
     * Leash cleanup must resolve the crank's stored worker UUID instead of
     * relying only on the nearby-entity fallback. Workers can be pulled or
     * teleported outside that fallback radius while their leash still points
     * at the crank knot; detaching in that state used to leave a ghost leash.
     */
    @GameTest(template = "empty")
    public static void detachReleasesAssignedWorkerOutsideCleanupRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 0, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, worldCrankPos);
        horse.setLeashedTo(knot, false);
        engine.attachWorker(horse, WorkerResolver.resolve(horse));

        // CHPUtils' fallback scan is capped at MAX_MOVEMENT_RADIUS + 4 (10
        // blocks). Move the assigned worker well beyond it while keeping the
        // same leash holder to reproduce the stale-knot failure mode.
        horse.setPos(worldCrankPos.getX() + 20.5D, worldCrankPos.getY(), worldCrankPos.getZ() + 0.5D);
        helper.assertTrue(horse.isLeashed(), "Pre-condition: worker is still leashed to the crank knot");
        helper.assertTrue(horse.getLeashHolder() == knot, "Pre-condition: crank knot is the leash holder");

        engine.detachWorker(false);

        helper.assertFalse(horse.isLeashed(),
                "Detach must release the assigned worker even outside the fallback search radius");
        helper.assertTrue(horse.getLeashHolder() == null,
                "Detached worker must not retain a stale leash-holder reference");
        helper.assertFalse(knot.isAlive(), "Crank knot must be discarded during detach cleanup");
        helper.succeed();
    }


    /**
     * Full persisted-leash regression: save a crank-controlled horse while its
     * chunk is conceptually unloaded, detach the crank while the entity is
     * absent, then reload the horse. Vanilla restores the saved BlockPos leash
     * by recreating a knot; CHP must recover after that restoration and remove
     * both the stale leash and now-unused knot.
     */
    @GameTest(template = "empty", timeoutTicks = 30, batch = "chp_recovery_ghost_leash")
    public static void reloadedWorkerDoesNotRecreateGhostCrankLeash(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 0, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, worldCrankPos);
        horse.setLeashedTo(knot, false);
        engine.attachWorker(horse, WorkerResolver.resolve(horse));
        engine.controlWorkerAiForTesting(horse);
        helper.assertTrue(WorkerAttachmentControl.hasMarker(horse),
                "Pre-condition: attached worker must carry persistent attachment ownership");
        helper.assertTrue(WorkerActivityControl.hasMarker(horse),
                "Pre-condition: controlled worker must also carry the AI marker");

        CompoundTag saved = horse.saveWithoutId(new CompoundTag());
        UUID workerUuid = horse.getUUID();

        // Simulate the worker chunk being absent when the crank is detached.
        horse.discard();
        engine.setCachedWorkerMobForTesting(null);
        engine.detachWorker(false);
        helper.assertTrue(CHPUtils.getKnot(level, worldCrankPos).isEmpty(),
                "Detach clears the currently loaded knot even though the worker is absent");

        Horse[] reloadedHolder = new Horse[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getEntity(workerUuid) == null,
                        "discarded worker must leave the level UUID index before reload"))
                .thenExecute(() -> {
                    Horse reloaded = EntityType.HORSE.create(level);
                    helper.assertTrue(reloaded != null, "Failed to recreate saved horse");
                    reloaded.load(saved);
                    helper.assertTrue(reloaded.getUUID().equals(workerUuid),
                            "Reload must preserve the worker UUID");
                    helper.assertTrue(WorkerAttachmentControl.hasMarker(reloaded),
                            "Reloaded worker must retain attachment ownership before recovery");
                    helper.assertTrue(WorkerActivityControl.hasMarker(reloaded),
                            "Reloaded worker must retain AI ownership before recovery");
                    helper.assertTrue(level.addFreshEntity(reloaded),
                            "Reloaded worker must register for ghost-leash recovery");
                    reloadedHolder[0] = reloaded;
                    helper.assertTrue(WorkerRecoveryQueue.isPendingForTesting(workerUuid),
                            "EntityJoin must queue the reloaded worker for recovery");
                })
                .thenExecute(() -> {
                    Horse reloaded = reloadedHolder[0];
                    helper.assertTrue(reloaded != null, "reloaded worker fixture must be initialized");

                    long leadsBefore = level.getEntitiesOfClass(
                            ItemEntity.class, new AABB(reloaded.blockPosition()).inflate(8.0D),
                            item -> item.getItem().is(Items.LEAD)).size();

                    // Vanilla restores the persisted BlockPos leash during baseTick.
                    // Invoke that exact path explicitly because GameTest fixture mobs
                    // are not guaranteed to receive a scheduler-driven tick.
                    reloaded.baseTick();
                    helper.assertTrue(reloaded.isLeashed(),
                            "vanilla must recreate the saved crank leash before CHP recovery");
                    helper.assertTrue(CHPUtils.getKnot(level, worldCrankPos).isPresent(),
                            "vanilla must recreate the saved crank knot before CHP recovery");

                    // baseTick restores the leash but does not advance tickCount.
                    // Advance the queue eligibility sentinel to model completion of
                    // that entity tick before the post-level-tick recovery pass.
                    reloaded.tickCount++;
                    WorkerRecoveryQueue.process(level);
                    helper.assertFalse(reloaded.isLeashed(),
                            "Reloaded orphan worker must not remain leashed to the old crank position");
                    helper.assertTrue(reloaded.getLeashHolder() == null,
                            "Reloaded orphan worker must have no stale leash holder");
                    helper.assertFalse(WorkerAttachmentControl.hasMarker(reloaded),
                            "Recovery must consume stale attachment ownership");
                    helper.assertFalse(WorkerActivityControl.hasMarker(reloaded),
                            "Recovery must consume the stale AI marker");
                    helper.assertFalse(reloaded.isNoAi(),
                            "Recovery must restore the worker's original NoAI=false state");
                    helper.assertTrue(CHPUtils.getKnot(level, worldCrankPos).isEmpty(),
                            "Recovery must remove the unused knot recreated from saved leash data");
                    long leadsAfter = level.getEntitiesOfClass(
                            ItemEntity.class, new AABB(reloaded.blockPosition()).inflate(8.0D),
                            item -> item.getItem().is(Items.LEAD)).size();
                    helper.assertTrue(leadsAfter == leadsBefore,
                            "Deferred detachWorker(false) must not spawn a new lead when the worker reloads");
                })
                .thenSucceed();
    }


    /**
     * Engine-level regression for workers that already had NoAI=true before
     * the crank started controlling them. The crank must own/maintain the CHP
     * activity marker without claiming the NoAI transition, must not rewrite
     * that marker every working tick, and must clear it on detach while
     * preserving the worker's original NoAI=true state.
     */
    @GameTest(template = "empty")
    public static void preexistingNoAiEngineMarkerClearsOnDetach(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 0, 2));
        horse.setNoAi(true);
        engine.setWorkerUuidForTesting(horse.getUUID());
        engine.setCachedWorkerMobForTesting(horse);

        engine.controlWorkerAiForTesting(horse);
        helper.assertTrue(engine.ownsWorkerActivityMarkerForTesting(),
                "Engine must own the CHP activity marker even for pre-existing NoAI");
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Engine must not claim a pre-existing NoAI transition");
        helper.assertTrue(WorkerActivityControl.hasMarker(horse),
                "Pre-existing NoAI worker must carry a recoverable CHP marker");
        helper.assertTrue(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must preserve the original NoAI=true baseline");

        CompoundTag marker = horse.getPersistentData().getCompound(WorkerActivityControl.MARKER_KEY);
        marker.putBoolean("TestSentinel", true);
        horse.getPersistentData().put(WorkerActivityControl.MARKER_KEY, marker);

        for (int i = 0; i < 5; i++) {
            engine.controlWorkerAiForTesting(horse);
        }
        helper.assertTrue(horse.getPersistentData()
                        .getCompound(WorkerActivityControl.MARKER_KEY)
                        .getBoolean("TestSentinel"),
                "Repeated control must maintain the same marker instead of reacquiring/rewriting it");

        CompoundTag saved = new CompoundTag();
        engine.write(saved, false);
        helper.assertTrue(saved.getBoolean("OwnsWorkerActivityMarker"),
                "Marker ownership must persist across crank saves");
        helper.assertFalse(saved.getBoolean("OwnsWorkerAiSuppression"),
                "Saved state must keep marker ownership separate from NoAI-transition ownership");

        engine.detachWorker(false);
        helper.assertTrue(horse.isNoAi(),
                "Detach must preserve the worker's original NoAI=true state");
        helper.assertFalse(WorkerActivityControl.hasMarker(horse),
                "Detach must clear this crank's CHP activity marker");
        helper.assertFalse(engine.ownsWorkerActivityMarkerForTesting(),
                "Engine must drop marker ownership after detach");
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Engine must own no NoAI transition after detach");
        helper.assertTrue(engine.aiSuppressedWorkerUuidForTesting() == null,
                "Engine must clear the controlled worker UUID after detach");
        helper.succeed();
    }

    /**
     * Permanent-detach regression: when the engine owns worker A and A
     * becomes unresolvable (discarded/unloaded), a permanent detach must
     * drop the engine-side AI-suppression ownership even though restoration
     * could not run. Otherwise the ownership record stays stuck on A and
     * the next worker B is merely maintained ({@code NoAI=true} without a
     * marker of its own) instead of acquiring its own recoverable marker --
     * a permanent frozen-animal bug.
     */
    @GameTest(template = "empty")
    public static void permanentClearDropsOwnershipForNextWorker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();

        // Worker A is acquired by the engine through its normal control path.
        Horse workerA = helper.spawn(EntityType.HORSE, new BlockPos(2, 0, 2));
        helper.assertFalse(workerA.isNoAi(), "Pre-condition: horse A has AI enabled");
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Pre-condition: a fresh crank owns no AI suppression");
        engine.controlWorkerAiForTesting(workerA);
        helper.assertTrue(workerA.isNoAi(), "Engine must suppress worker A's AI");
        helper.assertTrue(engine.ownsWorkerAiSuppressionForTesting(),
                "Engine must own worker A's suppression");
        helper.assertTrue(workerA.getUUID().equals(engine.aiSuppressedWorkerUuidForTesting()),
                "Engine's suppression record must name worker A");
        helper.assertTrue(WorkerActivityControl.hasMarker(workerA), "Worker A must carry a marker");

        // Worker A becomes unresolvable: discarded, so the level's entity
        // index no longer contains it. The cached reference is also dropped
        // to simulate a chunk-unload/BE-reload, where ownership survives in
        // NBT but no entity reference remains and the level lookup fails.
        workerA.discard();
        engine.setCachedWorkerMobForTesting(null);
        helper.assertTrue(level.getEntity(workerA.getUUID()) == null,
                "Pre-condition: discarded worker A is unresolved in the level");

        // Permanent detach: restoreWorkerAi cannot resolve A, so the clear
        // must still abandon the engine-side ownership record.
        engine.detachWorker(false);
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Permanent clear must abandon AI suppression ownership");
        helper.assertTrue(engine.aiSuppressedWorkerUuidForTesting() == null,
                "Permanent clear must drop the suppressed worker record");

        // Worker B attaches and starts work. It must receive its OWN marker
        // naming this crank's instance UUID; a stale ownership record would
        // have left B marker-less and permanently NoAI.
        Cow workerB = helper.spawn(EntityType.COW, new BlockPos(3, 0, 3));
        helper.assertFalse(workerB.isNoAi(), "Pre-condition: cow B has AI enabled");
        engine.attachWorker(workerB, WorkerResolver.resolve(workerB));
        engine.controlWorkerAiForTesting(workerB);
        helper.assertTrue(workerB.isNoAi(), "Engine must suppress worker B's AI");
        helper.assertTrue(WorkerActivityControl.hasMarker(workerB),
                "Worker B must receive its own recovery marker");
        UUID crankUuid = engine.crankInstanceUuid();
        helper.assertTrue(crankUuid.equals(WorkerActivityControl.markerCrankUuid(workerB)),
                "Worker B's marker must name this crank's instance UUID");
        helper.assertFalse(WorkerActivityControl.readPreviousNoAi(workerB),
                "Worker B's marker must record B's original NoAI=false");

        // Detaching B restores it cleanly through its own marker.
        engine.detachWorker(false);
        helper.assertFalse(workerB.isNoAi(), "Worker B must return to NoAI=false after detach");
        helper.assertFalse(WorkerActivityControl.hasMarker(workerB),
                "Worker B's marker must be removed after detach");
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Engine must not own any suppression after detaching B");
        helper.succeed();
    }


    /**
     * Production-shape regression for TFG: a 16 RPM / 32 SU worker on the
     * default Great ice path keeps the 2x RPM path bonus while path stress
     * scaling is disabled, leaving final stress capacity at exactly 32 SU.
     *
     * The worker profile and one path-policy getter are scoped dependencies:
     * mutating a watched loader config would let asynchronous reload callbacks
     * race this fixture. Every other platform option and service remains real.
     * Block resolution, evaluator, crank update, and serialized output exercise
     * the actual runtime path with the disabled policy contract.
     */
    @GameTest(template = "empty")
    public static void disabledPathStressScalingKeeps32SuWhileGreatPathBoostsRpm(GameTestHelper helper) {
        var originalConfig = CHPApi.config();
        var originalScripts = CHPApi.scripts();
        var originalDetaches = CHPApi.deferredDetaches();
        var originalItemStacks = CHPApi.itemStacks();
        Config disabledPathPolicy = new Config() {
            @Override public boolean enablePathStressScaling() { return false; }
        };

        try {
            CHPApi.init(disabledPathPolicy, originalScripts, net.minecraft.resources.ResourceLocation::fromNamespaceAndPath,
                    originalDetaches, originalItemStacks);
            helper.assertFalse(CHPApi.config().enablePathStressScaling(),
                    "Fixture must provide the disabled path-stress policy to the engine");

            BlockPos localCrankPos = new BlockPos(4, 2, 4);
            BlockPos[] offsets = HorseCrankEngine.generateOffsetsForRadius(2.5f);
            for (BlockPos offset : offsets) {
                BlockPos floor = localCrankPos.offset(offset);
                helper.setBlock(floor, Blocks.ICE);
                helper.setBlock(floor.above(), Blocks.AIR);
                helper.setBlock(floor.above(2), Blocks.AIR);
            }
            assertWalkableRing(helper, localCrankPos, offsets);

            helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
            AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
            HorseCrankEngine engine = crank.engine();
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));

            WorkerStats fixtureStats = new WorkerStats(
                    16.0f, 32.0f, 2.5f,
                    0.0f, WorkerStats.DEFAULT_SPEED_REF,
                    0.0f, WorkerStats.DEFAULT_HEALTH_REF,
                    false, false
            );
            WorkerResolver.ResolvedWorker fixture = new WorkerResolver.ResolvedWorker(
                    fixtureStats, 16.0f, 32.0f, 0.0f, 0.0f, true);
            engine.attachWorker(horse, fixture);

            CompoundTag state = new CompoundTag();
            engine.write(state, false);

            helper.assertTrue(state.getBoolean("HasValidWorkingBlocks"),
                    "Great path ring must be valid for the attached horse");
            assertNear(helper, 16.0f, state.getFloat("EffectiveBaseRpm"),
                    "fixture base RPM must reach the real crank engine");
            assertNear(helper, 32.0f, state.getFloat("EffectiveBaseStress"),
                    "fixture base stress must reach the real crank engine");
            assertNear(helper, 2.0f, state.getFloat("RpmModifier"),
                    "Great path must keep its 2x RPM multiplier");
            assertNear(helper, 1.0f, state.getFloat("PathStressModifier"),
                    "disabled path stress scaling must leave crank path stress neutral");

            float finalStress = state.getFloat("EffectiveBaseStress") * state.getFloat("PathStressModifier");
            assertNear(helper, 32.0f, finalStress,
                    "32 SU base must remain exactly 32 SU on a Great path");
        } finally {
            CHPApi.init(originalConfig, originalScripts, net.minecraft.resources.ResourceLocation::fromNamespaceAndPath,
                    originalDetaches, originalItemStacks);
        }

        helper.succeed();
    }


    @GameTest(template = "kinetic_network")
    public static void loadedVanillaLeashProfileReturnsActuallyConsumedAttachment(GameTestHelper helper) {
        List<AttachmentProfile> previousProfiles = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile profile = new AttachmentProfile(
                    CHPApi.modId("gametest_consuming_vanilla"),
                    1000,
                    Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.SADDLE)),
                    Set.of(),
                    AttachmentMode.VANILLA_LEASH,
                    3.0f,
                    Set.of(),
                    Set.of(),
                    Set.of(CHPApi.modId("horse_crank")),
                    true,
                    true,
                    1.0f
            );
            AttachmentProfileRegistry.replace(List.of(profile));

            BlockPos localCrankPos = new BlockPos(4, 2, 4);
            helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
            AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
            BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
            ServerLevel level = helper.getLevel();
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));
            net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            horse.setLeashedTo(player, false);
            ItemStack attachmentStack = new ItemStack(Items.SADDLE);

            int beforeSaddles = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldCrankPos).inflate(4.0D),
                    entity -> entity.getItem().is(Items.SADDLE)
            ).size();

            assertSelectableWorker(helper, worldCrankPos, horse, player);
            HorseCrankInteractions.Outcome outcome = HorseCrankInteractions.attachAt(
                    level, worldCrankPos, level.getBlockState(worldCrankPos), player, attachmentStack);
            helper.assertTrue(outcome == HorseCrankInteractions.Outcome.SUCCESS,
                    "Real interaction must attach the worker through profile resolution");
            helper.assertTrue(attachmentStack.isEmpty(),
                    "Survival consuming profile must remove exactly one attachment item");

            crank.engine().detachWorker(true);

            int afterSaddles = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldCrankPos).inflate(4.0D),
                    entity -> entity.getItem().is(Items.SADDLE)
            ).size();
            helper.assertTrue(afterSaddles == beforeSaddles + 1,
                    "Loaded vanilla-leash detach must return the consumed profile attachment item");
        } finally {
            AttachmentProfileRegistry.replace(previousProfiles);
        }
        helper.succeed();
    }


    @GameTest(template = "kinetic_network")
    public static void vanillaLeashBoundaryWorkerMustActuallyBindBeforeConsumption(GameTestHelper helper) {
        List<AttachmentProfile> previousProfiles = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile profile = new AttachmentProfile(
                    CHPApi.modId("gametest_vanilla_boundary"),
                    1000,
                    Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.SADDLE)),
                    Set.of(),
                    AttachmentMode.VANILLA_LEASH,
                    3.0f,
                    Set.of(),
                    Set.of(),
                    Set.of(CHPApi.modId("horse_crank")),
                    true,
                    true,
                    1.0f
            );
            AttachmentProfileRegistry.replace(List.of(profile));

            BlockPos localCrankPos = new BlockPos(4, 2, 4);
            helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
            AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
            BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
            ServerLevel level = helper.getLevel();

            // CHP's selection AABB reaches one block farther on the positive
            // edge than vanilla LeadItem.bindPlayerMobs(). This position is in
            // that strip: it must either be bound specifically or rejected,
            // never consumed/recorded without a real backend.
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(12, 2, 4));
            net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            horse.setLeashedTo(player, false);
            ItemStack attachmentStack = new ItemStack(Items.SADDLE);

            assertSelectableWorker(helper, worldCrankPos, horse, player);
            HorseCrankInteractions.Outcome outcome = HorseCrankInteractions.attachAt(
                    level, worldCrankPos, level.getBlockState(worldCrankPos), player, attachmentStack);

            helper.assertTrue(outcome == HorseCrankInteractions.Outcome.SUCCESS,
                    "Boundary worker attach must be handled successfully");
            helper.assertTrue(CHPUtils.isLeashedToKnotAt(horse, worldCrankPos),
                    "Selected boundary worker must actually be leashed to this crank before ownership is recorded");
            helper.assertTrue(attachmentStack.isEmpty(),
                    "Consumed vanilla-leash attachment is valid only after the exact worker backend exists");
            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                            horse, worldCrankPos, crank.engine().crankInstanceUuid()),
                    "Exact worker ownership must be recorded only after successful binding");

            crank.engine().detachWorker(true);
        } finally {
            AttachmentProfileRegistry.replace(previousProfiles);
        }
        helper.succeed();
    }

    @GameTest(template = "kinetic_network")
    public static void creativeConsumingProfileNeverRecordsOrDropsConsumedItem(GameTestHelper helper) {
        List<AttachmentProfile> previousProfiles = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile profile = new AttachmentProfile(
                    CHPApi.modId("gametest_creative_harness"),
                    1000,
                    Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.SADDLE)),
                    Set.of(),
                    AttachmentMode.HARNESS,
                    3.0f,
                    Set.of(),
                    Set.of(),
                    Set.of(CHPApi.modId("horse_crank")),
                    true,
                    true,
                    1.0f
            );
            AttachmentProfileRegistry.replace(List.of(profile));

            BlockPos localCrankPos = new BlockPos(4, 2, 4);
            helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
            AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
            BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
            ServerLevel level = helper.getLevel();
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));
            net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            player.getAbilities().instabuild = true;
            horse.setLeashedTo(player, false);
            ItemStack attachmentStack = new ItemStack(Items.SADDLE);

            int beforeSaddles = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldCrankPos).inflate(4.0D),
                    entity -> entity.getItem().is(Items.SADDLE)
            ).size();

            assertSelectableWorker(helper, worldCrankPos, horse, player);
            HorseCrankInteractions.Outcome outcome = HorseCrankInteractions.attachAt(
                    level, worldCrankPos, level.getBlockState(worldCrankPos), player, attachmentStack);
            helper.assertTrue(outcome == HorseCrankInteractions.Outcome.SUCCESS,
                    "Creative interaction must still attach through the custom profile");
            helper.assertTrue(attachmentStack.getCount() == 1,
                    "Creative consuming profile must not shrink the attachment stack");

            crank.engine().detachWorker(true);

            int afterSaddles = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldCrankPos).inflate(4.0D),
                    entity -> entity.getItem().is(Items.SADDLE)
            ).size();
            helper.assertTrue(afterSaddles == beforeSaddles,
                    "Creative attach must not mint a duplicate attachment item on detach");
        } finally {
            AttachmentProfileRegistry.replace(previousProfiles);
        }
        helper.succeed();
    }


    @GameTest(template = "kinetic_network")
    public static void vanillaLeashAttachDoesNotStealPlayerLeashedBoat(GameTestHelper helper) {
        List<AttachmentProfile> previousProfiles = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile profile = new AttachmentProfile(
                    CHPApi.modId("gametest_vanilla_specific_worker"),
                    1000,
                    Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.SADDLE)),
                    Set.of(),
                    AttachmentMode.VANILLA_LEASH,
                    3.0f,
                    Set.of(),
                    Set.of(),
                    Set.of(CHPApi.modId("horse_crank")),
                    false,
                    true,
                    1.0f
            );
            AttachmentProfileRegistry.replace(List.of(profile));

            BlockPos localCrankPos = new BlockPos(4, 2, 4);
            helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
            BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
            ServerLevel level = helper.getLevel();
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));
            Boat boat = helper.spawn(EntityType.BOAT, new BlockPos(7, 2, 4));
            net.minecraft.world.entity.player.Player player =
                    helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            horse.setLeashedTo(player, false);
            boat.setLeashedTo(player, false);

            assertSelectableWorker(helper, worldCrankPos, horse, player);
            HorseCrankInteractions.Outcome outcome = HorseCrankInteractions.attachAt(
                    level, worldCrankPos, level.getBlockState(worldCrankPos), player, new ItemStack(Items.SADDLE));

            helper.assertTrue(outcome == HorseCrankInteractions.Outcome.SUCCESS,
                    "Valid worker attach must succeed with another player-leashed Leashable nearby");
            helper.assertTrue(CHPUtils.isLeashedToKnotAt(horse, worldCrankPos),
                    "Selected worker must move onto the crank knot");
            helper.assertTrue(boat.getLeashHolder() == player,
                    "Unrelated player-leashed boat must remain on the player, not be batch-bound to the crank");
        } finally {
            AttachmentProfileRegistry.replace(previousProfiles);
        }
        helper.succeed();
    }

    @GameTest(template = "kinetic_network")
    public static void foreignHarnessOwnerCannotBeStolenBySecondCrank(GameTestHelper helper) {
        List<AttachmentProfile> previousProfiles = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile profile = new AttachmentProfile(
                    CHPApi.modId("gametest_owned_harness"),
                    1000,
                    Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.SADDLE)),
                    Set.of(),
                    AttachmentMode.HARNESS,
                    3.0f,
                    Set.of(),
                    Set.of(),
                    Set.of(CHPApi.modId("horse_crank")),
                    true,
                    true,
                    1.0f
            );
            AttachmentProfileRegistry.replace(List.of(profile));

            BlockPos localA = new BlockPos(3, 2, 4);
            BlockPos localB = new BlockPos(9, 2, 4);
            helper.setBlock(localA, BlockRegister.HORSE_CRANK.get());
            helper.setBlock(localB, BlockRegister.HORSE_CRANK.get());
            AbstractHorseCrankBlockEntity crankA = requireCrank(helper, localA);
            AbstractHorseCrankBlockEntity crankB = requireCrank(helper, localB);
            BlockPos worldA = helper.absolutePos(localA);
            BlockPos worldB = helper.absolutePos(localB);
            ServerLevel level = helper.getLevel();
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));
            net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().instabuild = false;

            horse.setLeashedTo(player, false);
            ItemStack firstHarness = new ItemStack(Items.SADDLE);
            assertSelectableWorker(helper, worldA, horse, player);
            HorseCrankInteractions.Outcome first = HorseCrankInteractions.attachAt(
                    level, worldA, level.getBlockState(worldA), player, firstHarness);
            helper.assertTrue(first == HorseCrankInteractions.Outcome.SUCCESS && firstHarness.isEmpty(),
                    "Crank A must consume and own the first harness");
            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                            horse, worldA, crankA.engine().crankInstanceUuid()),
                    "Crank A attachment marker must own the worker");

            // Minecraft permits a non-vanilla-backend worker to be leashed to a player again.
            horse.setLeashedTo(player, false);
            ItemStack secondHarness = new ItemStack(Items.SADDLE);
            assertSelectableWorker(helper, worldB, horse, player);
            HorseCrankInteractions.Outcome second = HorseCrankInteractions.attachAt(
                    level, worldB, level.getBlockState(worldB), player, secondHarness);

            helper.assertTrue(second == HorseCrankInteractions.Outcome.SUCCESS,
                    "Foreign ownership rejection is a handled interaction");
            helper.assertTrue(secondHarness.getCount() == 1,
                    "Rejected crank B attach must not consume another harness");
            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                            horse, worldA, crankA.engine().crankInstanceUuid()),
                    "Crank B must not overwrite crank A attachment ownership");
            helper.assertTrue(crankA.engine().isAssignedWorker(horse.getUUID()),
                    "Crank A must retain its worker assignment");
            helper.assertTrue(!crankB.engine().isAssignedWorker(horse.getUUID()),
                    "Crank B must not acquire the already-owned worker");

            int beforeReturned = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldA).inflate(5.0D),
                    entity -> entity.getItem().is(Items.SADDLE)
            ).size();
            crankA.engine().detachWorker(true);
            int afterReturned = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldA).inflate(5.0D),
                    entity -> entity.getItem().is(Items.SADDLE)
            ).size();
            helper.assertTrue(afterReturned == beforeReturned + 1,
                    "Crank A must still return its originally consumed harness");
        } finally {
            AttachmentProfileRegistry.replace(previousProfiles);
        }
        helper.succeed();
    }

    @GameTest(template = "kinetic_network")
    public static void consumedAttachmentPreservesFullStackState(GameTestHelper helper) {
        List<AttachmentProfile> previousProfiles = AttachmentProfileRegistry.all();
        try {
            AttachmentProfile profile = new AttachmentProfile(
                    CHPApi.modId("gametest_stateful_harness"),
                    1000,
                    Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)),
                    Set.of(),
                    AttachmentMode.HARNESS,
                    3.0f,
                    Set.of(),
                    Set.of(),
                    Set.of(CHPApi.modId("horse_crank")),
                    true,
                    true,
                    1.0f
            );
            AttachmentProfileRegistry.replace(List.of(profile));

            BlockPos localCrankPos = new BlockPos(4, 2, 4);
            helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
            AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
            BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
            ServerLevel level = helper.getLevel();
            Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));
            net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            horse.setLeashedTo(player, false);

            ItemStack attachmentStack = new ItemStack(Items.DIAMOND_SWORD);
            attachmentStack.setDamageValue(37);

            assertSelectableWorker(helper, worldCrankPos, horse, player);
            HorseCrankInteractions.Outcome outcome = HorseCrankInteractions.attachAt(
                    level, worldCrankPos, level.getBlockState(worldCrankPos), player, attachmentStack);
            helper.assertTrue(outcome == HorseCrankInteractions.Outcome.SUCCESS && attachmentStack.isEmpty(),
                    "Stateful consuming attachment must be consumed by the real interaction");

            crank.engine().detachWorker(true);

            ItemEntity returned = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(worldCrankPos).inflate(4.0D),
                    entity -> entity.getItem().is(Items.DIAMOND_SWORD)
            ).stream().findFirst().orElseThrow(() ->
                    new AssertionError("Detach must return the consumed stateful attachment"));
            ItemStack restored = returned.getItem();
            helper.assertTrue(restored.getCount() == 1,
                    "Returned attachment must remain exactly one item");
            helper.assertTrue(restored.getDamageValue() == 37,
                    "Returned attachment must preserve damage/component state");
        } finally {
            AttachmentProfileRegistry.replace(previousProfiles);
        }
        helper.succeed();
    }


    @GameTest(template = "empty")
    public static void customAttachmentProfilePersistsBackendRadiusAndOutput(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(4, 2, 4);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));

        WorkerStats fixtureStats = new WorkerStats(
                16.0f, 32.0f, 2.5f,
                0.0f, WorkerStats.DEFAULT_SPEED_REF,
                0.0f, WorkerStats.DEFAULT_HEALTH_REF,
                false, false
        );
        WorkerResolver.ResolvedWorker fixture = new WorkerResolver.ResolvedWorker(
                fixtureStats, 16.0f, 32.0f, 0.0f, 0.0f, true,
                "gametest", "createhorsepower:horse_crank", false);
        AttachmentProfile harness = new AttachmentProfile(
                CHPApi.modId("gametest_harness"),
                100,
                Set.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.SADDLE)),
                Set.of(),
                AttachmentMode.HARNESS,
                1.75f,
                Set.of(),
                Set.of(),
                Set.of(CHPApi.modId("horse_crank")),
                false,
                true,
                0.5f
        );

        engine.attachWorker(horse, fixture, harness, new ItemStack(Items.SADDLE));

        helper.assertTrue(engine.attachmentMode() == AttachmentMode.HARNESS,
                "Custom profile must select the non-vanilla harness backend");
        helper.assertTrue(WorkerAttachmentControl.markerMode(horse) == AttachmentMode.HARNESS,
                "Worker marker must persist the selected attachment backend");
        assertNear(helper, 1.75f, engine.getWorkerRadius(),
                "Attachment profile must cap worker radius");
        assertNear(helper, 8.0f, engine.getEffectiveBaseRpm(),
                "Attachment output multiplier must scale RPM");
        assertNear(helper, 16.0f, engine.getEffectiveBaseStress(),
                "Attachment output multiplier must scale stress capacity");

        CompoundTag saved = new CompoundTag();
        engine.write(saved, false);
        helper.assertTrue("harness".equals(saved.getString("AttachmentMode")),
                "Save data must preserve the attachment backend");
        helper.assertTrue(CHPApi.modId("gametest_harness").toString().equals(saved.getString("AttachmentProfile")),
                "Save data must preserve the selected attachment profile id");

        engine.detachWorker(false);
        engine.read(saved, false);
        helper.assertTrue(engine.attachmentMode() == AttachmentMode.HARNESS,
                "Reload must restore the selected attachment backend");
        helper.assertTrue(CHPApi.modId("gametest_harness").toString().equals(engine.attachmentProfileId()),
                "Reload must restore the selected attachment profile id");

        helper.succeed();
    }

    /**
     * Real interactions query the world's entity index. Keep these fixtures in
     * the full-sized template: the one-block empty template does not wait for
     * the chunks containing workers at (6, 2, 4) or (12, 2, 4) to become visible.
     */
    private static void assertSelectableWorker(
            GameTestHelper helper,
            BlockPos crankPos,
            Mob worker,
            net.minecraft.world.entity.player.Player player
    ) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.isPositionEntityTicking(worker.blockPosition()),
                "Fixture worker chunk must be entity-ticking before the interaction");
        List<Mob> selectable = level.getEntitiesOfClass(
                Mob.class, new AABB(crankPos).inflate(7.0D),
                mob -> mob.isLeashed() && mob.getLeashHolder() == player);
        helper.assertTrue(selectable.size() == 1 && selectable.get(0) == worker,
                "Fixture worker must be the sole player-leashed mob visible to the real interaction");
    }

    private static void assertNear(GameTestHelper helper, float expected, float actual, String message) {
        helper.assertTrue(Math.abs(expected - actual) < 1.0e-4f,
                message + " (expected " + expected + ", got " + actual + ")");
    }

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (!(be instanceof AbstractHorseCrankBlockEntity crank)) {
            throw new AssertionError(
                    "BlockEntity at " + pos + " must be a " + TileEntityRegister.HORSE_CRANK.getId()
                            + " but was " + (be == null ? "null" : be.getType().toString()));
        }
        return crank;
    }

    private static void prepareOrbitMovementFloor(GameTestHelper helper) {
        // The empty structure has no floor. Safe orbit movement now correctly
        // refuses floating movement, so give movement-only tests a real floor.
        // Keep the 1-block-radius fixture bounded to this test's local area.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static void assertWalkableRing(GameTestHelper helper, BlockPos center, BlockPos[] offsets) {
        var level = helper.getLevel();
        for (BlockPos offset : offsets) {
            BlockPos floor = helper.absolutePos(center.offset(offset));
            var state = level.getBlockState(floor);
            helper.assertTrue(net.steampn.createhorsepower.content.path.PathEvaluator.isWalkableTile(
                    level, floor),
                    "unsafe ring floor " + floor + " offset=" + offset
                            + " state=" + state
                            + " shape=" + state.getCollisionShape(level, floor).toAabbs()
                            + " overhead=" + level.getBlockState(floor.above())
                            + " overhead2=" + level.getBlockState(floor.above(2))
                            + " chunk=" + level.hasChunkAt(floor));
        }
    }

    private static double assertOrbitStep(
            GameTestHelper helper,
            Mob worker,
            double centerX,
            double centerZ,
            double radius,
            double currentAngle
    ) {
        double oldX = worker.getX();
        double oldZ = worker.getZ();
        double newAngle = WorkerOrbitMovement.normalizeAngle(currentAngle + Math.toRadians(15.0D));
        WorkerOrbitMovement.moveToAngle(worker, centerX, centerZ, (float) radius, currentAngle, newAngle);
        double deltaX = worker.getX() - oldX;
        double deltaZ = worker.getZ() - oldZ;
        double radialDistance = Math.hypot(worker.getX() - centerX, worker.getZ() - centerZ);
        float expectedYaw = WorkerOrbitMovement.yawFromMovement(deltaX, deltaZ);
        float yawError = Math.abs(Mth.wrapDegrees(worker.getYRot() - expectedYaw));

        helper.assertTrue(deltaX * deltaX + deltaZ * deltaZ > 1.0e-8D, "Worker must move along its orbit");
        helper.assertTrue(Math.abs(radialDistance - radius) < 0.001D, "Worker must remain on its configured radius");
        helper.assertTrue(yawError < 0.1F, "Worker yaw must face its actual movement vector");
        return newAngle;
    }
}
