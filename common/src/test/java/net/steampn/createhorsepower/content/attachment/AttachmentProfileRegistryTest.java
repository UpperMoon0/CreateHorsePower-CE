package net.steampn.createhorsepower.content.attachment;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentProfileRegistryTest {

    private static ResourceLocation id(String value) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new IllegalArgumentException(value);
        return result;
    }

    @AfterEach
    void resetRegistry() {
        AttachmentProfileRegistry.replace(List.of());
    }

    @Test
    void profilesUsePriorityThenStableIdTieBreak() {
        AttachmentProfile lowPriority = profile("example:a", 5, AttachmentMode.HARNESS);
        AttachmentProfile highPriority = profile("example:b", 10, AttachmentMode.YOKE);

        AttachmentProfileRegistry.replace(List.of(lowPriority, highPriority));
        assertEquals(List.of(highPriority, lowPriority), AttachmentProfileRegistry.all());

        AttachmentProfile lexicalFirst = profile("example:a", 10, AttachmentMode.HARNESS);
        AttachmentProfileRegistry.replace(List.of(highPriority, lexicalFirst));
        assertEquals(List.of(lexicalFirst, highPriority), AttachmentProfileRegistry.all(),
                "equal-priority profiles must use profile id as a stable tie break");
    }

    @Test
    void machineAllowlistAndValidationAreDeterministic() {
        ResourceLocation profileId = id("example:profile");
        ResourceLocation leadId = id("minecraft:lead");
        ResourceLocation horseCrank = id("createhorsepower:horse_crank");
        ResourceLocation otherMachine = id("example:other");

        AttachmentProfile restricted = new AttachmentProfile(
                profileId, 0, Set.of(leadId), Set.of(), AttachmentMode.VIRTUAL_TETHER,
                5.0f, Set.of(), Set.of(), Set.of(horseCrank), true, false, 1.25f);

        assertTrue(restricted.supportsMachine(horseCrank));
        assertFalse(restricted.supportsMachine(otherMachine));

        assertThrows(IllegalArgumentException.class, () -> new AttachmentProfile(
                profileId, 0, Set.of(), Set.of(), AttachmentMode.HARNESS,
                3.0f, Set.of(), Set.of(), Set.of(), false, true, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new AttachmentProfile(
                profileId, 0, Set.of(leadId), Set.of(), AttachmentMode.HARNESS,
                6.01f, Set.of(), Set.of(), Set.of(), false, true, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new AttachmentProfile(
                profileId, 0, Set.of(leadId), Set.of(), AttachmentMode.HARNESS,
                3.0f, Set.of(), Set.of(), Set.of(), false, true, 0.0f));
    }

    private static AttachmentProfile profile(String profileId, int priority, AttachmentMode mode) {
        return new AttachmentProfile(
                id(profileId), priority, Set.of(id("minecraft:lead")), Set.of(), mode,
                3.0f, Set.of(), Set.of(), Set.of(), false, true, 1.0f);
    }
}
