package net.steampn.createhorsepower.test;

import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WorkerRecoveryQueueTest {
    @Test
    void deferredDiagnosticsAreRateLimited() {
        long next = 0L;
        assertTrue(WorkerRecoveryQueue.shouldLogDeferred(100L, next));
        next = WorkerRecoveryQueue.nextDeferredReminder(100L);
        assertFalse(WorkerRecoveryQueue.shouldLogDeferred(101L, next));
        assertFalse(WorkerRecoveryQueue.shouldLogDeferred(next - 1L, next));
        assertTrue(WorkerRecoveryQueue.shouldLogDeferred(next, next));
    }
}
