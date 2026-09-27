package net.steampn.createhorsepower.content.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentProfileReloadListenerTest {

    @AfterEach
    void clearRegistry() {
        AttachmentProfileRegistry.replace(List.of());
    }

    @Test
    void omittedFieldsUseDocumentedDefaults() {
        AttachmentProfile profile = parse("example:minimal", """
                {"items":["minecraft:lead"]}
                """);
        assertEquals(0, profile.priority());
        assertEquals(AttachmentMode.VANILLA_LEASH, profile.mode());
        assertEquals(6.0f, profile.maxWorkingRadius());
        assertFalse(profile.consumeOnAttach());
        assertTrue(profile.dropOnDetach());
        assertEquals(1.0f, profile.outputMultiplier());
        assertTrue(profile.workers().isEmpty());
        assertTrue(profile.workerTags().isEmpty());
        assertTrue(profile.machines().isEmpty());
    }

    @Test
    void exactAndTagSelectorsAndAllModesParse() {
        for (AttachmentMode mode : AttachmentMode.values()) {
            AttachmentProfile profile = parse("example:" + mode.serializedName(), """
                    {
                      "priority": 7,
                      "items": ["minecraft:saddle"],
                      "item_tags": ["createhorsepower:attachment_items"],
                      "mode": "%s",
                      "max_working_radius": 4.5,
                      "workers": ["minecraft:horse"],
                      "worker_tags": ["createhorsepower:workers_large"],
                      "machines": ["createhorsepower:horse_crank"],
                      "consume_on_attach": true,
                      "drop_on_detach": false,
                      "output_multiplier": 1.25
                    }
                    """.formatted(mode.serializedName()));
            assertEquals(mode, profile.mode());
            assertTrue(profile.items().contains(ResourceLocation.parse("minecraft:saddle")));
            assertTrue(profile.itemTags().contains(ResourceLocation.parse("createhorsepower:attachment_items")));
            assertTrue(profile.workers().contains(ResourceLocation.parse("minecraft:horse")));
            assertTrue(profile.workerTags().contains(ResourceLocation.parse("createhorsepower:workers_large")));
            assertTrue(profile.machines().contains(ResourceLocation.parse("createhorsepower:horse_crank")));
            assertEquals(4.5f, profile.maxWorkingRadius());
            assertTrue(profile.consumeOnAttach());
            assertFalse(profile.dropOnDetach());
            assertEquals(1.25f, profile.outputMultiplier());
        }
    }

    @Test
    void malformedIdsTypesAndModesFailTheReloadBoundary() {
        assertThrows(IllegalStateException.class, () -> parseAll("example:bad_id", """
                {"items":["not_namespaced"]}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:bad_type", """
                {"items":"minecraft:lead"}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:bad_mode", """
                {"items":["minecraft:lead"],"mode":"rope_magic"}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:bad_radius", """
                {"items":["minecraft:lead"],"max_working_radius":6.01}
                """));
        assertThrows(IllegalStateException.class, () -> parseAll("example:unknown_field", """
                {"items":["minecraft:lead"],"movementRadius":2.0}
                """));
    }

    @Test
    void parsedJsonActuallyFlowsIntoRuntimeResolution() {
        List<AttachmentProfile> loaded = parseAll("example:saddle_harness", """
                {
                  "items":["minecraft:saddle"],
                  "mode":"harness",
                  "priority":50,
                  "consume_on_attach":true,
                  "output_multiplier":1.5
                }
                """);
        AttachmentProfileRegistry.replace(loaded);

        AttachmentProfile resolved = AttachmentProfileRegistry.byId("example:saddle_harness").orElseThrow();
        assertEquals(ResourceLocation.parse("example:saddle_harness"), resolved.id());
        assertEquals(AttachmentMode.HARNESS, resolved.mode());
        assertTrue(resolved.consumeOnAttach());
        assertEquals(1.5f, resolved.outputMultiplier());
    }

    private static AttachmentProfile parse(String id, String json) {
        return AttachmentProfileReloadListener.decode(
                ResourceLocation.parse(id), JsonParser.parseString(json).getAsJsonObject());
    }

    private static List<AttachmentProfile> parseAll(String id, String json) {
        Map<ResourceLocation, JsonElement> objects = Map.of(
                ResourceLocation.parse(id), JsonParser.parseString(json));
        return AttachmentProfileReloadListener.parseAll(objects);
    }
}
