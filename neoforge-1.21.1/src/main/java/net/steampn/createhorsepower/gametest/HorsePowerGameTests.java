package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerOrbitMovement;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.TileEntityRegister;

import java.util.UUID;

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
    public static void nonHorseWorkerUsesSameOrbitControl(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        double radius = 2.5D;
        double centerX = cow.getX() - radius;
        double centerZ = cow.getZ();
        boolean ownsAiSuppression = WorkerActivityControl.acquire(cow);

        helper.assertTrue(ownsAiSuppression, "Crank must suppress AI for non-horse workers");
        helper.assertTrue(cow.isNoAi(), "Working non-horse AI must be suppressed");
        double orbitAngle = WorkerOrbitMovement.angleFromPosition(cow.getX(), cow.getZ(), centerX, centerZ);
        assertOrbitStep(helper, cow, centerX, centerZ, radius, orbitAngle);
        WorkerActivityControl.release(cow, ownsAiSuppression);
        helper.assertTrue(!cow.isNoAi(), "Non-horse AI must be restored after work");
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

    /**
     * A worker is acquired by crank A, then crank A is removed (its local
     * ownership record is gone) and the marker is recovered purely from the
     * mob's persistent data. After recovery, an ordinary AI-enabled mob must
     * be back to NoAI=false.
     */
    @GameTest(template = "empty")
    public static void strandedWorkerAiIsRecoveredFromMarker(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        helper.assertTrue(!horse.isNoAi(), "Pre-condition: ordinary horse has AI enabled");

        UUID crankA = new UUID(0x1111L, 0xAAAAAAAAL);
        boolean ownsAiSuppression = WorkerActivityControl.acquire(horse, new BlockPos(3, 1, 3), crankA);
        helper.assertTrue(ownsAiSuppression, "Crank A must own the suppression for an ordinary AI-enabled horse");
        helper.assertTrue(horse.isNoAi(), "Crank A suppresses the horse's AI");

        boolean recovered = WorkerActivityControl.releaseFromMarker(horse);
        helper.assertTrue(recovered, "releaseFromMarker must find the stranded marker");
        helper.assertTrue(!horse.isNoAi(), "Stranded horse must return to NoAI=false after marker recovery");
        helper.succeed();
    }

    /**
     * A worker that was already NoAI=true before the crank touched it must
     * stay that way after both the fast-path release and the marker-only
     * recovery path.
     */
    @GameTest(template = "empty")
    public static void preexistingNoAiSurvivesMarkerRecovery(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        horse.setNoAi(true);

        UUID crankA = new UUID(0x2222L, 0xBBBBBBBBL);
        WorkerActivityControl.acquire(horse, new BlockPos(3, 1, 3), crankA);
        boolean recovered = WorkerActivityControl.releaseFromMarker(horse);
        helper.assertTrue(recovered, "releaseFromMarker must find the stranded marker");
        helper.assertTrue(horse.isNoAi(), "Pre-existing NoAI must survive marker recovery");
        helper.succeed();
    }

    /**
     * If a worker was already under the control of a different crank that
     * later disappeared, the next attach to a fresh crank must clear the
     * stale marker before issuing its own acquisition, so the worker ends up
     * in the correct NoAI state (true) and the new crank ends up owning the
     * suppression.
     */
    @GameTest(template = "empty")
    public static void reassignmentClearsStaleMarker(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        helper.assertTrue(!cow.isNoAi(), "Pre-condition: ordinary cow has AI enabled");

        UUID crankA = new UUID(0x3333L, 0xCCCCCCCCL);
        UUID crankB = new UUID(0x4444L, 0xDDDDDDDDL);

        WorkerActivityControl.acquire(cow, new BlockPos(1, 1, 1), crankA);
        helper.assertTrue(cow.isNoAi(), "Crank A suppresses the cow's AI");
        helper.assertTrue(WorkerActivityControl.hasForeignMarker(cow, crankB),
                "Crank B must see crank A's marker as foreign");

        WorkerActivityControl.releaseFromMarker(cow);
        helper.assertTrue(!cow.isNoAi(), "Stale marker recovery restores the cow's original AI");

        boolean ownsB = WorkerActivityControl.acquire(cow, new BlockPos(2, 1, 2), crankB);
        helper.assertTrue(ownsB, "Crank B must own the suppression for an AI-enabled cow");
        helper.assertTrue(cow.isNoAi(), "Crank B suppresses the cow's AI");
        helper.assertTrue(!WorkerActivityControl.hasForeignMarker(cow, crankB),
                "Crank B's own marker is not foreign to itself");

        WorkerActivityControl.release(cow, ownsB);
        helper.assertTrue(!cow.isNoAi(), "Crank B's release returns the cow to NoAI=false");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noMarkerMeansNothingToRelease(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        helper.assertTrue(!WorkerActivityControl.releaseFromMarker(cow),
                "releaseFromMarker on a clean worker is a no-op");
        helper.assertTrue(!cow.isNoAi(), "Untouched worker stays at NoAI=false");
        helper.succeed();
    }

    private static double assertOrbitStep(
            GameTestHelper helper,
            Mob worker,
            double centerX,
            double centerZ,
            double radius,
            double currentAngle
    ) {
        double oldX = worker.getX();
        double oldZ = worker.getZ();
        double newAngle = WorkerOrbitMovement.normalizeAngle(currentAngle + Math.toRadians(15.0D));
        WorkerOrbitMovement.moveToAngle(worker, centerX, centerZ, (float) radius, currentAngle, newAngle);
        double deltaX = worker.getX() - oldX;
        double deltaZ = worker.getZ() - oldZ;
        double radialDistance = Math.hypot(worker.getX() - centerX, worker.getZ() - centerZ);
        float expectedYaw = WorkerOrbitMovement.yawFromMovement(deltaX, deltaZ);
        float yawError = Math.abs(Mth.wrapDegrees(worker.getYRot() - expectedYaw));

        helper.assertTrue(deltaX * deltaX + deltaZ * deltaZ > 1.0e-8D, "Worker must move along its orbit");
        helper.assertTrue(Math.abs(radialDistance - radius) < 0.001D, "Worker must remain on its configured radius");
        helper.assertTrue(yawError < 0.1F, "Worker yaw must face its actual movement vector");
        return newAngle;
    }
}
