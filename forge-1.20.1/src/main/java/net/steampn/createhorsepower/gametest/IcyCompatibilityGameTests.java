package net.steampn.createhorsepower.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
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
}
