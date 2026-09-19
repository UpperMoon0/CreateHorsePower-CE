package net.steampn.createhorsepower.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.CrankProperties;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.HorseCrankInteractions;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.platform.DeferredDetachStore;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.utils.CHPUtils;

import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class HorsePowerLifecycleGameTests {
    private HorsePowerLifecycleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void durableNoDropDetachSurvivesCrankReplacement(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();
        ServerLevel level = helper.getLevel();

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        horse.setLeashedTo(knot, true);
        engine.setWorkerUuidForTesting(horse.getUUID());
        engine.setCachedWorkerMobForTesting(horse);
        WorkerAttachmentControl.markAttached(horse, crank.getBlockPos(), engine.crankInstanceUuid());
        engine.controlWorkerAiForTesting(horse);

        CompoundTag savedWorker = new CompoundTag();
        horse.saveWithoutId(savedWorker);
        UUID workerUuid = horse.getUUID();
        UUID oldCrankUuid = engine.crankInstanceUuid();

        horse.discard();
        engine.setCachedWorkerMobForTesting(null);
        engine.detachWorker(false);

        var durable = CHPApi.deferredDetaches().get(level, workerUuid);
        helper.assertTrue(durable != null && !durable.dropLead(),
                "detach(false) must persist a no-drop policy at level scope while worker is unloaded");
        helper.assertTrue(durable != null && durable.matches(crank.getBlockPos(), oldCrankUuid),
                "durable detach record must retain crank position and instance UUID");

        level.destroyBlock(crank.getBlockPos(), false);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity replacement = requireCrank(helper, localCrankPos);
        helper.assertFalse(oldCrankUuid.equals(replacement.engine().crankInstanceUuid()),
                "replacement crank must not inherit the old instance UUID");

        Horse reloaded = EntityType.HORSE.create(level);
        helper.assertTrue(reloaded != null, "horse must be creatable for reload regression");
        reloaded.load(savedWorker);
        level.addFreshEntity(reloaded);
        WorkerRecoveryQueue.enqueue(reloaded, level);

        helper.runAfterDelay(3, () -> {
            helper.assertFalse(WorkerAttachmentControl.hasMarker(reloaded),
                    "durable detach recovery must clear the attachment marker");
            helper.assertFalse(WorkerActivityControl.hasMarker(reloaded),
                    "durable detach recovery must clear the CHP activity marker");
            helper.assertFalse(reloaded.isNoAi(),
                    "durable detach recovery must restore CHP-owned NoAI suppression");
            helper.assertTrue(CHPApi.deferredDetaches().get(level, workerUuid) == null,
                    "durable detach policy must be consumed after worker recovery");
            long droppedLeads = level.getEntitiesOfClass(ItemEntity.class,
                            new AABB(reloaded.blockPosition()).inflate(6.0D),
                            item -> item.getItem().is(Items.LEAD))
                    .size();
            helper.assertTrue(droppedLeads == 0,
                    "detach(false) must remain no-drop after old crank destruction/replacement");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recoveryTimeoutRestoresOwnedNoAiWithoutLoadingOldCrank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        BlockPos oldCrankPos = horse.blockPosition().offset(1024, 0, 1024);
        UUID oldCrankUuid = UUID.randomUUID();

        helper.assertFalse(level.hasChunkAt(oldCrankPos), "old crank chunk must begin unloaded");
        boolean changedNoAi = WorkerActivityControl.acquire(horse, oldCrankPos, oldCrankUuid);
        helper.assertTrue(changedNoAi && horse.isNoAi(),
                "fixture must create CHP-owned NoAI false->true state");
        WorkerRecoveryQueue.enqueue(horse, level);

        helper.runAfterDelay(2, () -> {
            WorkerRecoveryQueue.expireForTesting(horse.getUUID());
            WorkerRecoveryQueue.process(level);

            helper.assertFalse(level.hasChunkAt(oldCrankPos),
                    "bounded recovery must not force-load the old crank chunk");
            helper.assertFalse(WorkerActivityControl.hasMarker(horse),
                    "timed-out recovery must clear the CHP activity marker");
            helper.assertFalse(horse.isNoAi(),
                    "timed-out recovery must restore CHP-owned NoAI false->true state");
            helper.assertFalse(WorkerRecoveryQueue.isPendingForTesting(horse.getUUID()),
                    "timed-out recovery must terminate instead of remaining pending forever");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recoveryTimeoutAgeSurvivesWorkerReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        BlockPos oldCrankPos = horse.blockPosition().offset(1024, 0, 1024);
        UUID oldCrankUuid = UUID.randomUUID();

        helper.assertFalse(level.hasChunkAt(oldCrankPos), "old crank chunk must begin unloaded");
        helper.assertTrue(WorkerActivityControl.acquire(horse, oldCrankPos, oldCrankUuid),
                "fixture must create CHP-owned NoAI state");
        WorkerRecoveryQueue.enqueue(horse, level);
        WorkerRecoveryQueue.advanceRecoveryAgeForTesting(horse, 600L);

        CompoundTag savedWorker = new CompoundTag();
        horse.saveWithoutId(savedWorker);
        UUID workerUuid = horse.getUUID();
        horse.discard();

        Horse reloaded = EntityType.HORSE.create(level);
        helper.assertTrue(reloaded != null, "horse must be creatable for recovery reload regression");
        reloaded.load(savedWorker);
        level.addFreshEntity(reloaded);
        WorkerRecoveryQueue.enqueue(reloaded, level);
        helper.assertTrue(WorkerRecoveryQueue.recoveryAgeForTesting(reloaded, level.getGameTime()) >= 600L,
                "recovery age must survive worker unload/reload instead of resetting to zero");
        WorkerRecoveryQueue.advanceRecoveryAgeForTesting(reloaded, 600L);

        helper.runAfterDelay(2, () -> {
            WorkerRecoveryQueue.process(level);
            helper.assertFalse(level.hasChunkAt(oldCrankPos),
                    "reload-spanning timeout must not force-load the old crank chunk");
            helper.assertFalse(WorkerActivityControl.hasMarker(reloaded),
                    "600 + reload + 600 ticks must expire the CHP activity marker");
            helper.assertFalse(reloaded.isNoAi(),
                    "reload-spanning timeout must restore CHP-owned NoAI");
            helper.assertFalse(WorkerRecoveryQueue.isPendingForTesting(workerUuid),
                    "reload-spanning timeout must terminate recovery");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleLoadedAssignmentCanBeRepairedWithoutStealingForeignLeash(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));

        crank.engine().setWorkerUuidForTesting(horse.getUUID());
        level.setBlock(crank.getBlockPos(), crank.getBlockState().setValue(CrankProperties.HAS_WORKER, true), 3);

        BlockPos foreignKnotPos = crank.getBlockPos().offset(4, 0, 0);
        LeashFenceKnotEntity foreignKnot = LeashFenceKnotEntity.getOrCreateKnot(level, foreignKnotPos);
        horse.setLeashedTo(foreignKnot, true);

        boolean repaired = HorseCrankInteractions.repairStaleAssignmentBeforeAttach(
                level, crank.getBlockPos(), level.getBlockState(crank.getBlockPos()));
        helper.assertTrue(repaired,
                "loaded worker re-leashed away from the crank must be treated as a stale assignment");
        helper.assertFalse(level.getBlockState(crank.getBlockPos()).getValue(CrankProperties.HAS_WORKER),
                "stale assignment repair must clear the ghost HAS_WORKER state immediately");
        helper.assertTrue(horse.getLeashHolder() == foreignKnot && foreignKnot.isAlive(),
                "stale crank cleanup must preserve the worker's new foreign leash");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newAttachmentSupersedesDurableDetachIntent(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        UUID workerUuid = horse.getUUID();
        UUID crankUuid = crank.engine().crankInstanceUuid();

        CHPApi.deferredDetaches().put(level, workerUuid,
                new DeferredDetachStore.Entry(crank.getBlockPos(), crankUuid, false));
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crankUuid);
        WorkerRecoveryQueue.enqueue(horse, level);
        WorkerAttachmentControl.markAttached(horse, crank.getBlockPos(), crankUuid);

        helper.assertTrue(CHPApi.deferredDetaches().get(level, workerUuid) == null,
                "successful new attachment must consume obsolete durable detach intent");
        helper.assertFalse(WorkerRecoveryQueue.isPendingForTesting(workerUuid),
                "successful new attachment must cancel obsolete queued recovery");
        helper.assertTrue(WorkerAttachmentControl.hasMarker(horse)
                        && crankUuid.equals(WorkerAttachmentControl.markerCrankUuid(horse)),
                "successful new attachment must write fresh current ownership");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recoveredWorkerKeepsKnotUsedByAnotherLoadedMob(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        ServerLevel level = helper.getLevel();
        UUID crankUuid = crank.engine().crankInstanceUuid();

        Horse recovering = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        Horse other = helper.spawn(EntityType.HORSE, new BlockPos(3, 1, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        recovering.setLeashedTo(knot, true);
        other.setLeashedTo(knot, true);
        WorkerAttachmentControl.markAttached(recovering, crank.getBlockPos(), crankUuid);
        CHPApi.deferredDetaches().put(level, recovering.getUUID(),
                new DeferredDetachStore.Entry(crank.getBlockPos(), crankUuid, false));

        WorkerAttachmentControl.RecoveryResult result = WorkerAttachmentControl.recoverIfOrphaned(recovering, level);
        helper.assertTrue(result == WorkerAttachmentControl.RecoveryResult.RECOVERED,
                "matching durable detach must recover the stale worker immediately");
        helper.assertFalse(recovering.isLeashed(),
                "recovered worker must release its persisted crank leash");
        helper.assertTrue(other.getLeashHolder() == knot,
                "recovery must preserve another loaded mob using the same exact knot");
        helper.assertTrue(knot.isAlive(),
                "shared knot must stay alive while another loaded mob still uses it");

        other.dropLeash(true, false);
        knot.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void foreignCrankCleanupDoesNotConsumeDurableDetachIntent(GameTestHelper helper) {
        BlockPos localCrankA = new BlockPos(0, 1, 0);
        BlockPos localCrankB = new BlockPos(4, 1, 0);
        helper.setBlock(localCrankA, BlockRegister.HORSE_CRANK.get());
        helper.setBlock(localCrankB, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crankA = requireCrank(helper, localCrankA);
        AbstractHorseCrankBlockEntity crankB = requireCrank(helper, localCrankB);
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));

        DeferredDetachStore.Entry ownedByA = new DeferredDetachStore.Entry(
                crankA.getBlockPos(), crankA.engine().crankInstanceUuid(), false);
        CHPApi.deferredDetaches().put(level, horse.getUUID(), ownedByA);

        CHPUtils.cleanUpLeash(level, crankB.getBlockPos(), horse.getUUID(), true);

        DeferredDetachStore.Entry remaining = CHPApi.deferredDetaches().get(level, horse.getUUID());
        helper.assertTrue(remaining != null
                        && remaining.matches(crankA.getBlockPos(), crankA.engine().crankInstanceUuid())
                        && !remaining.dropLead(),
                "cleanup from crank B must not erase crank A's durable detach ownership");

        CHPApi.deferredDetaches().remove(level, horse.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleCrankDetachPreservesNewCrankActivityOwnership(GameTestHelper helper) {
        BlockPos localCrankA = new BlockPos(0, 1, 0);
        BlockPos localCrankB = new BlockPos(4, 1, 0);
        helper.setBlock(localCrankA, BlockRegister.HORSE_CRANK.get());
        helper.setBlock(localCrankB, BlockRegister.HORSE_CRANK.get());
        HorseCrankEngine engineA = requireCrank(helper, localCrankA).engine();
        HorseCrankEngine engineB = requireCrank(helper, localCrankB).engine();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));

        engineA.setWorkerUuidForTesting(horse.getUUID());
        engineA.setCachedWorkerMobForTesting(horse);
        engineA.controlWorkerAiForTesting(horse);
        helper.assertTrue(engineA.crankInstanceUuid().equals(WorkerActivityControl.markerCrankUuid(horse)),
                "fixture crank A must initially own the activity marker");
        helper.assertTrue(horse.isNoAi(), "fixture crank A must suppress worker AI");

        WorkerActivityControl.releaseFromMarker(horse);
        engineB.setWorkerUuidForTesting(horse.getUUID());
        engineB.setCachedWorkerMobForTesting(horse);
        engineB.controlWorkerAiForTesting(horse);
        UUID newOwner = engineB.crankInstanceUuid();
        helper.assertTrue(newOwner.equals(WorkerActivityControl.markerCrankUuid(horse)),
                "fixture crank B must replace crank A as current activity owner");
        helper.assertTrue(horse.isNoAi(), "new crank must own the active NoAI transition");

        engineA.detachWorker(false);

        helper.assertTrue(newOwner.equals(WorkerActivityControl.markerCrankUuid(horse)),
                "stale crank A cleanup must not clear crank B's newer activity marker");
        helper.assertTrue(horse.isNoAi(),
                "stale crank A cleanup must not restore NoAI while crank B still owns the transition");
        helper.assertFalse(engineA.ownsWorkerActivityMarkerForTesting(),
                "stale crank A must relinquish only its local marker bookkeeping");
        helper.assertTrue(engineB.ownsWorkerActivityMarkerForTesting(),
                "current crank B must retain its local ownership bookkeeping");

        engineB.detachWorker(false);
        helper.assertFalse(WorkerActivityControl.hasMarker(horse),
                "current crank B cleanup must still release its own marker normally");
        helper.assertFalse(horse.isNoAi(),
                "current crank B cleanup must restore the worker's original NoAI=false state");
        helper.succeed();
    }



    /**
     * Regression for the field failure where attachWorker() updates HAS_WORKER
     * through Level#setBlock(), but BlockEntity#getBlockState() can still expose
     * the old false value during the next tick. Before the fix, reconcileWorker()
     * trusted that stale BE cache and immediately cleared workerUuid even though
     * the world state, leash, and persistent ownership marker were all valid.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void attachmentSurvivesStaleBlockEntityWorkerState(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(2, 2, 2);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 2, 2));

        helper.runAfterDelay(5, () -> {
            AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
            HorseCrankEngine engine = crank.engine();

            for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(HorseCrankEngine.DEFAULT_RADIUS)) {
                level.setBlock(crank.getBlockPos().offset(offset), Blocks.GRAVEL.defaultBlockState(), 3);
            }

            LeashFenceKnotEntity knot =
                    LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
            horse.setLeashedTo(knot, false);
            engine.attachWorker(horse, WorkerResolver.resolve(horse));

            var worldState = level.getBlockState(crank.getBlockPos());
            helper.assertTrue(worldState.getValue(CrankProperties.HAS_WORKER),
                    "attach must set authoritative world HAS_WORKER=true");
            helper.assertTrue(engine.isAssignedWorker(horse.getUUID()),
                    "attach must persist the worker UUID before the next tick");
            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                            horse, crank.getBlockPos(), engine.crankInstanceUuid()),
                    "attach must persist exact crank ownership on the worker");

            helper.runAfterDelay(3, () -> {
                helper.assertTrue(engine.isAssignedWorker(horse.getUUID()),
                        "next ticks must not erase the worker UUID from a valid attachment");
                helper.assertTrue(engine.isWorkerResolved(),
                        "valid attached worker must remain resolved after block-state reconciliation");
                helper.assertTrue(engine.isWorkerEligible(),
                        "valid horse must remain eligible after reconciliation");
                helper.assertTrue(engine.hasValidWorkingBlocks,
                        "absolute gravel fixture must provide a valid worker path");
                helper.assertTrue(engine.isWorking(),
                        "crank must continue working after attachment reconciliation");
                helper.assertTrue(Math.abs(engine.generatedSpeed()) > 0.0F,
                        "working crank must continue contributing rotation");
                helper.assertTrue(horse.getLeashHolder() == knot && knot.isAlive(),
                        "the original live crank leash must remain intact");

                engine.detachWorker(false);
                helper.succeed();
            });
        });
    }

    /**
     * End-to-end regression for the reported shared-network failure.
     *
     * Three normal horses first drive one real Create kinetic graph containing a
     * 36-press stress bank at a sustainable 5 RPM. A fourth horse is then attached
     * on its own vertical shaft branch with movement speed pinned to the configured
     * maximum scaling clamp; that faster source raises the shared graph to ~10.625
     * RPM and makes the same load genuinely overstressed. Severing only the fast
     * crank's shaft branch must let Create tear down/rebuild the graph around the
     * three surviving sources, return the stress/capacity relation to sustainable,
     * and resume actual network rotation without losing any surviving worker.
     */
    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void sharedOverstressedNetworkRecoversAfterFastCrankBranchLoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int[] crankZ = {4, 9, 14, 19};

        BlockPos[] crankPositions = new BlockPos[4];
        BlockPos[] branchShaftPositions = new BlockPos[4];
        AbstractHorseCrankBlockEntity[] cranks = new AbstractHorseCrankBlockEntity[4];
        FixtureWorker[] workers = new FixtureWorker[4];

        var shaftX = AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
        var shaftY = AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        var shaftZ = AllBlocks.SHAFT.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z);
        // AXIS=Z leaves X/Y shaft faces open: vertical crank input -> east bus.
        var branchGearbox = AllBlocks.GEARBOX.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z);
        // AXIS=Y leaves X/Z faces open: shared north/south bus with east stress-bank branch.
        var busGearbox = AllBlocks.GEARBOX.getDefaultState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        var pressX = AllBlocks.MECHANICAL_PRESS.getDefaultState()
                .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST);

        // Four identical branches all enter the west side of the shared bus, so
        // gearbox sign conventions are the same for every crank.
        for (int i = 0; i < crankZ.length; i++) {
            int z = crankZ[i];
            BlockPos crankPos = origin.offset(4, 4, z);
            BlockPos branchShaft = origin.offset(4, 3, z);
            crankPositions[i] = crankPos;
            branchShaftPositions[i] = branchShaft;

            level.setBlock(crankPos, BlockRegister.HORSE_CRANK.get().defaultBlockState(), 3);
            level.setBlock(branchShaft, shaftY, 3);
            level.setBlock(origin.offset(6, 2, z), branchGearbox, 3);
            level.setBlock(origin.offset(7, 2, z), shaftX, 3);
            level.setBlock(origin.offset(6, 2, z), shaftX, 3);
            level.setBlock(origin.offset(7, 2, z), busGearbox, 3);
        }

        // Continuous Z-axis bus between the four branch gearboxes.
        for (int z = crankZ[0] + 1; z < crankZ[crankZ.length - 1]; z++) {
            boolean branchJunction = false;
            for (int junctionZ : crankZ) {
                if (z == junctionZ) {
                    branchJunction = true;
                    break;
                }
            }
            if (!branchJunction) {
                level.setBlock(origin.offset(7, 2, z), shaftZ, 3);
            }
        }

        // Six rows x six presses = 36 real Create stress consumers. The rows
        // snake through Y-axis gearboxes so every press is in the same graph.
        level.setBlock(origin.offset(8, 2, crankZ[1]), shaftX, 3);
        for (int row = 0; row < 6; row++) {
            int z = crankZ[1] + row;
            for (int x = 9; x <= 14; x++) {
                level.setBlock(origin.offset(x, 2, z), pressX, 3);
            }
            if (row < 5) {
                int turnX = (row & 1) == 0 ? 15 : 8;
                level.setBlock(origin.offset(turnX, 2, z), busGearbox, 3);
                level.setBlock(origin.offset(turnX, 2, z + 1), busGearbox, 3);
            }
        }

        // Give every worker a complete valid gravel orbit without touching the
        // kinetic graph, which lives one block lower at Y=2.
        for (BlockPos crankPos : crankPositions) {
            for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(HorseCrankEngine.DEFAULT_RADIUS)) {
                level.setBlock(crankPos.offset(offset), Blocks.GRAVEL.defaultBlockState(), 3);
            }
        }

        helper.runAfterDelay(8, () -> {
            for (int i = 0; i < cranks.length; i++) {
                BlockEntity be = level.getBlockEntity(crankPositions[i]);
                helper.assertTrue(be instanceof AbstractHorseCrankBlockEntity,
                        "fixture crank " + i + " must have a horse-crank block entity");
                cranks[i] = (AbstractHorseCrankBlockEntity) be;
            }

            // Bring the normal sources online one at a time. The first source
            // establishes the shared network's local rotation signs; each later
            // crank then inherits its already-moving local theoretical speed in
            // attachWorker(), exactly as separately attached gameplay cranks do.
            // Starting all three in the same tick would make every fresh engine
            // choose +1 before the gearbox graph has a direction and can create
            // an artificial source conflict unrelated to branch-loss recovery.
            workers[1] = attachFixtureHorse(helper, level, cranks[1], 0.225D);

            helper.runAfterDelay(8, () -> {
                workers[2] = attachFixtureHorse(helper, level, cranks[2], 0.225D);

                helper.runAfterDelay(8, () -> {
                    workers[3] = attachFixtureHorse(helper, level, cranks[3], 0.225D);

                    helper.runAfterDelay(12, () -> {
                KineticBlockEntity stressProbe =
                        requireKinetic(helper, level, origin.offset(9, 2, crankZ[1]), "stress-bank press");
                helper.assertFalse(stressProbe.isOverStressed(),
                        "three normal cranks must start with a sustainable shared network");

                var initialNetwork = stressProbe.getOrCreateNetwork();
                StringBuilder initialDiag = new StringBuilder()
                        .append("probe{speed=").append(stressProbe.getSpeed())
                        .append(", theoretical=").append(stressProbe.getTheoreticalSpeed())
                        .append(", network=").append(stressProbe.network).append('}');
                for (int i = 1; i < cranks.length; i++) {
                    HorseCrankEngine engine = cranks[i].engine();
                    initialDiag.append(" crank").append(i).append("{working=").append(engine.isWorking())
                            .append(", resolved=").append(engine.isWorkerResolved())
                            .append(", eligible=").append(engine.isWorkerEligible())
                            .append(", path=").append(engine.hasValidWorkingBlocks)
                            .append(", invalidPath=").append(engine.getInvalidBlockCount())
                            .append(", assigned=").append(workers[i] != null && engine.isAssignedWorker(workers[i].horse().getUUID()))
                            .append(", generated=").append(engine.generatedSpeed())
                            .append(", speed=").append(cranks[i].getSpeed())
                            .append(", theoretical=").append(cranks[i].getTheoreticalSpeed())
                            .append(", network=").append(cranks[i].network).append('}');
                }
                helper.assertTrue(initialNetwork != null,
                        "stress-bank probe must join the shared Create network; " + initialDiag);
                float initialStress = initialNetwork.calculateStress();
                float initialCapacity = initialNetwork.calculateCapacity();
                helper.assertTrue(Math.abs(stressProbe.getSpeed()) > 0.0F,
                        "sustainable shared network must have real non-zero rotation; " + initialDiag
                                + " stress=" + initialStress + " capacity=" + initialCapacity);

                helper.assertTrue(initialStress > 0.0F && initialStress < initialCapacity,
                        "fixture must begin below capacity, got stress=" + initialStress
                                + " capacity=" + initialCapacity);

                for (int i = 1; i < cranks.length; i++) {
                    HorseCrankEngine engine = cranks[i].engine();
                    helper.assertTrue(engine.isWorking() && engine.isAssignedWorker(workers[i].horse().getUUID()),
                            "normal crank " + i + " must be actively generating before fast source joins");
                    helper.assertTrue(cranks[i].network != null && cranks[i].network.equals(stressProbe.network),
                            "normal crank " + i + " must belong to the shared Create network");
                }

                // The fourth source is materially faster because the horse is
                // pinned at 2.5x the profile speed reference (the default max clamp).
                workers[0] = attachFixtureHorse(helper, level, cranks[0], 0.5625D);

                helper.runAfterDelay(12, () -> {
                    KineticBlockEntity overloadedProbe =
                            requireKinetic(helper, level, origin.offset(9, 2, crankZ[1]), "overloaded stress-bank press");
                    HorseCrankEngine fastEngine = cranks[0].engine();
                    helper.assertTrue(fastEngine.isWorking() && fastEngine.isAssignedWorker(workers[0].horse().getUUID()),
                            "fast crank must remain actively attached while driving the overload transition");
                    helper.assertTrue(Math.abs(fastEngine.generatedSpeed())
                                    > Math.abs(cranks[1].engine().generatedSpeed()) * 1.5F,
                            "fixture fast horse must materially outrun the normal horses");

                    var overloadedNetwork = overloadedProbe.getOrCreateNetwork();
                    float overloadedStress = overloadedNetwork.calculateStress();
                    float overloadedCapacity = overloadedNetwork.calculateCapacity();
                    helper.assertTrue(overloadedStress > overloadedCapacity,
                            "fast source must genuinely overstress the shared Create network, got stress="
                                    + overloadedStress + " capacity=" + overloadedCapacity);
                    helper.assertTrue(overloadedProbe.isOverStressed(),
                            "Create must mark the shared graph overstressed before branch loss");
                    helper.assertTrue(Math.abs(overloadedProbe.getTheoreticalSpeed()) > 0.0F
                                    && Math.abs(overloadedProbe.getSpeed()) == 0.0F,
                            "overstressed graph must retain theoretical rotation while actual rotation is stopped");

                    for (int i = 0; i < cranks.length; i++) {
                        helper.assertTrue(cranks[i].network != null && cranks[i].network.equals(overloadedProbe.network),
                                "all four crank sources must be members of one shared network before branch loss");
                    }

                    // Reproduce the field event: the crank remains present with
                    // its horse attached; only its connecting shaft branch is lost.
                    level.destroyBlock(branchShaftPositions[0], false);
                    helper.assertTrue(level.getBlockState(branchShaftPositions[0]).isAir(),
                            "fast crank branch shaft must actually be severed");

                    helper.runAfterDelay(20, () -> {
                        KineticBlockEntity recoveredProbe =
                                requireKinetic(helper, level, origin.offset(9, 2, crankZ[1]), "recovered stress-bank press");
                        var recoveredNetwork = recoveredProbe.getOrCreateNetwork();
                        float recoveredStress = recoveredNetwork.calculateStress();
                        float recoveredCapacity = recoveredNetwork.calculateCapacity();

                        helper.assertFalse(recoveredProbe.isOverStressed(),
                                "surviving three-source network must recover from the overloaded state");
                        helper.assertTrue(recoveredStress > 0.0F && recoveredStress < recoveredCapacity,
                                "surviving network must be genuinely sustainable after rebuild, got stress="
                                        + recoveredStress + " capacity=" + recoveredCapacity);
                        helper.assertTrue(Math.abs(recoveredProbe.getSpeed()) > 0.0F,
                                "surviving kinetic network must resume actual non-zero rotation");

                        for (int i = 1; i < cranks.length; i++) {
                            HorseCrankEngine engine = cranks[i].engine();
                            FixtureWorker worker = workers[i];
                            helper.assertTrue(engine.isAssignedWorker(worker.horse().getUUID()),
                                    "surviving crank " + i + " must retain its worker UUID");
                            helper.assertTrue(engine.isWorkerResolved() && engine.isWorkerEligible(),
                                    "surviving crank " + i + " must keep its worker resolved and eligible");
                            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                                            worker.horse(), cranks[i].getBlockPos(), engine.crankInstanceUuid()),
                                    "surviving crank " + i + " must retain exact durable ownership");
                            helper.assertTrue(worker.horse().getLeashHolder() == worker.knot() && worker.knot().isAlive(),
                                    "surviving crank " + i + " must retain its live crank leash");
                            helper.assertTrue(engine.isWorking() && Math.abs(engine.generatedSpeed()) > 0.0F,
                                    "surviving crank " + i + " must continue generating after network rebuild");
                            helper.assertTrue(cranks[i].network != null && cranks[i].network.equals(recoveredProbe.network),
                                    "surviving crank " + i + " must rejoin the recovered shared network");
                            helper.assertTrue(Math.abs(cranks[i].getSpeed()) > 0.0F,
                                    "surviving crank " + i + " must have actual, not merely requested, rotation");
                        }

                        for (int i = 0; i < cranks.length; i++) {
                            if (workers[i] != null) {
                                cranks[i].engine().detachWorker(false);
                            }
                        }
                        helper.succeed();
                    });
                });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty")
    public static void tfcHorseAndTerrainHaveBuiltinCompatibility(GameTestHelper helper) {
        EntityType<?> tfcHorse = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id("tfc", "horse"));
        helper.assertTrue(tfcHorse != null && WorkerResolver.getBaseStats(tfcHorse).isPresent(),
                "TFC horse must resolve through the built-in optional worker profile");

        Block tfcGround = BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).toString().startsWith("tfc:grass/"))
                .findFirst()
                .orElse(null);
        helper.assertTrue(tfcGround != null, "TFC dev runtime must expose generated grass blocks");
        helper.assertTrue(PathEvaluator.getPathStats(tfcGround).isPresent(),
                "TFC grass must be valid crank footing without manual server config");
        helper.succeed();
    }


    private record FixtureWorker(Horse horse, LeashFenceKnotEntity knot) {}

    private static FixtureWorker attachFixtureHorse(
            GameTestHelper helper,
            ServerLevel level,
            AbstractHorseCrankBlockEntity crank,
            double movementSpeed
    ) {
        Horse horse = EntityType.HORSE.create(level);
        helper.assertTrue(horse != null, "fixture horse must be creatable");

        var speedAttribute = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        var healthAttribute = horse.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(speedAttribute != null && healthAttribute != null,
                "fixture horse must expose movement-speed and max-health attributes");
        speedAttribute.setBaseValue(movementSpeed);
        healthAttribute.setBaseValue(22.0D);
        horse.setHealth(horse.getMaxHealth());

        BlockPos crankPos = crank.getBlockPos();
        // Spawn on the actual gravel orbit, not inside the unsupported center.
        // The configured horse radius is 2.5 blocks, so centerX + radius lands
        // on the x+3 path tile generated by generateOffsetsForRadius().
        horse.moveTo(crankPos.getX() + 3.0D, crankPos.getY(), crankPos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(horse),
                "fixture horse must register in the server level");

        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, crankPos);
        horse.setLeashedTo(knot, false);
        var resolved = WorkerResolver.resolve(horse);
        helper.assertTrue(resolved.isValid(), "fixture horse must resolve as a valid worker");
        crank.engine().attachWorker(horse, resolved);
        return new FixtureWorker(horse, knot);
    }

    private static KineticBlockEntity requireKinetic(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos worldPos,
            String description
    ) {
        BlockEntity be = level.getBlockEntity(worldPos);
        helper.assertTrue(be instanceof KineticBlockEntity,
                description + " must have a Create kinetic block entity at " + worldPos);
        return (KineticBlockEntity) be;
    }

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos localPos) {
        BlockEntity be = helper.getBlockEntity(localPos);
        helper.assertTrue(be instanceof AbstractHorseCrankBlockEntity,
                "expected a horse crank block entity at " + localPos);
        return (AbstractHorseCrankBlockEntity) be;
    }
}
