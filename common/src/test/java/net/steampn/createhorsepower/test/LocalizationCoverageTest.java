package net.steampn.createhorsepower.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationCoverageTest {

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "block.createhorsepower.horse_crank",
            "config.jade.plugin_createhorsepower.horse_crank_info",
            "itemGroup.createhorsepower",
            "tooltip.createhorsepower.horse_crank.alreadyHasWorker",
            "tooltip.createhorsepower.horse_crank.maximumMobs",
            "tooltip.createhorsepower.horse_crank.notValidWorker",
            "tooltip.createhorsepower.horse_crank.attached",
            "tooltip.createhorsepower.goggles.header",
            "tooltip.createhorsepower.goggles.status.no_worker",
            "tooltip.createhorsepower.goggles.status.stopped_redstone",
            "tooltip.createhorsepower.goggles.status.worker_unloaded",
            "tooltip.createhorsepower.goggles.status.worker_ineligible",
            "tooltip.createhorsepower.goggles.status.invalid_path",
            "tooltip.createhorsepower.goggles.status.vetoed",
            "tooltip.createhorsepower.goggles.status.working",
            "tooltip.createhorsepower.goggles.worker",
            "tooltip.createhorsepower.goggles.path_efficiency",
            "tooltip.createhorsepower.goggles.speed_bonus",
            "tooltip.createhorsepower.goggles.health_bonus",
            "tooltip.createhorsepower.goggles.redstone_mode",
            "tooltip.createhorsepower.redstone_mode.ignore",
            "tooltip.createhorsepower.redstone_mode.high_stops",
            "tooltip.createhorsepower.redstone_mode.high_runs",
            "tooltip.createhorsepower.redstone_mode.changed"
    );

    @Test
    void englishLocalesContainEveryRuntimeTranslationAndStayInParity() {
        JsonObject enUs = load("en_us.json");
        JsonObject enUd = load("en_ud.json");

        for (String key : REQUIRED_KEYS) {
            assertTrue(enUs.has(key), () -> "en_us.json is missing " + key);
            assertTrue(enUd.has(key), () -> "en_ud.json is missing " + key);
        }
        assertEquals(enUs.keySet(), enUd.keySet(), "English locale files must contain matching keys");
    }

    private static JsonObject load(String fileName) {
        String resource = "/assets/createhorsepower/lang/" + fileName;
        InputStream stream = LocalizationCoverageTest.class.getResourceAsStream(resource);
        assertNotNull(stream, () -> "Missing test resource " + resource);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
