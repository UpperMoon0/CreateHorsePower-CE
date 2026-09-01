package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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

        assertOrbitStep(helper, horse, centerX, centerZ, radius);
        assertOrbitStep(helper, horse, centerX, centerZ, radius);
        helper.assertTrue(!horse.isEating(), "Working horses must not remain in their eating animation");
        helper.succeed();
    }

    private static void assertOrbitStep(GameTestHelper helper, Horse horse, double centerX, double centerZ, double radius) {
        double oldX = horse.getX();
        double oldZ = horse.getZ();
        WorkerOrbitMovement.move(horse, centerX, centerZ, (float) radius, Math.toRadians(15.0D));
        double deltaX = horse.getX() - oldX;
        double deltaZ = horse.getZ() - oldZ;
        double radialDistance = Math.hypot(horse.getX() - centerX, horse.getZ() - centerZ);
        float expectedYaw = WorkerOrbitMovement.yawFromMovement(deltaX, deltaZ);
        float yawError = Math.abs(Mth.wrapDegrees(horse.getYRot() - expectedYaw));

        helper.assertTrue(deltaX * deltaX + deltaZ * deltaZ > 1.0e-8D, "Worker must move along its orbit");
        helper.assertTrue(Math.abs(radialDistance - radius) < 0.001D, "Worker must remain on its configured radius");
        helper.assertTrue(yawError < 0.1F, "Worker yaw must face its actual movement vector");
    }
}
