package net.steampn.createhorsepower.test;

import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import net.steampn.createhorsepower.compat.kubejs.WorkerProfilesKubeEvent;
import net.steampn.createhorsepower.content.stats.WorkerProfileRegistry;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeJSWorkerMachineOverrideTest {
    @AfterEach
    void clear() {
        KubeJSProfileRegistry.clear();
        WorkerProfileRegistry.clear();
    }

    @Test
    void camelCaseMachineOverrideParsesWithFieldInheritance() {
        WorkerStats datapack = WorkerStats.builder().rpm(3.0f).stress(300.0f)
                .machine("createhorsepower:horse_crank", new net.steampn.createhorsepower.content.stats.WorkerStatsOverride(
                        java.util.Optional.of(4.0f), java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()))
                .build();
        WorkerProfileRegistry.replace(List.of(new WorkerProfileRegistry.Entry(
                ResourceLocation.parse("example:horse"), ResourceLocation.parse("minecraft:horse"), 100, datapack)));

        new WorkerProfilesKubeEvent().add("minecraft:horse", Map.of(
                "rpm", 6.0,
                "stress", 700.0,
                "machines", Map.of("createhorsepower:horse_crank", Map.of(
                        "rpm", 8.0,
                        "movementRadius", 2.0,
                        "requiresTamed", true
                ))
        ));

        WorkerStats selected = KubeJSProfileRegistry.getWorker(ResourceLocation.parse("minecraft:horse")).orElseThrow();
        assertEquals(6.0f, selected.baseRpm());
        WorkerStats machine = selected.forMachine("createhorsepower:horse_crank");
        assertEquals(8.0f, machine.baseRpm());
        assertEquals(700.0f, machine.stressCapacity());
        assertEquals(2.0f, machine.movementRadius());
        assertTrue(machine.requiresTamed());
    }

    @Test
    void snakeCaseOrUnknownNestedMachineKeysFailInsteadOfSilentlyNoOping() {
        WorkerProfilesKubeEvent event = new WorkerProfilesKubeEvent();
        assertThrows(IllegalArgumentException.class, () -> event.add("minecraft:horse", Map.of(
                "machines", Map.of("createhorsepower:horse_crank", Map.of("movement_radius", 2.0))
        )));
        assertThrows(IllegalArgumentException.class, () -> event.add("minecraft:horse", Map.of(
                "movement_radius", 2.0
        )));
    }
}
