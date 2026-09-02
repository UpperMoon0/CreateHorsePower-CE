package net.steampn.createhorsepower.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the actual NeoForge precedence path: exact TFC Data Map values must replace tier-tag defaults. */
public class TfcWorkerProfilePrecedenceTest {
    private static JsonObject values() throws Exception {
        var stream = TfcWorkerProfilePrecedenceTest.class.getResourceAsStream(
                "/data/createhorsepower/data_maps/entity_type/worker_stats.json");
        assertNotNull(stream, "generated worker_stats data map must be on the test classpath");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("values");
        }
    }

    @Test
    void tfcExactProfilesReplaceGenericTierValueAndAreOptional() throws Exception {
        JsonObject values = values();
        assertExact(values, "tfc:horse", 5.0f, 600.0f);
        assertExact(values, "tfc:donkey", 4.0f, 650.0f);
        assertExact(values, "tfc:mule", 4.5f, 700.0f);
        assertExact(values, "tfc:cow", 3.0f, 300.0f);
        assertExact(values, "tfc:pig", 3.5f, 200.0f);
        assertExact(values, "tfc:sheep", 3.0f, 180.0f);
        assertExact(values, "tfc:dromedary_camel", 4.0f, 750.0f);
        assertExact(values, "tfc:bactrian_camel", 4.0f, 750.0f);
    }

    private static void assertExact(JsonObject values, String id, float rpm, float stress) {
        JsonObject entry = values.getAsJsonObject(id);
        assertNotNull(entry, "missing exact optional profile for " + id);
        assertTrue(entry.get("replace").getAsBoolean(), id + " must replace the generic tier-tag default");
        assertTrue(entry.has("neoforge:conditions"), id + " must be guarded when TFC is absent");
        JsonObject value = entry.getAsJsonObject("value");
        assertEquals(rpm, value.get("rpm").getAsFloat(), 0.001f);
        assertEquals(stress, value.get("stress").getAsFloat(), 0.001f);
    }
}
