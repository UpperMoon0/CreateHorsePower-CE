package net.steampn.createhorsepower.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.steampn.createhorsepower.compat.BionicsCompatibilityScenarios;

/** Mandatory real-Bionics checks, selected only by the opt-in CI namespace. */
@GameTestHolder("createhorsepower_bionics")
@PrefixGameTestTemplate(false)
public final class BionicsCompatibilityGameTests {
    private BionicsCompatibilityGameTests() {}

    private static void requirePublishedApi(GameTestHelper h) {
        var mod = net.minecraftforge.fml.ModList.get().getModContainerById("createbionics");
        h.assertTrue(mod.isPresent(), "Bionics jar must actually be loaded");
        h.assertTrue(mod.isPresent() && "2.0.0".equals(mod.get().getModInfo().getVersion().toString()),
                "Expected Bionics 2.0.0 exactly, not a different or missing release");
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 80)
    public static void realRegistryAndFuelPolicy(GameTestHelper h) {
        requirePublishedApi(h);
        BionicsCompatibilityScenarios.realRegistryAndFuelPolicy(h);
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 900)
    public static void actualOrbitAndFuelDebit(GameTestHelper h) {
        requirePublishedApi(h);
        BionicsCompatibilityScenarios.actualOrbitAndFuelDebit(h);
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 300)
    public static void stopResumeAndDetach(GameTestHelper h) {
        requirePublishedApi(h);
        BionicsCompatibilityScenarios.stopResumeAndDetach(h);
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 200)
    public static void realRiderWaterAndTamingPolicy(GameTestHelper h) {
        requirePublishedApi(h);
        // Exercise the *actual* loader config instead of only a mocked
        // require-tame boolean. Restore synchronously before delayed water work.
        boolean previous = net.steampn.createhorsepower.config.Config.REQUIRE_TAMED_HORSE.get();
        net.steampn.createhorsepower.config.Config.REQUIRE_TAMED_HORSE.set(true);
        try {
            BionicsCompatibilityScenarios.realRiderWaterAndTamingPolicy(h, h.makeMockPlayer());
        } finally {
            net.steampn.createhorsepower.config.Config.REQUIRE_TAMED_HORSE.set(previous);
        }
    }

    @GameTest(template = "kinetic_network", timeoutTicks = 260)
    public static void invalidTrackStopsRealFuelBurn(GameTestHelper h) {
        requirePublishedApi(h);
        BionicsCompatibilityScenarios.invalidTrackStopsRealFuelBurn(h);
    }
}
