package net.steampn.createhorsepower.test;

import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KubeJSProfileRegistryTest {

    @BeforeEach
    void setup() {
        KubeJSProfileRegistry.clear();
    }

    @Test
    @DisplayName("KubeJSProfileRegistry correctly stores and retrieves worker profiles")
    void testWorkerProfileRegistry() {
        ResourceLocation id = ResourceLocation.parse("minecraft:cow");
        WorkerStats stats = WorkerStats.builder().rpm(3.0f).stress(350.0f).build();

        KubeJSProfileRegistry.registerWorker(id, stats);

        var registered = KubeJSProfileRegistry.getWorker(id);
        assertTrue(registered.isPresent());
        assertEquals(3.0f, registered.get().baseRpm());
        assertEquals(350.0f, registered.get().stressCapacity());
    }

    @Test
    @DisplayName("KubeJSProfileRegistry correctly stores and retrieves path block profiles")
    void testPathProfileRegistry() {
        ResourceLocation id = ResourceLocation.parse("minecraft:smooth_stone");
        PathStats stats = PathStats.of(1.3f, 1.2f);

        KubeJSProfileRegistry.registerPathBlock(id, stats);

        var registered = KubeJSProfileRegistry.getPathBlock(id);
        assertTrue(registered.isPresent());
        assertEquals(1.3f, registered.get().speedMultiplier());
        assertEquals(1.2f, registered.get().stressMultiplier());
    }
}
