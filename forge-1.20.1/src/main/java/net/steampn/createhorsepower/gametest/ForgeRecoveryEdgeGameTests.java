package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.platform.DeferredDetachStore;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.utils.CHPUtils;

import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ForgeRecoveryEdgeGameTests {
    private ForgeRecoveryEdgeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void timeoutWithoutSerializedLeashDoesNotSpawnLead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        BlockPos oldCrankPos = horse.blockPosition().offset(1024, 0, 1024);
        UUID oldCrankUuid = UUID.randomUUID();

        helper.assertFalse(level.hasChunkAt(oldCrankPos), "old crank chunk must begin unloaded");
        helper.assertFalse(horse.isLeashed(), "fixture horse must begin without a leash");

        CompoundTag marker = new CompoundTag();
        marker.putLong("CrankPos", oldCrankPos.asLong());
        marker.putUUID("CrankUuid", oldCrankUuid);
        horse.getPersistentData().put(WorkerAttachmentControl.MARKER_KEY, marker);

        WorkerRecoveryQueue.enqueue(horse, level);
        WorkerRecoveryQueue.expireForTesting(horse.getUUID());

        helper.runAfterDelay(3, () -> {
            helper.assertFalse(WorkerAttachmentControl.hasMarker(horse),
                    "timed-out orphan recovery must clear stale attachment ownership");
            helper.assertFalse(horse.isLeashed(),
                    "timed-out recovery must not invent a live leash for an unleaded worker");
            helper.assertFalse(level.hasChunkAt(oldCrankPos),
                    "timed-out recovery must not force-load the old crank chunk");

            long droppedLeads = level.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(horse.blockPosition()).inflate(6.0D),
                    item -> item.getItem().is(Items.LEAD)
            ).size();
            helper.assertTrue(droppedLeads == 0,
                    "a stale CHP marker without serialized vanilla leash data must not manufacture a lead");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unloadedDetachKeepsOnlyLevelScopedPolicy(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        var blockEntity = helper.getBlockEntity(localCrankPos);
        helper.assertTrue(blockEntity instanceof AbstractHorseCrankBlockEntity,
                "expected a horse crank block entity for deferred-policy regression");
        AbstractHorseCrankBlockEntity crank = (AbstractHorseCrankBlockEntity) blockEntity;
        HorseCrankEngine engine = crank.engine();
        ServerLevel level = helper.getLevel();

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        horse.setLeashedTo(knot, true);
        UUID workerUuid = horse.getUUID();
        engine.setWorkerUuidForTesting(workerUuid);
        engine.setCachedWorkerMobForTesting(horse);
        WorkerAttachmentControl.markAttached(horse, crank.getBlockPos(), engine.crankInstanceUuid());

        horse.discard();
        engine.setCachedWorkerMobForTesting(null);
        engine.detachWorker(false);

        var durable = CHPApi.deferredDetaches().get(level, workerUuid);
        helper.assertTrue(durable != null
                        && durable.matches(crank.getBlockPos(), engine.crankInstanceUuid())
                        && !durable.dropLead(),
                "unloaded detach(false) must retain the authoritative level-scoped no-drop policy");
        helper.assertFalse(engine.hasDeferredDetachPolicy(workerUuid),
                "new unloaded detaches must not retain a duplicate BE-local legacy policy");

        CHPApi.deferredDetaches().remove(level, workerUuid);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "chp_recovery_shutdown")
    public static void transientRecoveryClearDropsPendingReferenceButKeepsClock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        BlockPos oldCrankPos = horse.blockPosition().offset(1024, 0, 1024);
        UUID oldCrankUuid = UUID.randomUUID();

        WorkerActivityControl.acquire(horse, oldCrankPos, oldCrankUuid);
        WorkerRecoveryQueue.enqueue(horse, level);
        WorkerRecoveryQueue.advanceRecoveryAgeForTesting(horse, 42L);
        helper.assertTrue(WorkerRecoveryQueue.isPendingForTesting(horse.getUUID()),
                "fixture must hold a transient pending recovery entry");

        WorkerRecoveryQueue.clearTransientState();

        helper.assertFalse(WorkerRecoveryQueue.isPendingForTesting(horse.getUUID()),
                "server-stop cleanup must drop transient pending references");
        helper.assertTrue(WorkerRecoveryQueue.recoveryAgeForTesting(horse, level.getGameTime()) >= 42L,
                "transient cleanup must preserve the worker's durable recovery clock");

        WorkerActivityControl.releaseFromMarker(horse);
        WorkerRecoveryQueue.clearRecoveryClock(horse);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleCrankCannotClearNewOwnerWithClonedUuid(GameTestHelper helper) {
        BlockPos localCrankA = new BlockPos(0, 1, 0);
        BlockPos localCrankB = new BlockPos(4, 1, 0);
        helper.setBlock(localCrankA, BlockRegister.HORSE_CRANK.get());
        helper.setBlock(localCrankB, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crankA = requireCrank(helper, localCrankA);
        AbstractHorseCrankBlockEntity crankB = requireCrank(helper, localCrankB);
        HorseCrankEngine engineA = crankA.engine();
        HorseCrankEngine engineB = crankB.engine();
        UUID clonedUuid = UUID.randomUUID();
        engineA.setCrankInstanceUuidForTesting(clonedUuid);
        engineB.setCrankInstanceUuidForTesting(clonedUuid);

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        engineA.setWorkerUuidForTesting(horse.getUUID());
        engineA.setCachedWorkerMobForTesting(horse);
        engineA.controlWorkerAiForTesting(horse);
        helper.assertTrue(WorkerActivityControl.isOwnedBy(horse, crankA.getBlockPos(), clonedUuid),
                "crank A must initially own the activity marker");
        helper.assertTrue(WorkerActivityControl.hasForeignMarker(horse, crankB.getBlockPos(), clonedUuid),
                "same UUID at crank B's different position must still be foreign");

        engineB.setWorkerUuidForTesting(horse.getUUID());
        engineB.setCachedWorkerMobForTesting(horse);
        engineB.controlWorkerAiForTesting(horse);
        WorkerAttachmentControl.markAttached(horse, crankB.getBlockPos(), clonedUuid);
        helper.assertTrue(WorkerActivityControl.isOwnedBy(horse, crankB.getBlockPos(), clonedUuid),
                "crank B must replace A as current activity owner despite sharing the UUID");
        helper.assertTrue(WorkerAttachmentControl.isOwnedBy(horse, crankB.getBlockPos(), clonedUuid),
                "crank B must own the persistent attachment marker");
        helper.assertTrue(horse.isNoAi(), "current crank B must retain active AI suppression");

        engineA.detachWorker(false);

        helper.assertTrue(WorkerActivityControl.isOwnedBy(horse, crankB.getBlockPos(), clonedUuid),
                "stale crank A cleanup must not clear crank B's activity marker");
        helper.assertTrue(WorkerAttachmentControl.isOwnedBy(horse, crankB.getBlockPos(), clonedUuid),
                "stale crank A cleanup must not clear crank B's attachment marker");
        helper.assertTrue(horse.isNoAi(),
                "stale crank A cleanup must not restore AI while crank B owns suppression");
        helper.assertFalse(engineA.ownsWorkerActivityMarkerForTesting(),
                "stale crank A must relinquish only its local ownership bookkeeping");
        helper.assertTrue(engineB.ownsWorkerActivityMarkerForTesting(),
                "current crank B must retain its local ownership bookkeeping");

        engineB.detachWorker(false);
        helper.assertFalse(WorkerActivityControl.hasMarker(horse),
                "current crank B cleanup must release its activity marker normally");
        helper.assertFalse(WorkerAttachmentControl.hasMarker(horse),
                "current crank B cleanup must release its attachment marker normally");
        helper.assertFalse(horse.isNoAi(),
                "current crank B cleanup must restore the worker's original AI state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void directMarkerOwnershipUsesPositionAndUuid(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        UUID clonedUuid = UUID.randomUUID();
        BlockPos crankA = horse.blockPosition().offset(4, 0, 0);
        BlockPos crankB = horse.blockPosition().offset(-4, 0, 0);

        helper.assertTrue(WorkerActivityControl.acquire(horse, crankA, clonedUuid),
                "first crank must own the false-to-true NoAI transition");
        helper.assertTrue(WorkerActivityControl.hasForeignMarker(horse, crankB, clonedUuid),
                "same UUID with a different position must be foreign at the marker layer");
        helper.assertTrue(WorkerActivityControl.acquire(horse, crankB, clonedUuid),
                "foreign-position acquire must recover the old marker and establish a fresh baseline");
        helper.assertTrue(WorkerActivityControl.isOwnedBy(horse, crankB, clonedUuid),
                "activity marker must move to the complete crank B identity");

        WorkerAttachmentControl.markAttached(horse, crankB, clonedUuid);
        WorkerAttachmentControl.clearIfOwnedBy(horse, crankA, clonedUuid);
        helper.assertTrue(WorkerAttachmentControl.isOwnedBy(horse, crankB, clonedUuid),
                "same-UUID cleanup from crank A must not erase crank B's attachment marker");
        WorkerAttachmentControl.clearIfOwnedBy(horse, crankB, clonedUuid);
        helper.assertFalse(WorkerAttachmentControl.hasMarker(horse),
                "exact crank B identity must still clear its own attachment marker");

        WorkerActivityControl.release(horse, true);
        helper.assertFalse(WorkerActivityControl.hasMarker(horse),
                "exact owner release must clear the activity marker");
        helper.assertFalse(horse.isNoAi(),
                "exact owner release must restore the original NoAI=false state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void attachmentTimeoutPreservesForeignActivityWithClonedUuid(GameTestHelper helper) {
        BlockPos localCurrentCrank = new BlockPos(0, 1, 0);
        helper.setBlock(localCurrentCrank, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity currentCrank = requireCrank(helper, localCurrentCrank);
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        UUID clonedUuid = UUID.randomUUID();
        BlockPos staleAttachmentPos = horse.blockPosition().offset(1024, 0, 1024);

        helper.assertFalse(level.hasChunkAt(staleAttachmentPos),
                "stale attachment crank chunk must begin unloaded");
        currentCrank.engine().setCrankInstanceUuidForTesting(clonedUuid);
        currentCrank.engine().setWorkerUuidForTesting(horse.getUUID());
        helper.assertTrue(WorkerActivityControl.acquire(horse, currentCrank.getBlockPos(), clonedUuid),
                "current crank fixture must own the worker's activity suppression");
        WorkerAttachmentControl.markAttached(horse, staleAttachmentPos, clonedUuid);
        WorkerRecoveryQueue.enqueue(horse, level);
        WorkerRecoveryQueue.expireForTesting(horse.getUUID());

        helper.runAfterDelay(3, () -> {
            WorkerRecoveryQueue.process(level);
            helper.assertFalse(WorkerAttachmentControl.hasMarker(horse),
                    "timed-out stale attachment marker must be removed");
            helper.assertTrue(WorkerActivityControl.isOwnedBy(
                            horse, currentCrank.getBlockPos(), clonedUuid),
                    "attachment timeout must preserve a different-position activity owner even with the same UUID");
            helper.assertTrue(horse.isNoAi(),
                    "attachment timeout must not restore AI while the current crank still owns suppression");
            helper.assertFalse(WorkerRecoveryQueue.isPendingForTesting(horse.getUUID()),
                    "timed-out stale attachment recovery must leave the queue");
            helper.assertFalse(level.hasChunkAt(staleAttachmentPos),
                    "timeout must not force-load the stale attachment crank chunk");

            WorkerActivityControl.release(horse, true);
            helper.assertFalse(horse.isNoAi(),
                    "explicit current-owner cleanup must still restore the original AI state");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unloadedForeignCleanupCannotOverwriteDurableDetach(GameTestHelper helper) {
        BlockPos localCrankA = new BlockPos(0, 1, 0);
        BlockPos localCrankB = new BlockPos(4, 1, 0);
        helper.setBlock(localCrankA, BlockRegister.HORSE_CRANK.get());
        helper.setBlock(localCrankB, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crankA = requireCrank(helper, localCrankA);
        AbstractHorseCrankBlockEntity crankB = requireCrank(helper, localCrankB);
        ServerLevel level = helper.getLevel();
        UUID workerUuid = UUID.randomUUID();

        DeferredDetachStore.Entry ownedByA = new DeferredDetachStore.Entry(
                crankA.getBlockPos(), crankA.engine().crankInstanceUuid(), false);
        CHPApi.deferredDetaches().put(level, workerUuid, ownedByA);

        CHPUtils.cleanUpLeash(level, crankB.getBlockPos(), workerUuid, true);

        DeferredDetachStore.Entry remaining = CHPApi.deferredDetaches().get(level, workerUuid);
        helper.assertTrue(remaining != null
                        && remaining.matches(crankA.getBlockPos(), crankA.engine().crankInstanceUuid())
                        && !remaining.dropLead(),
                "foreign unloaded cleanup must not overwrite crank A's no-drop durable detach intent");
        CHPApi.deferredDetaches().remove(level, workerUuid);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void exactWorkerDetachPreservesOtherMobOnSharedKnot(GameTestHelper helper) {
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        ServerLevel level = helper.getLevel();
        Horse worker = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        Horse other = helper.spawn(EntityType.HORSE, new BlockPos(3, 1, 2));
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, crank.getBlockPos());
        worker.setLeashedTo(knot, true);
        other.setLeashedTo(knot, true);
        crank.engine().setWorkerUuidForTesting(worker.getUUID());
        crank.engine().setCachedWorkerMobForTesting(worker);
        WorkerAttachmentControl.markAttached(worker, crank.getBlockPos(), crank.engine().crankInstanceUuid());

        crank.engine().detachWorker(false);

        helper.assertFalse(worker.isLeashed(), "assigned worker must be detached");
        helper.assertTrue(other.getLeashHolder() == knot,
                "detaching the assigned worker must preserve another mob sharing the knot");
        helper.assertTrue(knot.isAlive(), "shared knot must remain while another loaded mob uses it");

        other.dropLeash(true, false);
        knot.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recoveryPreservesDelayedForeignEntityLeash(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(2, 1, 2));
        Horse foreignHolder = helper.spawn(EntityType.HORSE, new BlockPos(3, 1, 2));
        BlockPos staleCrankPos = horse.blockPosition().offset(1024, 0, 1024);
        UUID staleCrankUuid = UUID.randomUUID();

        horse.setLeashedTo(foreignHolder, true);
        WorkerAttachmentControl.markAttached(horse, staleCrankPos, staleCrankUuid);

        CompoundTag savedHorse = new CompoundTag();
        CompoundTag savedHolder = new CompoundTag();
        horse.saveWithoutId(savedHorse);
        foreignHolder.saveWithoutId(savedHolder);
        UUID workerUuid = horse.getUUID();
        horse.discard();
        foreignHolder.discard();

        Horse reloaded = EntityType.HORSE.create(level);
        helper.assertTrue(reloaded != null, "horse must be creatable for delayed-leash regression");
        reloaded.load(savedHorse);
        level.addFreshEntity(reloaded);
        CHPApi.deferredDetaches().put(level, workerUuid,
                new DeferredDetachStore.Entry(staleCrankPos, staleCrankUuid, false));
        WorkerRecoveryQueue.enqueue(reloaded, level);

        helper.runAfterDelay(3, () -> {
            CompoundTag afterRecovery = new CompoundTag();
            reloaded.saveWithoutId(afterRecovery);
            helper.assertFalse(WorkerAttachmentControl.hasMarker(reloaded),
                    "matching durable recovery must clear only CHP attachment ownership");
            helper.assertTrue(afterRecovery.contains("Leash")
                            && afterRecovery.getCompound("Leash").hasUUID("UUID"),
                    "unresolved foreign entity UUID leash must survive CHP recovery");

            Horse restoredHolder = EntityType.HORSE.create(level);
            helper.assertTrue(restoredHolder != null, "foreign holder must be recreatable");
            restoredHolder.load(savedHolder);
            level.addFreshEntity(restoredHolder);

            helper.runAfterDelay(6, () -> {
                helper.assertTrue(reloaded.getLeashHolder() == restoredHolder,
                        "vanilla must still be able to resolve the preserved foreign UUID leash");
                reloaded.dropLeash(true, false);
                helper.succeed();
            });
        });
    }

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos localPos) {
        BlockEntity blockEntity = helper.getBlockEntity(localPos);
        helper.assertTrue(blockEntity instanceof AbstractHorseCrankBlockEntity,
                "expected horse crank block entity at " + localPos);
        return (AbstractHorseCrankBlockEntity) blockEntity;
    }
}
