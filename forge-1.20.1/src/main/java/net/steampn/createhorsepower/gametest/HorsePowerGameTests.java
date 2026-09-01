package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerOrbitMovement;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.TileEntityRegister;

/** Loader-level smoke test proving the crank registrations survive full server bootstrap. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class HorsePowerGameTests {
    private HorsePowerGameTests() {}

    @GameTest(template = "empty")
    public static void crankRegistrations(GameTestHelper helper) {
        BlockRegister.HORSE_CRANK.get();
        TileEntityRegister.HORSE_CRANK.get();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void workerOrbitFacesMovement(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        double radius = 2.5D;
        double centerX = horse.getX() - radius;
        double centerZ = horse.getZ();
        horse.setEating(true);
        boolean ownsAiSuppression = WorkerActivityControl.acquire(horse);
        helper.assertTrue(ownsAiSuppression, "Crank must own suppression for an ordinary AI-enabled horse");
        helper.assertTrue(horse.isNoAi(), "Working horse AI must be suppressed");

        double orbitAngle = WorkerOrbitMovement.angleFromPosition(horse.getX(), horse.getZ(), centerX, centerZ);
        orbitAngle = assertOrbitStep(helper, horse, centerX, centerZ, radius, orbitAngle);
        assertOrbitStep(helper, horse, centerX, centerZ, radius, orbitAngle);
        helper.assertTrue(!horse.isEating(), "Working horses must not remain in their eating animation");

        WorkerActivityControl.release(horse, ownsAiSuppression);
        helper.assertTrue(!horse.isNoAi(), "Crank-owned AI suppression must be restored after work");
        horse.setNoAi(true);
        boolean ownsPreexistingSuppression = WorkerActivityControl.acquire(horse);
        helper.assertTrue(!ownsPreexistingSuppression, "Crank must not own a pre-existing NoAI state");
        WorkerActivityControl.release(horse, ownsPreexistingSuppression);
        helper.assertTrue(horse.isNoAi(), "Pre-existing NoAI state must survive crank release");
        helper.succeed();
    }

    private static double assertOrbitStep(
            GameTestHelper helper,
            Horse horse,
            double centerX,
            double centerZ,
            double radius,
            double currentAngle
    ) {
        double oldX = horse.getX();
        double oldZ = horse.getZ();
        double newAngle = WorkerOrbitMovement.normalizeAngle(currentAngle + Math.toRadians(15.0D));
        WorkerOrbitMovement.moveToAngle(horse, centerX, centerZ, (float) radius, currentAngle, newAngle);
        double deltaX = horse.getX() - oldX;
        double deltaZ = horse.getZ() - oldZ;
        double radialDistance = Math.hypot(horse.getX() - centerX, horse.getZ() - centerZ);
        float expectedYaw = WorkerOrbitMovement.yawFromMovement(deltaX, deltaZ);
        float yawError = Math.abs(Mth.wrapDegrees(horse.getYRot() - expectedYaw));

        helper.assertTrue(deltaX * deltaX + deltaZ * deltaZ > 1.0e-8D, "Worker must move along its orbit");
        helper.assertTrue(Math.abs(radialDistance - radius) < 0.001D, "Worker must remain on its configured radius");
        helper.assertTrue(yawError < 0.1F, "Worker yaw must face its actual movement vector");
        return newAngle;
    }
}
