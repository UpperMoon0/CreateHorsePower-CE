package net.steampn.createhorsepower.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerOrbitMovement;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
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

    /**
     * Two consecutive {@link WorkerActivityControl#acquire} calls from the
     * same crank must preserve the worker's original {@code NoAI} state.
     * The previous implementation overwrote {@code PreviousNoAi} with the
     * mob's current {@code NoAI} (which is {@code true} after the first
     * acquire), losing the original baseline.
     */
    @GameTest(template = "empty")
    public static void sameOwnerReacquirePreservesOriginalNoAi(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        helper.assertTrue(!horse.isNoAi(), "Pre-condition: ordinary horse has AI enabled");

        UUID crank = new UUID(0x5555L, 0xEEEEEEEEL);
        BlockPos pos = new BlockPos(1, 1, 1);

        boolean first = WorkerActivityControl.acquire(horse, pos, crank);
        helper.assertTrue(first, "First acquire must claim ownership for an AI-enabled horse");
        helper.assertTrue(horse.isNoAi(), "First acquire must suppress AI");
        helper.assertFalse(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must still record original NoAI=false after first acquire");

        boolean second = WorkerActivityControl.acquire(horse, pos, crank);
        helper.assertTrue(second, "Second acquire from the same crank must still own the suppression");
        helper.assertTrue(horse.isNoAi(), "Second acquire must keep the horse NoAI");
        helper.assertFalse(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must STILL record original NoAI=false after second acquire");

        WorkerActivityControl.release(horse, second);
        helper.assertFalse(horse.isNoAi(), "Final release must return the horse to its original NoAI");
        helper.succeed();
    }

    /**
     * A worker that started out with {@code NoAI=true} must stay that way
     * after a same-crank reacquire. The crank must not own a pre-existing
     * NoAI state.
     */
    @GameTest(template = "empty")
    public static void preexistingNoAiSurvivesSameOwnerReacquire(GameTestHelper helper) {
        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(0, 0, 0));
        horse.setNoAi(true);

        UUID crank = new UUID(0x6666L, 0xFFFFFFFEL);
        BlockPos pos = new BlockPos(2, 1, 2);

        boolean first = WorkerActivityControl.acquire(horse, pos, crank);
        boolean second = WorkerActivityControl.acquire(horse, pos, crank);

        helper.assertFalse(first, "Crank must not own a pre-existing NoAI state");
        helper.assertFalse(second, "Same-crank reacquire must not flip ownership either");
        helper.assertTrue(horse.isNoAi(), "Preexisting NoAI must still hold");
        helper.assertTrue(WorkerActivityControl.readPreviousNoAi(horse),
                "Marker must record the original NoAI=true across the reacquire");

        WorkerActivityControl.release(horse, second);
        helper.assertTrue(horse.isNoAi(), "Final release must leave the horse at its original NoAI=true");
        helper.succeed();
    }

    /**
     * Two different crank UUIDs at the same block position must NOT be
     * treated as the same owner. The previous position-derived UUID would
     * have failed this test, allowing a fresh crank to inherit a stale
     * marker from a predecessor.
     */
    @GameTest(template = "empty")
    public static void replacementCrankAtSamePositionIsForeign(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(0, 0, 0));
        helper.assertTrue(!cow.isNoAi(), "Pre-condition: ordinary cow has AI enabled");

        UUID crankA = new UUID(0x7777L, 0xAAAAAAAAAAAAL);
        UUID crankB = new UUID(0x8888L, 0xBBBBBBBBBBBBL);
        helper.assertTrue(!crankA.equals(crankB), "Pre-condition: test UUIDs must differ");
        BlockPos samePos = new BlockPos(3, 1, 3);

        boolean first = WorkerActivityControl.acquire(cow, samePos, crankA);
        helper.assertTrue(first, "Crank A must own the suppression");
        helper.assertTrue(cow.isNoAi(), "Crank A suppresses the cow's AI");
        helper.assertTrue(WorkerActivityControl.hasForeignMarker(cow, crankB),
                "Crank B at the same position must see crank A's marker as foreign");
        helper.assertFalse(WorkerActivityControl.hasForeignMarker(cow, crankA),
                "Crank A's own marker is not foreign to itself");

        WorkerActivityControl.release(cow, first);
        helper.succeed();
    }

    /**
     * When a marked mob loads into a server world and the original crank no
     * longer exists at the recorded position, the marker must be consumed
     * and the mob's original {@code NoAI} state restored.
     */
    @GameTest(template = "empty")
    public static void orphanMarkerIsRecoveredOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
        helper.assertTrue(level.getBlockEntity(worldCrankPos) == null,
                "Pre-condition: no crank at the recorded position");

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 3));
        UUID crankA = new UUID(0x9999L, 0xCCCCCCCCCL);
        boolean owns = WorkerActivityControl.acquire(horse, worldCrankPos, crankA);
        helper.assertTrue(owns, "Crank A must own the suppression");
        helper.assertTrue(horse.isNoAi(), "Crank A suppresses the horse's AI");
        helper.assertTrue(WorkerActivityControl.hasMarker(horse), "Marker must be present");

        boolean recovered = WorkerActivityControl.recoverIfOrphaned(horse, level);
        helper.assertTrue(recovered, "recoverIfOrphaned must consume the marker when no live crank exists");
        helper.assertFalse(horse.isNoAi(), "Recovered horse must return to NoAI=false");
        helper.assertFalse(WorkerActivityControl.hasMarker(horse), "Marker must be removed after recovery");
        helper.succeed();
    }

    /**
     * When a marked mob loads and the live crank at the recorded position
     * still has a matching instance UUID and still claims this mob, the
     * marker must be left alone so the live crank can finish its work.
     */
    @GameTest(template = "empty")
    public static void liveCrankWithMatchingUuidKeepsMarker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());

        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        UUID liveUuid = new UUID(0xAAAA0L, 0xDDDDDDD0L);
        crank.engine().setCrankInstanceUuidForTesting(liveUuid);

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 3));
        boolean owns = WorkerActivityControl.acquire(horse, worldCrankPos, liveUuid);
        helper.assertTrue(owns, "Pre-condition: this acquire must own the suppression");

        crank.engine().setWorkerUuidForTesting(horse.getUUID());
        helper.assertTrue(crank.engine().isAssignedWorker(horse.getUUID()),
                "Pre-condition: live crank claims this horse");
        helper.assertTrue(WorkerActivityControl.markerCrankUuid(horse).equals(liveUuid),
                "Pre-condition: marker must record the live UUID");
        helper.assertTrue(WorkerActivityControl.markerCrankPos(horse).equals(worldCrankPos),
                "Pre-condition: marker must record the live crank position");

        boolean recovered = WorkerActivityControl.recoverIfOrphaned(horse, level);
        helper.assertFalse(recovered, "recoverIfOrphaned must NOT consume a marker that matches a live crank");
        helper.assertTrue(WorkerActivityControl.hasMarker(horse), "Marker must remain on the worker");
        helper.assertTrue(horse.isNoAi(), "Live crank still owns the suppression");

        WorkerActivityControl.release(horse, owns);
        helper.succeed();
    }

    /**
     * When crank A acquires a worker and then disappears, and a brand-new
     * crank B (different UUID) is placed at the same coordinates, the
     * worker's marker must be considered orphaned because crank B is not
     * the recorded owner even though it is at the same block.
     */
    @GameTest(template = "empty")
    public static void foreignReplacementCrankTriggersOrphan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        BlockPos worldCrankPos = helper.absolutePos(localCrankPos);

        UUID crankA = new UUID(0xCAFE0L, 0x11111111L);
        UUID crankB = new UUID(0xCAFE0L, 0x22222222L);
        helper.assertTrue(!crankA.equals(crankB), "Pre-condition: replacement UUIDs must differ");

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(3, 0, 3));
        boolean owns = WorkerActivityControl.acquire(horse, worldCrankPos, crankA);
        helper.assertTrue(owns, "Crank A must own the suppression initially");
        helper.assertTrue(horse.isNoAi(), "Crank A suppresses the horse's AI");

        // Crank A vanishes (no block entity at the recorded position).
        // A new crank B is placed at the same coordinates.
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity replacement = requireCrank(helper, localCrankPos);
        replacement.engine().setCrankInstanceUuidForTesting(crankB);
        // Crank B does NOT claim the original worker.

        boolean recovered = WorkerActivityControl.recoverIfOrphaned(horse, level);
        helper.assertTrue(recovered,
                "Marker must be recovered when the live crank at the recorded position is a different instance");
        helper.assertFalse(horse.isNoAi(), "Recovered horse must return to NoAI=false");
        helper.assertFalse(WorkerActivityControl.hasMarker(horse), "Marker must be removed after recovery");
        helper.succeed();
    }

    /**
     * Permanent-detach regression: when the engine owns worker A and A
     * becomes unresolvable (discarded/unloaded), a permanent detach must
     * drop the engine-side AI-suppression ownership even though restoration
     * could not run. Otherwise the ownership record stays stuck on A and
     * the next worker B is merely maintained ({@code NoAI=true} without a
     * marker of its own) instead of acquiring its own recoverable marker —
     * a permanent frozen-animal bug.
     */
    @GameTest(template = "empty")
    public static void permanentClearDropsOwnershipForNextWorker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos localCrankPos = new BlockPos(0, 1, 0);
        helper.setBlock(localCrankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = requireCrank(helper, localCrankPos);
        HorseCrankEngine engine = crank.engine();

        // Worker A is acquired by the engine through its normal control path.
        Horse workerA = helper.spawn(EntityType.HORSE, new BlockPos(2, 0, 2));
        helper.assertFalse(workerA.isNoAi(), "Pre-condition: horse A has AI enabled");
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Pre-condition: a fresh crank owns no AI suppression");
        engine.controlWorkerAiForTesting(workerA);
        helper.assertTrue(workerA.isNoAi(), "Engine must suppress worker A's AI");
        helper.assertTrue(engine.ownsWorkerAiSuppressionForTesting(),
                "Engine must own worker A's suppression");
        helper.assertTrue(workerA.getUUID().equals(engine.aiSuppressedWorkerUuidForTesting()),
                "Engine's suppression record must name worker A");
        helper.assertTrue(WorkerActivityControl.hasMarker(workerA), "Worker A must carry a marker");

        // Worker A becomes unresolvable: discarded, so the level's entity
        // index no longer contains it. The cached reference is also dropped
        // to simulate a chunk-unload/BE-reload, where ownership survives in
        // NBT but no entity reference remains and the level lookup fails.
        workerA.discard();
        engine.setCachedWorkerMobForTesting(null);
        helper.assertTrue(level.getEntity(workerA.getUUID()) == null,
                "Pre-condition: discarded worker A is unresolved in the level");

        // Permanent detach: restoreWorkerAi cannot resolve A, so the clear
        // must still abandon the engine-side ownership record.
        engine.detachWorker(false);
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Permanent clear must abandon AI suppression ownership");
        helper.assertTrue(engine.aiSuppressedWorkerUuidForTesting() == null,
                "Permanent clear must drop the suppressed worker record");

        // Worker B attaches and starts work. It must receive its OWN marker
        // naming this crank's instance UUID; a stale ownership record would
        // have left B marker-less and permanently NoAI.
        Cow workerB = helper.spawn(EntityType.COW, new BlockPos(3, 0, 3));
        helper.assertFalse(workerB.isNoAi(), "Pre-condition: cow B has AI enabled");
        engine.attachWorker(workerB, WorkerResolver.resolve(workerB));
        engine.controlWorkerAiForTesting(workerB);
        helper.assertTrue(workerB.isNoAi(), "Engine must suppress worker B's AI");
        helper.assertTrue(WorkerActivityControl.hasMarker(workerB),
                "Worker B must receive its own recovery marker");
        UUID crankUuid = engine.crankInstanceUuid();
        helper.assertTrue(crankUuid.equals(WorkerActivityControl.markerCrankUuid(workerB)),
                "Worker B's marker must name this crank's instance UUID");
        helper.assertFalse(WorkerActivityControl.readPreviousNoAi(workerB),
                "Worker B's marker must record B's original NoAI=false");

        // Detaching B restores it cleanly through its own marker.
        engine.detachWorker(false);
        helper.assertFalse(workerB.isNoAi(), "Worker B must return to NoAI=false after detach");
        helper.assertFalse(WorkerActivityControl.hasMarker(workerB),
                "Worker B's marker must be removed after detach");
        helper.assertFalse(engine.ownsWorkerAiSuppressionForTesting(),
                "Engine must not own any suppression after detaching B");
        helper.succeed();
    }

    private static AbstractHorseCrankBlockEntity requireCrank(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (!(be instanceof AbstractHorseCrankBlockEntity crank)) {
            throw new AssertionError(
                    "BlockEntity at " + pos + " must be a " + TileEntityRegister.HORSE_CRANK.getId()
                            + " but was " + (be == null ? "null" : be.getType().toString()));
        }
        return crank;
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
