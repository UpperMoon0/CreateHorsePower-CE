package net.steampn.createhorsepower.content.attachment;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-data reload listener for namespaced createhorsepower/attachment_profiles JSON resources. */
public final class AttachmentProfileReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "priority", "items", "item_tags", "mode", "max_working_radius",
            "workers", "worker_tags", "machines", "consume_on_attach", "drop_on_detach", "output_multiplier"
    );

    public AttachmentProfileReloadListener() {
        super(GSON, "createhorsepower/attachment_profiles");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        AttachmentProfileRegistry.replace(parseAll(objects));
    }

    static List<AttachmentProfile> parseAll(Map<ResourceLocation, JsonElement> objects) {
        List<AttachmentProfile> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                loaded.add(decode(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Invalid Create Horse Power attachment profile " + entry.getKey() + ": " + ex.getMessage(), ex);
            }
        }
        return List.copyOf(loaded);
    }

    public static AttachmentProfile decode(ResourceLocation id, JsonObject json) {
        rejectUnknownKeys(json, ALLOWED_KEYS, "attachment profile");
        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        Set<ResourceLocation> items = ids(json, "items");
        Set<ResourceLocation> itemTags = ids(json, "item_tags");
        AttachmentMode mode = AttachmentMode.parse(string(json, "mode", "vanilla_leash"));
        float radius = json.has("max_working_radius") ? json.get("max_working_radius").getAsFloat() : 6.0f;
        Set<ResourceLocation> workers = ids(json, "workers");
        Set<ResourceLocation> workerTags = ids(json, "worker_tags");
        Set<ResourceLocation> machines = ids(json, "machines");
        boolean consume = json.has("consume_on_attach") && json.get("consume_on_attach").getAsBoolean();
        boolean drop = !json.has("drop_on_detach") || json.get("drop_on_detach").getAsBoolean();
        float output = json.has("output_multiplier") ? json.get("output_multiplier").getAsFloat() : 1.0f;
        return new AttachmentProfile(id, priority, items, itemTags, mode, radius, workers, workerTags, machines, consume, drop, output);
    }


    private static void rejectUnknownKeys(JsonObject json, Set<String> allowed, String context) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(context + " has unknown field '" + key + "'");
            }
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static Set<ResourceLocation> ids(JsonObject json, String key) {
        if (!json.has(key)) return Set.of();
        JsonArray array = json.getAsJsonArray(key);
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement element : array) result.add(parseId(element.getAsString()));
        return Set.copyOf(result);
    }

    private static ResourceLocation parseId(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException("resource id must be namespace:path, got " + value);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid resource id: " + value);
        }
        return parsed;
    }
}
