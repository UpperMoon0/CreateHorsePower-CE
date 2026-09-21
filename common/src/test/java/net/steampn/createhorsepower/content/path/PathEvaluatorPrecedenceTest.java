package net.steampn.createhorsepower.content.path;

import net.steampn.createhorsepower.content.stats.PathStats;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PathEvaluatorPrecedenceTest {

    @Test
    void platformOverrideWinsPackLegacyAndBundledDefaults() {
        PathStats platform = new PathStats(1.4f, 1.3f);
        PathStats legacy = new PathStats(0.5f, 0.9f);
        PathStats bundled = new PathStats(0.7f, 0.9f);

        Optional<PathStats> result = PathEvaluator.resolveFallbackPathStats(
                Optional.of(platform), Optional.of(legacy), Optional.of(bundled));

        assertSame(platform, result.orElseThrow());
    }

    @Test
    void packLegacyPathWinsBundledDefaults() {
        PathStats legacy = new PathStats(0.5f, 0.9f);
        PathStats bundled = new PathStats(0.7f, 0.9f);

        Optional<PathStats> result = PathEvaluator.resolveFallbackPathStats(
                Optional.empty(), Optional.of(legacy), Optional.of(bundled));

        assertSame(legacy, result.orElseThrow());
        assertEquals(0.5f, result.orElseThrow().speedMultiplier());
    }

    @Test
    void bundledDefaultRemainsFallbackWhenPackDoesNotOverride() {
        PathStats bundled = new PathStats(1.1f, 1.0f);

        Optional<PathStats> result = PathEvaluator.resolveFallbackPathStats(
                Optional.empty(), Optional.empty(), Optional.of(bundled));

        assertSame(bundled, result.orElseThrow());
    }

    @Test
    void pathStressScalingPolicyPreservesExistingMultipliersWhenEnabled() {
        assertEquals(0.90f, PathEvaluator.applyPathStressScalingPolicy(0.90f, true));
        assertEquals(1.00f, PathEvaluator.applyPathStressScalingPolicy(1.00f, true));
        assertEquals(1.10f, PathEvaluator.applyPathStressScalingPolicy(1.10f, true));
    }

    @Test
    void pathStressScalingPolicyCanNeutralizePathStressWithoutChangingSpeedPolicy() {
        assertEquals(1.00f, PathEvaluator.applyPathStressScalingPolicy(0.90f, false));
        assertEquals(1.00f, PathEvaluator.applyPathStressScalingPolicy(1.00f, false));
        assertEquals(1.00f, PathEvaluator.applyPathStressScalingPolicy(1.10f, false));
    }
}
