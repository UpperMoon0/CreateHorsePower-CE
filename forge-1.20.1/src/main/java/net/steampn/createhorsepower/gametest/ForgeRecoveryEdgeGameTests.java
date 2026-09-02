package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;

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
}
