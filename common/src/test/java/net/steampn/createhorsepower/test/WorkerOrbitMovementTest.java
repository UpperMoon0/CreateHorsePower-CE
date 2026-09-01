package net.steampn.createhorsepower.test;

import net.steampn.createhorsepower.blocks.crank.WorkerOrbitMovement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkerOrbitMovementTest {
    @Test
    @DisplayName("Movement vectors convert to Minecraft yaw coordinates")
    void cardinalMovementYaw() {
        assertEquals(-90.0F, WorkerOrbitMovement.yawFromMovement(1.0D, 0.0D), 0.001F);
        assertEquals(0.0F, WorkerOrbitMovement.yawFromMovement(0.0D, 1.0D), 0.001F);
        assertEquals(90.0F, WorkerOrbitMovement.yawFromMovement(-1.0D, 0.0D), 0.001F);
        assertEquals(-180.0F, WorkerOrbitMovement.yawFromMovement(0.0D, -1.0D), 0.001F);
    }
}
