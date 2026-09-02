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

    @Test
    @DisplayName("Stored orbit angles advance independently and remain normalized")
    void orbitAngleProgression() {
        double angularDelta = 0.013934425314345814D;
        double angle = -2.431930893039327D;
        for (int tick = 0; tick < 520; tick++) {
            angle = WorkerOrbitMovement.normalizeAngle(angle + angularDelta);
        }

        assertEquals(
                WorkerOrbitMovement.normalizeAngle(-2.431930893039327D + angularDelta * 520.0D),
                angle,
                1.0e-12D);
    }
    @Test
    @DisplayName("Visual ground speed is bounded independently from mechanical RPM")
    void visualGroundSpeedClamps() {
        assertEquals(0.8D, WorkerOrbitMovement.groundSpeedBlocksPerSecond(0.01D, 10.0D, 0.8D, 3.5D), 1.0e-9D);
        assertEquals(2.25D, WorkerOrbitMovement.groundSpeedBlocksPerSecond(0.225D, 10.0D, 0.8D, 3.5D), 1.0e-9D);
        assertEquals(3.5D, WorkerOrbitMovement.groundSpeedBlocksPerSecond(2.0D, 10.0D, 0.8D, 3.5D), 1.0e-9D);
    }

    @Test
    @DisplayName("Equal visual ground speed stays equal across worker radii")
    void linearOrbitSpeedIsRadiusIndependent() {
        double groundSpeed = 2.4D;
        double smallDelta = WorkerOrbitMovement.angularDeltaPerTick(groundSpeed, 2.5D, 1.0D);
        double largeDelta = WorkerOrbitMovement.angularDeltaPerTick(groundSpeed, 6.0D, 1.0D);

        assertEquals(groundSpeed / 20.0D, WorkerOrbitMovement.linearDistancePerTick(2.5D, smallDelta), 1.0e-12D);
        assertEquals(groundSpeed / 20.0D, WorkerOrbitMovement.linearDistancePerTick(6.0D, largeDelta), 1.0e-12D);
    }

}
