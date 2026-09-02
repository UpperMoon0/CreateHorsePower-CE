package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.registry.BlockRegister;

import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class CrankIdentityCollisionGameTests {
    private CrankIdentityCollisionGameTests() {}

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

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos localPos) {
        BlockEntity blockEntity = helper.getBlockEntity(localPos);
        helper.assertTrue(blockEntity instanceof AbstractHorseCrankBlockEntity,
                "expected horse crank block entity at " + localPos);
        return (AbstractHorseCrankBlockEntity) blockEntity;
    }
}
