package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.steampn.createhorsepower.content.attachment.AttachmentMode;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.machine.AnimalPowerMachinePolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPowerEngineMigrationTest {

    @Test
    void legacySingleWorkerNbtMigratesIntoAssignmentListWithoutDataLoss() {
        UUID worker = UUID.randomUUID();
        BlockPos workerPos = new BlockPos(7, 65, -3);
        CompoundTag legacy = new CompoundTag();
        legacy.putUUID("WorkerUUID", worker);
        legacy.putLong("WorkerPos", workerPos.asLong());
        legacy.putFloat("WorkerRadius", 2.5f);
        legacy.putString("RedstoneMode", RedstoneMode.IGNORE.getSerializedName());

        ResourceLocation machineId = ResourceLocation.tryParse("createhorsepower:horse_crank");
        assertNotNull(machineId);
        AnimalPowerEngine engine = new AnimalPowerEngine(
                new FakeHost(),
                RedstoneMode.HIGH_STOPS,
                new AnimalPowerMachinePolicy(machineId, 1));

        engine.read(legacy, false);

        assertEquals(worker, engine.getWorkerUuid());
        assertTrue(engine.isAssignedWorker(worker));
        assertEquals(1, engine.assignments().entries().size());
        assertEquals(workerPos, engine.assignments().primary().lastKnownPos());
        assertEquals(AttachmentMode.VANILLA_LEASH, engine.attachmentMode());
        assertEquals("createhorsepower:legacy_vanilla_leash", engine.attachmentProfileId());
        assertEquals(RedstoneMode.IGNORE, engine.getRedstoneMode());

        CompoundTag migrated = new CompoundTag();
        engine.write(migrated, false);
        assertTrue(migrated.hasUUID("WorkerUUID"), "legacy single-worker key remains for backward compatibility");
        assertTrue(migrated.contains("AnimalPowerWorkers"), "generalized runtime must persist the new assignment list");
        assertEquals(worker, migrated.getUUID("WorkerUUID"));
    }

    private static final class FakeHost implements AnimalPowerEngine.Host {
        @Override public Level level() { return null; }
        @Override public BlockPos pos() { return BlockPos.ZERO; }
        @Override public BlockState blockState() { return null; }
        @Override public boolean hasWorkerProperty() { return false; }
        @Override public void setWorkerPresent(boolean present) {}
        @Override public void setBlockState(BlockState state) {}
        @Override public float theoreticalSpeed() { return 0.0f; }
        @Override public void refreshKinetic() {}
        @Override public void syncToClient() {}
        @Override public void markDirty() {}
        @Override public void clearKineticInfo() {}
        @Override public void requestSpeedUpdate() {}
        @Override public void setLastCapacityProvided(float capacity) {}
    }
}
