package net.steampn.createhorsepower.content.path;

import net.steampn.createhorsepower.content.stats.PathStats;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void evaluationModesRemainDistinctAndStressPolicyOnlyNeutralizesStress() {
        PathStats normal = new PathStats(1.00f, 0.80f);
        PathStats custom = new PathStats(1.40f, 1.05f);
        PathStats great = new PathStats(2.00f, 1.20f);
        PathStats[] mixed = {normal, custom, great};

        PathEvaluator.Result legacyEnabled = PathEvaluator.evaluateResolvedPathStats(
                mixed, PathEvaluationMode.LEGACY, 1.0, 0.5f, 1.25f, 2.0f, true);
        PathEvaluator.Result worstEnabled = PathEvaluator.evaluateResolvedPathStats(
                mixed, PathEvaluationMode.WORST_BLOCK, 1.0, 0.5f, 1.25f, 2.0f, true);
        PathEvaluator.Result weightedEnabled = PathEvaluator.evaluateResolvedPathStats(
                mixed, PathEvaluationMode.WEIGHTED_AVERAGE, 1.0, 0.5f, 1.25f, 2.0f, true);

        // These expectations intentionally differ by mode so broken dispatch
        // cannot alias every branch and still satisfy the regression.
        assertEquals(1.25f, legacyEnabled.speedMultiplier(), 0.0001f);
        assertEquals(1.00f, legacyEnabled.stressMultiplier(), 0.0001f);

        assertEquals(1.00f, worstEnabled.speedMultiplier(), 0.0001f);
        assertEquals(0.80f, worstEnabled.stressMultiplier(), 0.0001f);

        assertEquals((1.00f + 1.40f + 2.00f) / 3.0f,
                weightedEnabled.speedMultiplier(), 0.0001f);
        assertEquals((0.80f + 1.05f + 1.20f) / 3.0f,
                weightedEnabled.stressMultiplier(), 0.0001f);

        for (PathEvaluationMode mode : PathEvaluationMode.values()) {
            PathEvaluator.Result enabled = PathEvaluator.evaluateResolvedPathStats(
                    mixed, mode, 1.0, 0.5f, 1.25f, 2.0f, true);
            PathEvaluator.Result disabled = PathEvaluator.evaluateResolvedPathStats(
                    mixed, mode, 1.0, 0.5f, 1.25f, 2.0f, false);

            assertTrue(enabled.isValid(), mode + " enabled path should be valid");
            assertTrue(disabled.isValid(), mode + " disabled path should remain valid");
            assertEquals(enabled.speedMultiplier(), disabled.speedMultiplier(), 0.0001f,
                    mode + " stress policy must not change RPM");
            assertEquals(1.00f, disabled.stressMultiplier(), 0.0001f,
                    mode + " disabled policy should neutralize only final path stress");
            assertEquals(enabled.validBlocks(), disabled.validBlocks());
            assertEquals(enabled.invalidBlocks(), disabled.invalidBlocks());
            assertEquals(enabled.totalBlocks(), disabled.totalBlocks());
            assertEquals(enabled.efficiencyPercent(), disabled.efficiencyPercent());
        }
    }

    @Test
    void mixedTrackWithUnsupportedTilesLosesOnlyItsMissingContribution() {
        PathStats[] track = {
                new PathStats(1.0f, 1.0f),
                new PathStats(2.0f, 1.2f),
                null,
                null
        };
        PathEvaluator.Result result = PathEvaluator.evaluateResolvedPathStats(
                track, PathEvaluationMode.WEIGHTED_AVERAGE,
                0.0, 0.5f, 1.0f, 2.0f, true);

        assertTrue(result.isValid());
        assertEquals(2, result.validBlocks());
        assertEquals(2, result.invalidBlocks());
        assertEquals(4, result.totalBlocks());
        assertEquals(0.75f, result.speedMultiplier(), 0.0001f);
        assertEquals(0.55f, result.stressMultiplier(), 0.0001f);
        assertEquals(75, result.efficiencyPercent());

        PathEvaluator.Result stressDisabled = PathEvaluator.evaluateResolvedPathStats(
                track, PathEvaluationMode.WEIGHTED_AVERAGE,
                0.0, 0.5f, 1.0f, 2.0f, false);
        assertEquals(0.75f, stressDisabled.speedMultiplier(), 0.0001f);
        assertEquals(1.0f, stressDisabled.stressMultiplier(), 0.0001f);
    }

    @Test
    void explicitStrictCoverageStillWorksAndEmptyTracksNeverRun() {
        PathStats[] partial = {PathStats.NORMAL, null};
        assertFalse(PathEvaluator.evaluateResolvedPathStats(
                partial, PathEvaluationMode.WEIGHTED_AVERAGE,
                1.0, 0.5f, 1.0f, 2.0f, true).isValid());
        assertTrue(PathEvaluator.evaluateResolvedPathStats(
                partial, PathEvaluationMode.WEIGHTED_AVERAGE,
                0.5, 0.5f, 1.0f, 2.0f, true).isValid());
        for (PathEvaluationMode mode : PathEvaluationMode.values()) {
            PathEvaluator.Result empty = PathEvaluator.evaluateResolvedPathStats(
                    new PathStats[] {null, null}, mode,
                    0.0, 0.5f, 1.0f, 2.0f, true);
            assertFalse(empty.isValid());
            assertEquals(0, empty.efficiencyPercent());
        }
    }

    @Test
    void everyModeHonorsZeroEfficiencyFromUnsupportedSolidTiles() {
        PathStats[] sparse = {PathStats.NORMAL, null, null, null};
        PathEvaluator.Result weighted = PathEvaluator.evaluateResolvedPathStats(
                sparse, PathEvaluationMode.WEIGHTED_AVERAGE,
                0.0, 0.5f, 1.0f, 2.0f, true);
        PathEvaluator.Result worst = PathEvaluator.evaluateResolvedPathStats(
                sparse, PathEvaluationMode.WORST_BLOCK,
                0.0, 0.5f, 1.0f, 2.0f, true);
        PathEvaluator.Result legacy = PathEvaluator.evaluateResolvedPathStats(
                sparse, PathEvaluationMode.LEGACY,
                0.0, 0.5f, 1.0f, 2.0f, true);

        assertEquals(0.25f, weighted.speedMultiplier(), 0.0001f);
        assertEquals(0.25f, weighted.stressMultiplier(), 0.0001f);
        assertEquals(0.00f, worst.speedMultiplier(), 0.0001f);
        assertEquals(0.00f, worst.stressMultiplier(), 0.0001f);
        assertEquals(0.25f, legacy.speedMultiplier(), 0.0001f);
        assertEquals(0.25f, legacy.stressMultiplier(), 0.0001f);

        for (PathEvaluationMode mode : PathEvaluationMode.values()) {
            PathEvaluator.Result disabled = PathEvaluator.evaluateResolvedPathStats(
                    sparse, mode, 0.0, 0.5f, 1.0f, 2.0f, false);
            assertEquals(1.0f, disabled.stressMultiplier(), 0.0001f,
                    mode + " disabling stress scaling only neutralizes stress");
            assertTrue(disabled.speedMultiplier() <= 0.25f,
                    mode + " must not restore full RPM on sparse paths");
        }
    }

    @Test
    void pathStressScalingPolicyDoesNotBypassCoverage() {
        PathStats great = new PathStats(2.0f, 1.10f);
        PathStats[] incomplete = {great, null};

        for (PathEvaluationMode mode : PathEvaluationMode.values()) {
            PathEvaluator.Result enabled = PathEvaluator.evaluateResolvedPathStats(
                    incomplete, mode, 1.0, 0.5f, 1.0f, 2.0f, true);
            PathEvaluator.Result disabled = PathEvaluator.evaluateResolvedPathStats(
                    incomplete, mode, 1.0, 0.5f, 1.0f, 2.0f, false);

            assertFalse(enabled.isValid(), mode + " incomplete path must fail full coverage");
            assertFalse(disabled.isValid(), mode + " stress policy must not bypass coverage");
            assertEquals(1, enabled.validBlocks());
            assertEquals(1, enabled.invalidBlocks());
            assertEquals(2, enabled.totalBlocks());
            assertEquals(enabled.validBlocks(), disabled.validBlocks());
            assertEquals(enabled.invalidBlocks(), disabled.invalidBlocks());
            assertEquals(enabled.totalBlocks(), disabled.totalBlocks());
        }
    }
}
