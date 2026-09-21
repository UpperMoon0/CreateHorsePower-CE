package net.steampn.createhorsepower.test;

import net.steampn.createhorsepower.compat.OptionalIntegrations;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalIntegrationsTest {

    @Test
    @DisplayName("When KubeJS is absent, fireBeforeAttach must return true to allow vanilla attachment")
    void testFireBeforeAttachWithoutKubeJs() {
        boolean result = OptionalIntegrations.INSTANCE.fireBeforeAttach(null, null, null, null, WorkerResolver.ResolvedWorker.INVALID);
        assertTrue(result, "Attachment must succeed when KubeJS is not installed");
    }

    @Test
    @DisplayName("When KubeJS is absent, fireBeforeWorkStart must return true to allow crank generation")
    void testFireBeforeWorkStartWithoutKubeJs() {
        boolean result = OptionalIntegrations.INSTANCE.fireBeforeWorkStart(null, null, null);
        assertTrue(result, "Work start must proceed when KubeJS is not installed");
    }

    @Test
    @DisplayName("When KubeJS is absent, path evaluation must preserve the evaluated absolute multipliers")
    void testFirePathEvaluatedWithoutKubeJsPreservesEvaluatedValues() {
        PathEvaluator.Result evaluated = new PathEvaluator.Result(
                true, 2.0f, 1.10f, 8, 0, 8, 200);

        float[] result = OptionalIntegrations.INSTANCE.firePathEvaluated(null, null, evaluated);

        assertArrayEquals(new float[]{2.0f, 1.10f}, result, 0.0001f,
                "No-KubeJS fallback must return the evaluated path values unchanged");
    }
}
