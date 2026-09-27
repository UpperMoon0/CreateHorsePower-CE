package net.steampn.createhorsepower.content.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.compat.kubejs.KubeJSProfileRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkerProfileReloadListenerTest {
    @AfterEach
    void clear() {
        WorkerProfileRegistry.clear();
        KubeJSProfileRegistry.clear();
    }

    @Test
    void sharedJsonParsesMachineOverridesAndFlowsIntoWorkerResolution() {
        WorkerProfileRegistry.Entry entry = parse("example:horse_power", """
                {
                  "entity":"minecraft:horse",
                  "priority":20,
                  "rpm":5.5,
                  "stress":620.0,
                  "movement_radius":3.0,
                  "speed_scaling":0.4,
                  "requires_tamed":true,
                  "machines": {
                    "createhorsepower:horse_crank": {
                      "rpm":7.25,
                      "movement_radius":2.0,
                      "allow_baby":true
                    }
                  }
                }
                """);
        WorkerProfileRegistry.replace(List.of(entry));

        WorkerStats selected = WorkerResolver.selectPackStats(ResourceLocation.parse("minecraft:horse"), java.util.Optional.empty())
                .orElseThrow().stats();
        assertEquals(5.5f, selected.baseRpm());
        assertEquals(620.0f, selected.stressCapacity());
        assertTrue(selected.requiresTamed());
        assertTrue(selected.hasMachineOverride("createhorsepower:horse_crank"));

        WorkerStats machine = selected.forMachine("createhorsepower:horse_crank");
        assertEquals(7.25f, machine.baseRpm());
        assertEquals(620.0f, machine.stressCapacity(), "omitted machine fields inherit base JSON profile");
        assertEquals(2.0f, machine.movementRadius());
        assertTrue(machine.allowBaby());
    }

    @Test
    void priorityThenProfileIdDeterministicallySelectsOneEntityProfile() {
        WorkerProfileRegistry.Entry low = parse("example:a", """
                {"entity":"minecraft:cow","priority":1,"rpm":2.0}
                """);
        WorkerProfileRegistry.Entry high = parse("example:b", """
                {"entity":"minecraft:cow","priority":10,"rpm":8.0}
                """);
        WorkerProfileRegistry.replace(List.of(high, low));
        assertEquals(8.0f, WorkerProfileRegistry.get(ResourceLocation.parse("minecraft:cow")).orElseThrow().baseRpm());
    }

    @Test
    void equalPriorityUsesStableProfileIdTieBreak() {
        WorkerProfileRegistry.Entry z = parse("example:z", """
                {"entity":"minecraft:cow","priority":5,"rpm":9.0}
                """);
        WorkerProfileRegistry.Entry a = parse("example:a", """
                {"entity":"minecraft:cow","priority":5,"rpm":4.0}
                """);
        WorkerProfileRegistry.replace(List.of(z, a));
        assertEquals(4.0f, WorkerProfileRegistry.get(ResourceLocation.parse("minecraft:cow")).orElseThrow().baseRpm());
    }

    @Test
    void kubeJsThenSharedJsonThenPlatformDataUseDocumentedSourcePrecedence() {
        WorkerStats shared = WorkerStats.builder().rpm(3.0f).build();
        WorkerStats platform = WorkerStats.builder().rpm(6.0f).build();
        WorkerStats kube = WorkerStats.builder().rpm(9.0f).build();
        ResourceLocation horse = ResourceLocation.parse("minecraft:horse");
        WorkerProfileRegistry.replace(List.of(new WorkerProfileRegistry.Entry(
                ResourceLocation.parse("example:horse"), horse, 100, shared)));

        WorkerResolver.SelectedProfile selected = WorkerResolver.selectPackStats(horse, java.util.Optional.of(platform)).orElseThrow();
        assertEquals("datapack_json", selected.source());
        assertEquals(3.0f, selected.stats().baseRpm());

        KubeJSProfileRegistry.registerWorker(horse, kube);
        selected = WorkerResolver.selectPackStats(horse, java.util.Optional.of(platform)).orElseThrow();
        assertEquals("kubejs", selected.source());
        assertEquals(9.0f, selected.stats().baseRpm());

        KubeJSProfileRegistry.clear();
        WorkerProfileRegistry.clear();
        selected = WorkerResolver.selectPackStats(horse, java.util.Optional.of(platform)).orElseThrow();
        assertEquals("platform_data", selected.source());
        assertEquals(6.0f, selected.stats().baseRpm());
    }

    @Test
    void malformedEntityUnknownFieldsAndInvalidNestedOverridesFailReload() {
        assertThrows(IllegalStateException.class, () -> parseAll("example:missing_entity", """
                {"rpm":5.0}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:bad_entity", """
                {"entity":"horse","rpm":5.0}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:unknown", """
                {"entity":"minecraft:horse","movementRadius":3.0}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:bad_machine", """
                {"entity":"minecraft:horse","machines":{"createhorsepower:horse_crank":{"movement_radius":6.1}}}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:nested_typo", """
                {"entity":"minecraft:horse","machines":{"createhorsepower:horse_crank":{"movementRadius":2.0}}}
                """));
    }

    private static WorkerProfileRegistry.Entry parse(String id, String json) {
        return WorkerProfileReloadListener.decode(
                ResourceLocation.parse(id), JsonParser.parseString(json).getAsJsonObject());
    }

    private static List<WorkerProfileRegistry.Entry> parseAll(String id, String json) {
        Map<ResourceLocation, JsonElement> objects = Map.of(
                ResourceLocation.parse(id), JsonParser.parseString(json));
        return WorkerProfileReloadListener.parseAll(objects);
    }
}
