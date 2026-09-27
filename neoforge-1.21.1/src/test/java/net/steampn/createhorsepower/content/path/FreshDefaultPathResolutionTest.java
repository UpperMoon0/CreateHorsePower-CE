package net.steampn.createhorsepower.content.path;

import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.content.stats.PathStats;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreshDefaultPathResolutionTest {
    @Test
    void untouchedFreshConfigFallsThroughToRichBundledDirtAndGravelProfiles() {
        assertTrue(Config.POOR_PATH.getDefault().isEmpty());
        assertTrue(Config.NORMAL_PATH.getDefault().isEmpty());
        assertTrue(Config.GREAT_PATH.getDefault().isEmpty());
        assertEquals(0.70f, resolveWithFreshDefaults("minecraft:dirt", new PathStats(0.70f, 0.90f)).speedMultiplier());
        assertEquals(1.10f, resolveWithFreshDefaults("minecraft:gravel", new PathStats(1.10f, 1.00f)).speedMultiplier());
    }

    private static PathStats resolveWithFreshDefaults(String id, PathStats bundled) {
        Optional<PathStats> legacy = PathEvaluator.legacyPathStats(
                id,
                Config.POOR_PATH.getDefault(), Config.NORMAL_PATH.getDefault(), Config.GREAT_PATH.getDefault(),
                Config.POOR_MULTIPLIER.getDefault(), Config.NORMAL_MULTIPLIER.getDefault(), Config.GREAT_MULTIPLIER.getDefault());
        return PathEvaluator.resolveFallbackPathStats(Optional.empty(), legacy, Optional.of(bundled)).orElseThrow();
    }
}
