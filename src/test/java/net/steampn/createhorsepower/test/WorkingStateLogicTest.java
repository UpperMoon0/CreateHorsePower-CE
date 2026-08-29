package net.steampn.createhorsepower.test;

import net.steampn.createhorsepower.content.path.PathEvaluationMode;
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
    @DisplayName("PathStats of helper handles clamping")
    void testPathStatsClamping() {
        PathStats stats = PathStats.of(-0.5f, -1.0f);
        assertEquals(0.0f, stats.speedMultiplier());
        assertEquals(0.0f, stats.stressMultiplier());
    }
}
