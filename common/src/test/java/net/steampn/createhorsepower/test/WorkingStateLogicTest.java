package net.steampn.createhorsepower.test;

import net.minecraft.core.BlockPos;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.PathStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WorkingStateLogicTest {

    @Test
    @DisplayName("PathEvaluator handles empty or null levels safely")
    void testPathEvaluatorNullSafety() {
        PathEvaluator.Result result = PathEvaluator.evaluate(null, null, null);
        assertFalse(result.isValid(), "Null level or offsets should return invalid result");
        assertEquals(0.0f, result.speedMultiplier());
    }

    @Test
    @DisplayName("PathStats of helper returns valid stats")
    void testPathStatsOf() {
        PathStats stats = PathStats.of(1.5f, 1.2f);
        assertEquals(1.5f, stats.speedMultiplier());
        assertEquals(1.2f, stats.stressMultiplier());
    }

    @Test
    @DisplayName("generateOffsetsForRadius generates appropriate track blocks for different radii")
    void testGenerateOffsetsForRadius() {
        BlockPos[] standardOffsets = HorseCrankEngine.generateOffsetsForRadius(2.5f);
        assertTrue(standardOffsets.length > 0, "Standard 2.5 radius must generate track offsets");

        BlockPos[] smallOffsets = HorseCrankEngine.generateOffsetsForRadius(1.5f);
        assertTrue(smallOffsets.length > 0, "Small 1.5 radius must generate track offsets");

        BlockPos[] largeOffsets = HorseCrankEngine.generateOffsetsForRadius(4.0f);
        assertTrue(largeOffsets.length > standardOffsets.length, "Larger radius must generate more track offsets");
    }
}
