package net.steampn.createhorsepower.content.attachment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.utils.CHPTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Atomically replaced registry populated by datapack reloads. */
public final class AttachmentProfileRegistry {
    private static volatile List<AttachmentProfile> profiles = List.of();

    private AttachmentProfileRegistry() {}

    public static void replace(List<AttachmentProfile> loaded) {
        ArrayList<AttachmentProfile> sorted = new ArrayList<>(loaded);
        sorted.sort(Comparator.comparingInt(AttachmentProfile::priority).reversed()
                .thenComparing(p -> p.id().toString()));
        profiles = List.copyOf(sorted);
    }

    public static List<AttachmentProfile> all() { return profiles; }

    public static Optional<AttachmentProfile> byId(String id) {
        return profiles.stream().filter(profile -> profile.id().toString().equals(id)).findFirst();
    }

    public static Optional<AttachmentProfile> explicit(ItemStack stack) {
        AttachmentProfile tagMatch = null;
        for (AttachmentProfile profile : profiles) {
            if (profile.matchesExactItem(stack)) return Optional.of(profile);
            if (tagMatch == null && profile.matchesItem(stack)) tagMatch = profile;
        }
        return Optional.ofNullable(tagMatch);
    }

    public static Optional<AttachmentProfile> resolve(ItemStack stack) {
        Optional<AttachmentProfile> explicit = explicit(stack);
        if (explicit.isPresent()) return explicit;
        if (stack.is(CHPTags.Items.ATTACHMENT_ITEMS) || stack.is(CHPTags.Items.WORKER_LEASHES)) {
            return Optional.of(legacyVanilla());
        }
        return Optional.empty();
    }

    public static AttachmentProfile legacyVanilla() {
        return new AttachmentProfile(
                CHPApi.modId("legacy_vanilla_leash"), Integer.MIN_VALUE,
                Set.of(), Set.of(CHPApi.modId("attachment_items")), AttachmentMode.VANILLA_LEASH,
                6.0f, Set.of(), Set.of(), Set.of(), false, true, 1.0f
        );
    }

    public static AttachmentMode persistedModeOrDefault(String serialized) {
        try { return AttachmentMode.parse(serialized); }
        catch (RuntimeException ignored) { return AttachmentMode.VANILLA_LEASH; }
    }
}
