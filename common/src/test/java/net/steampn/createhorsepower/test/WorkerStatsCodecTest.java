package net.steampn.createhorsepower.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WorkerStatsCodecTest {

    @Test
    @DisplayName("WorkerStats codec encodes and decodes properly with custom values")
    void testWorkerStatsCodec() {
        String json = """
                {
                    "rpm": 6.0,
                    "stress": 800.0,
                    "movement_radius": 3.0,
                    "speed_scaling": 0.8,
                    "speed_reference": 0.25,
                    "health_scaling": 0.3,
                    "health_reference": 24.0,
                    "requires_tamed": true,
                    "allow_baby": false
                }
                """;

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        var result = WorkerStats.CODEC.parse(JsonOps.INSTANCE, jsonObject);

        assertTrue(result.result().isPresent(), "WorkerStats should decode cleanly");
        WorkerStats stats = result.result().get();

        assertEquals(6.0f, stats.baseRpm());
        assertEquals(800.0f, stats.stressCapacity());
        assertEquals(3.0f, stats.movementRadius());
        assertEquals(0.8f, stats.speedScaling());
        assertEquals(0.25f, stats.speedReference());
        assertEquals(0.3f, stats.healthScaling());
        assertEquals(24.0f, stats.healthReference());
        assertTrue(stats.requiresTamed());
        assertFalse(stats.allowBaby());
    }

    @Test
    @DisplayName("WorkerStats codec rejects negative values")
    void testWorkerStatsRejectsNegative() {
        String json = """
                {
                    "rpm": -5.0,
                    "stress": -100.0
                }
                """;

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        var result = WorkerStats.CODEC.parse(JsonOps.INSTANCE, jsonObject);

        assertTrue(result.error().isPresent(), "WorkerStats must reject negative rpm or stress");
    }

    @Test
    @DisplayName("PathStats codec encodes and decodes valid multipliers")
    void testPathStatsCodec() {
        String json = """
                {
                    "speed_multiplier": 1.25,
                    "stress_multiplier": 1.10
                }
                """;

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        var result = PathStats.CODEC.parse(JsonOps.INSTANCE, jsonObject);

        assertTrue(result.result().isPresent());
        PathStats stats = result.result().get();
        assertEquals(1.25f, stats.speedMultiplier());
        assertEquals(1.10f, stats.stressMultiplier());
    }

    @Test
    @DisplayName("PathStats codec rejects negative multipliers")
    void testPathStatsRejectsNegative() {
        String json = """
                {
                    "speed_multiplier": -1.0
                }
                """;

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        var result = PathStats.CODEC.parse(JsonOps.INSTANCE, jsonObject);

        assertTrue(result.error().isPresent(), "PathStats must reject negative speed multiplier");
    }

    @Test
    @DisplayName("WorkerStats.Builder rejects non-finite or out-of-range values")
    void testWorkerStatsBuilderValidation() {
        assertThrows(IllegalArgumentException.class, () -> WorkerStats.builder().rpm(-1.0f));
        assertThrows(IllegalArgumentException.class, () -> WorkerStats.builder().movementRadius(0.1f));
        assertEquals(0.5f, WorkerStats.builder().movementRadius(0.5f).build().movementRadius());
        assertEquals(6.0f, WorkerStats.builder().movementRadius(6.0f).build().movementRadius());
        assertThrows(IllegalArgumentException.class, () -> WorkerStats.builder().movementRadius(6.01f));
        assertThrows(IllegalArgumentException.class, () -> WorkerStats.builder().movementRadius(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> WorkerStats.builder().speedReference(0.0f));
    }

    @Test
    @DisplayName("WorkerStats codec rejects movement radii beyond the vanilla leash-safe range")
    void testWorkerStatsCodecRejectsExcessiveRadius() {
        JsonObject jsonObject = JsonParser.parseString("{\"movement_radius\": 6.01}").getAsJsonObject();
        var result = WorkerStats.CODEC.parse(JsonOps.INSTANCE, jsonObject);

        assertTrue(result.error().isPresent(), "movement_radius above 6.0 must be rejected");
    }

    @Test
    @DisplayName("PathStats rejects non-finite or negative values")
    void testPathStatsValidation() {
        assertThrows(IllegalArgumentException.class, () -> PathStats.of(Float.NaN, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> PathStats.of(Float.POSITIVE_INFINITY, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> PathStats.of(1.0f, -0.5f));
        assertThrows(IllegalArgumentException.class, () -> new PathStats(Float.NaN, 1.0f));
    }

    @Test
    @DisplayName("Default WorkerStats has requiresTamed set to false")
    void testDefaultWorkerStatsRequiresTamedFalse() {
        assertFalse(WorkerStats.DEFAULT.requiresTamed(), "Default WorkerStats must have requiresTamed = false");
        String horseJson = """
                {
                    "rpm": 5.0,
                    "stress": 600.0,
                    "speed_scaling": 0.75,
                    "health_scaling": 0.25
                }
                """;
        JsonObject jsonObject = JsonParser.parseString(horseJson).getAsJsonObject();
        var result = WorkerStats.CODEC.parse(JsonOps.INSTANCE, jsonObject);
        assertTrue(result.result().isPresent());
        assertFalse(result.result().get().requiresTamed(), "Omitted requires_tamed must default to false");
    }

    @Test
    @DisplayName("Machine overrides inherit omitted base fields and replace exact fields")
    void testMachineOverrideInheritance() {
        JsonObject jsonObject = JsonParser.parseString("""
                {
                  "rpm": 5.0,
                  "stress": 600.0,
                  "movement_radius": 3.0,
                  "requires_tamed": true,
                  "machines": {
                    "createhorsepower:horse_crank": {
                      "rpm": 7.0,
                      "movement_radius": 2.0,
                      "allow_baby": true
                    }
                  }
                }
                """).getAsJsonObject();

        WorkerStats base = WorkerStats.CODEC.parse(JsonOps.INSTANCE, jsonObject).result().orElseThrow();
        WorkerStats effective = base.forMachine("createhorsepower:horse_crank");

        assertEquals(7.0f, effective.baseRpm());
        assertEquals(600.0f, effective.stressCapacity(), "omitted override fields inherit base stats");
        assertEquals(2.0f, effective.movementRadius());
        assertTrue(effective.requiresTamed(), "base boolean is inherited");
        assertTrue(effective.allowBaby(), "explicit machine boolean wins");
        assertEquals(base, base.forMachine("example:other_machine"), "missing machine override must be a no-op");
        assertTrue(base.hasMachineOverride("createhorsepower:horse_crank"));
    }

    @Test
    @DisplayName("Machine override codec rejects invalid fields and malformed machine ids")
    void testMachineOverrideValidation() {
        var invalidRadius = WorkerStats.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"machines":{"createhorsepower:horse_crank":{"movement_radius":6.01}}}
                """).getAsJsonObject());
        assertTrue(invalidRadius.error().isPresent(), invalidRadius.toString());

        var invalidId = WorkerStats.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"machines":{"not namespaced":{"rpm":5.0}}}
                """).getAsJsonObject());
        assertTrue(invalidId.error().isPresent());
    }}
