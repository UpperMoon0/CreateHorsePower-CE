package net.steampn.createhorsepower.test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.steampn.createhorsepower.content.machine.WorkerAssignments;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerAssignmentsTest {
    @Test
    void boundedAssignmentsPersistAndMigrateIndependentlyOfHorseCrank() {
        WorkerAssignments assignments = new WorkerAssignments(2);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        assignments.assign(first, new BlockPos(1, 2, 3));
        assignments.assign(second, null);
        assertThrows(IllegalStateException.class, () -> assignments.assign(third, BlockPos.ZERO));

        CompoundTag tag = new CompoundTag();
        assignments.write(tag);
        WorkerAssignments restored = new WorkerAssignments(2);
        assertTrue(restored.read(tag));
        assertEquals(2, restored.entries().size());
        assertEquals(first, restored.primary().workerUuid());
        assertEquals(new BlockPos(1, 2, 3), restored.primary().lastKnownPos());
    }
}
