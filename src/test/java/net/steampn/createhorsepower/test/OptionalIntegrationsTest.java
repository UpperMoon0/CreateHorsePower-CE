package net.steampn.createhorsepower.test;

import net.steampn.createhorsepower.compat.OptionalIntegrations;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalIntegrationsTest {

    @Test
    @DisplayName("When KubeJS is absent, fireBeforeAttach must return true to allow vanilla attachment")
    void testFireBeforeAttachWithoutKubeJs() {
        boolean result = OptionalIntegrations.fireBeforeAttach(null, null, null, null, WorkerResolver.ResolvedWorker.INVALID);
        assertTrue(result, "Attachment must succeed when KubeJS is not installed");
    }

    @Test
    @DisplayName("When KubeJS is absent, fireBeforeWorkStart must return true to allow crank generation")
    void testFireBeforeWorkStartWithoutKubeJs() {
        boolean result = OptionalIntegrations.fireBeforeWorkStart(null, null, null);
        assertTrue(result, "Work start must proceed when KubeJS is not installed");
    }
}
