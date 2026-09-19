package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
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


    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ownedWorkerLeashRepairKeepsCrankRunning(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(4, 2, 4);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(HorseCrankEngine.DEFAULT_RADIUS)) {
            helper.setBlock(localCrankPos.offset(offset), Blocks.GRAVEL);
        }

        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(6, 2, 4));

        LeashFenceKnotEntity originalKnot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        horse.setLeashedTo(originalKnot, false);
        engine.attachWorker(horse, WorkerResolver.resolve(horse));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(engine.isWorkerResolved(), "fixture worker must resolve before the network rebuild");
            helper.assertTrue(engine.isWorking(), "fixture crank must be working before the network rebuild");
            helper.assertTrue(Math.abs(engine.generatedSpeed()) > 0.0F,
                    "fixture crank must contribute rotation before the network rebuild");

            // Model the transient vanilla leash representation loss while
            // Create rebuilds a kinetic network after a failed branch.
            horse.dropLeash(true, false);
            if (originalKnot.isAlive()) {
                originalKnot.discard();
            }
            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                            horse, crank.getBlockPos(), engine.crankInstanceUuid()),
                    "durable worker ownership must survive loss of the vanilla leash representation");

            helper.runAfterDelay(2, () -> {
                helper.assertTrue(engine.isWorkerResolved(),
                        "owned worker must remain resolved after the transient knot loss");
                helper.assertTrue(engine.isWorking(),
                        "crank must continue working after repairing its owned worker leash");
                helper.assertTrue(Math.abs(engine.generatedSpeed()) > 0.0F,
                        "crank must continue contributing rotation after the repair");
                helper.assertTrue(CHPUtils.isLeashedToKnotAt(horse, crank.getBlockPos()),
                        "missing vanilla knot must be recreated for the exact owned worker");
                engine.detachWorker(false);
                helper.succeed();
            });
        });
    }


    @GameTest(template = "empty", timeoutTicks = 80)
    public static void fourCrankBranchLossKeepsThreeOwnedWorkersAttached(GameTestHelper helper) {
        BlockPos[] positions = {
                new BlockPos(1, 1, 1),
                new BlockPos(4, 1, 1),
                new BlockPos(1, 1, 4),
                new BlockPos(4, 1, 4)
        };
        AbstractHorseCrankBlockEntity[] cranks = new AbstractHorseCrankBlockEntity[4];
        Horse[] horses = new Horse[4];
        LeashFenceKnotEntity[] knots = new LeashFenceKnotEntity[4];
        ServerLevel level = helper.getLevel();

        for (int i = 0; i < positions.length; i++) {
            helper.setBlock(positions[i], BlockRegister.HORSE_CRANK.get());
            cranks[i] = requireCrank(helper, positions[i]);
            horses[i] = helper.spawn(EntityType.HORSE, positions[i].offset(0, 0, 1));
            knots[i] = LeashFenceKnotEntity.getOrCreateKnot(level, cranks[i].getBlockPos());
            horses[i].setLeashedTo(knots[i], false);
            cranks[i].engine().attachWorker(horses[i], WorkerResolver.resolve(horses[i]));
            helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                            horses[i], cranks[i].getBlockPos(), cranks[i].engine().crankInstanceUuid()),
                    "each crank must begin with independent durable worker ownership");
        }

        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < cranks.length; i++) {
                helper.assertTrue(cranks[i].engine().isWorkerResolved(),
                        "all four workers must resolve before the branch-loss transition");
            }

            level.destroyBlock(cranks[0].getBlockPos(), false);

            for (int i = 1; i < cranks.length; i++) {
                horses[i].dropLeash(true, false);
                if (knots[i].isAlive()) {
                    knots[i].discard();
                }
            }

            helper.runAfterDelay(HorseCrankEngine.MISSING_ATTACHMENT_GRACE_TICKS + 2, () -> {
                for (int i = 1; i < cranks.length; i++) {
                    helper.assertTrue(cranks[i].engine().isWorkerResolved(),
                            "surviving crank " + i + " must keep resolving its worker after branch loss");
                    helper.assertTrue(WorkerAttachmentControl.isOwnedBy(
                                    horses[i], cranks[i].getBlockPos(), cranks[i].engine().crankInstanceUuid()),
                            "surviving crank " + i + " must keep its durable ownership marker");
                    helper.assertTrue(CHPUtils.isLeashedToKnotAt(horses[i], cranks[i].getBlockPos()),
                            "surviving crank " + i + " must recreate only its own missing leash knot");
                    cranks[i].engine().detachWorker(false);
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ownedMarkerDoesNotStealForeignLeash(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(1, 1, 1);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 1));

        LeashFenceKnotEntity ownKnot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        horse.setLeashedTo(ownKnot, true);
        engine.attachWorker(horse, WorkerResolver.resolve(horse));

        BlockPos foreignPos = crank.getBlockPos().offset(3, 0, 0);
        LeashFenceKnotEntity foreignKnot = LeashFenceKnotEntity.getOrCreateKnot(level, foreignPos);
        horse.setLeashedTo(foreignKnot, true);
        if (ownKnot.isAlive()) {
            ownKnot.discard();
        }

        engine.afterHostTick();

        helper.assertTrue(horse.getLeashHolder() == foreignKnot,
                "self-healing must never steal a worker from a live foreign leash");
        helper.assertFalse(engine.isWorkerResolved(),
                "foreign reassignment must remain visible as an unresolved old crank assignment");

        engine.detachWorker(false);
        horse.dropLeash(true, false);
        if (foreignKnot.isAlive()) {
            foreignKnot.discard();
        }
        helper.succeed();
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

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos localPos) {
        BlockEntity be = helper.getBlockEntity(localPos);
        helper.assertTrue(be instanceof AbstractHorseCrankBlockEntity,
                "expected a horse crank block entity at " + localPos);
        return (AbstractHorseCrankBlockEntity) be;
    }
}
