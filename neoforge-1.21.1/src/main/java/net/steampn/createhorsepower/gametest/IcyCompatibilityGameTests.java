package net.steampn.createhorsepower.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.compat.IcyCompatibilityScenarios;

@GameTestHolder("createhorsepower_icy")
@PrefixGameTestTemplate(false)
public final class IcyCompatibilityGameTests {
    @GameTest(template = "minecraft:kinetic_network")
    public static void recognitionAndPolicy(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.recognitionAndPolicy(h); }

    @GameTest(template = "minecraft:kinetic_network")
    public static void replacementAndSavedWorker(GameTestHelper h) { IcyCompatibilityScenarios.replacementAndSavedWorker(h); }

    @GameTest(template = "minecraft:kinetic_network")
    public static void successfulRecallReleasesControl(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.successfulRecallReleasesControl(h); }
    @GameTest(template = "minecraft:kinetic_network")
    public static void snapshotRecallReleasesVirtualWorker(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.snapshotRecallReleasesVirtualWorker(h); }
    @GameTest(template = "minecraft:kinetic_network")
    public static void snapshotRecallReleasesLeashedWorker(GameTestHelper h) throws Exception { IcyCompatibilityScenarios.snapshotRecallReleasesLeashedWorker(h); }
}
