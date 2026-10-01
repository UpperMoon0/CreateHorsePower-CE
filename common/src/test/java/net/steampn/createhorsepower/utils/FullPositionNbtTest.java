package net.steampn.createhorsepower.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.steampn.createhorsepower.content.machine.WorkerAssignments;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FullPositionNbtTest {
    @Test void exactPositionsSurviveBothEnvelopeEdgesAndPackedAliases() {
        for (String key : new String[]{"CrankPos", "WorkerPos", "Pos"}) {
            for (int y : new int[]{-8_000_000, -1_000_000, -2049, -65, 320, 2048, 1_000_000, 7_999_999}) {
                BlockPos pos = new BlockPos(-17, y, 33);
                CompoundTag tag = new CompoundTag();
                FullPositionNbt.put(tag, key, pos);
                assertEquals(pos, FullPositionNbt.get(tag.copy(), key));
                assertTrue(tag.contains(key, 4), "legacy packed key must remain present");
                if (y < -2048 || y > 2047) assertNotEquals(pos, BlockPos.of(tag.getLong(key)));
            }
        }
    }
    @Test void oldDenseSaveRemainsReadableAndMalformedExactDataFailsClosed() {
        CompoundTag tag = new CompoundTag();
        BlockPos pos = new BlockPos(2, 70, 3);
        tag.putLong("CrankPos", pos.asLong());
        assertEquals(pos, FullPositionNbt.get(tag, "CrankPos"));
        tag.putIntArray("CrankPosXYZ", new int[]{1, 2});
        assertThrows(IllegalArgumentException.class, () -> FullPositionNbt.get(tag, "CrankPos"));
        tag.putString("CrankPosXYZ", "invalid");
        assertThrows(IllegalArgumentException.class, () -> FullPositionNbt.get(tag, "CrankPos"));
    }
    @Test void realWorkerAssignmentRoundTripRetainsHighPositionAndUuid() {
        WorkerAssignments before = new WorkerAssignments(1);
        UUID worker = UUID.randomUUID();
        BlockPos pos = new BlockPos(7, -1_000_000, -3);
        before.assign(worker, pos);
        CompoundTag tag = new CompoundTag(); before.write(tag);
        WorkerAssignments after = new WorkerAssignments(1);
        assertTrue(after.read(tag.copy()));
        assertEquals(worker, after.primary().workerUuid());
        assertEquals(pos, after.primary().lastKnownPos());
    }
}
