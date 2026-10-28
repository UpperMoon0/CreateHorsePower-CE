package net.steampn.createhorsepower.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.compat.IcyCompatibilityScenarios;

@GameTestHolder("createhorsepower_icy")
@PrefixGameTestTemplate(false)
public final class IcyCompatibilityGameTests {
    @GameTest(template = "kinetic_network")
    public static void recognitionAndPolicy(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.recognitionAndPolicy(h); }

    @GameTest(template = "kinetic_network")
    public static void replacementAndSavedWorker(GameTestHelper h) { IcyCompatibilityScenarios.replacementAndSavedWorker(h); }

    @GameTest(template = "kinetic_network")
    public static void successfulRecallReleasesControl(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.successfulRecallReleasesControl(h); }
    @GameTest(template = "kinetic_network")
    public static void snapshotRecallReleasesVirtualWorker(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.snapshotRecallReleasesVirtualWorker(h); }
    @GameTest(template = "kinetic_network")
    public static void snapshotRecallReleasesLeashedWorker(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.snapshotRecallReleasesLeashedWorker(h); }
    @GameTest(template = "kinetic_network")
    public static void vanillaSwapRetainsWorker(GameTestHelper h) { IcyCompatibilityScenarios.vanillaSwapRetainsWorker(h); }
    @GameTest(template = "kinetic_network")
    public static void permissionInteractionsAndTrust(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.permissionInteractionsAndTrust(h); }
    @GameTest(template = "kinetic_network")
    public static void tickingOrbitAndWagonHandoff(GameTestHelper h) { IcyCompatibilityScenarios.tickingOrbitAndWagonHandoff(h); }
    @GameTest(template = "kinetic_network")
    public static void unauthorizedDetachCannotHandleOwnedOrUnavailableHorse(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyLifecycleScenarios.unauthorizedDetachCannotHandleOwnedOrUnavailableHorse(h); }
    @GameTest(template = "kinetic_network")
    public static void unloadedCachedBodyCannotReturnAttachmentTwice(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyLifecycleScenarios.unloadedCachedBodyCannotReturnAttachmentTwice(h); }
    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void activeCartEquipmentPausesAndResumesAfterTicks(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyCleanupScenarios.activeCartEquipmentPausesAndResumesAfterTicks(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void crankBreakRestoresActiveAi(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyCleanupScenarios.crankBreakRestoresActiveAi(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void crankBreakPreservesOriginalNoAi(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyCleanupScenarios.crankBreakPreservesOriginalNoAi(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void deadWorkerDoesNotResurrectOrKeepGeneration(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyCleanupScenarios.deadWorkerDoesNotResurrectOrKeepGeneration(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void duplicateUuidAndForeignClaimCannotTakeControl(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyCleanupScenarios.duplicateUuidAndForeignClaimCannotTakeControl(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void ownerDisconnectDoesNotOrphanKnotAssignment(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyCleanupScenarios.ownerDisconnectDoesNotOrphanKnotAssignment(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void loadedCommandsRespectFailureAndRelease(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyRecallScenarios.loadedCommandsRespectFailureAndRelease(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void snapshotWhistleRespectsFailureAndRelease(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyRecallScenarios.snapshotWhistleRespectsFailureAndRelease(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void snapshotSendHomeReleases(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyRecallScenarios.snapshotSendHomeReleases(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void snapshotSendHomeUnsafePreservesAssignment(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyRecallScenarios.snapshotSendHomeUnsafePreservesAssignment(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void revivalRespectsFailureAndReleases(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyRecallScenarios.revivalRespectsFailureAndReleases(h); }

    @GameTest(template = "kinetic_network")
    public static void activityOnlyDepartureAndForeignRecovery(GameTestHelper h) { IcyCompatibilityScenarios.activityOnlyDepartureAndForeignRecovery(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void noDropTombstoneSurvivesOldCrankReload(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyLifecycleScenarios.noDropTombstoneSurvivesOldCrankReload(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void loadedNoDropTombstoneSurvivesDirectLeashCleanup(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyLifecycleScenarios.loadedNoDropTombstoneSurvivesDirectLeashCleanup(h); }

    @GameTest(template = "kinetic_network", timeoutTicks = 240)
    public static void noDropTombstoneSurvivesCrankRemoval(GameTestHelper h) throws Exception { net.steampn.createhorsepower.compat.IcyLifecycleScenarios.noDropTombstoneSurvivesCrankRemoval(h); }

}
