package net.steampn.createhorsepower.content.stats;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-data reload listener for createhorsepower/worker_profiles JSON resources. */
public final class WorkerProfileReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "entity", "priority", "rpm", "stress", "movement_radius", "speed_scaling", "speed_reference",
            "health_scaling", "health_reference", "requires_tamed", "allow_baby", "machines"
    );
    private static final Set<String> MACHINE_KEYS = Set.of(
            "rpm", "stress", "movement_radius", "speed_scaling", "speed_reference",
            "health_scaling", "health_reference", "requires_tamed", "allow_baby"
    );

    public WorkerProfileReloadListener() {
        super(GSON, "createhorsepower/worker_profiles");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        WorkerProfileRegistry.replace(parseAll(objects));
    }

    static List<WorkerProfileRegistry.Entry> parseAll(Map<ResourceLocation, JsonElement> objects) {
        List<WorkerProfileRegistry.Entry> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            try {
                loaded.add(decode(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Invalid Create Horse Power worker profile " + entry.getKey() + ": " + ex.getMessage(), ex);
            }
        }
        return List.copyOf(loaded);
    }

    public static WorkerProfileRegistry.Entry decode(ResourceLocation id, JsonObject json) {
        rejectUnknownKeys(json);
        if (!json.has("entity") || !json.get("entity").isJsonPrimitive()) {
            throw new IllegalArgumentException("entity must be a namespaced entity id");
        }
        ResourceLocation entityId = parseNamespacedId(json.get("entity").getAsString(), "entity");
        validateMachines(json);
        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        var decoded = WorkerStats.CODEC.parse(JsonOps.INSTANCE, json);
        WorkerStats stats = decoded.result().orElseThrow(() -> new IllegalArgumentException(
                decoded.error().map(Object::toString).orElse("worker profile did not decode")));
        return new WorkerProfileRegistry.Entry(id, entityId, priority, stats);
    }


    private static void validateMachines(JsonObject json) {
        if (!json.has("machines")) return;
        if (!json.get("machines").isJsonObject()) {
            throw new IllegalArgumentException("machines must be an object keyed by namespaced machine id");
        }
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("machines").entrySet()) {
            parseNamespacedId(entry.getKey(), "machine id");
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("machines." + entry.getKey() + " must be an object");
            }
            for (String key : entry.getValue().getAsJsonObject().keySet()) {
                if (!MACHINE_KEYS.contains(key)) {
                    throw new IllegalArgumentException("machines." + entry.getKey() + " has unknown field '" + key + "'");
                }
            }
        }
    }

    private static void rejectUnknownKeys(JsonObject json) {
        for (String key : json.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("unknown field '" + key + "'");
            }
        }
    }

    private static ResourceLocation parseNamespacedId(String value, String field) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException(field + " must be namespace:path, got " + value);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(field + " is not a valid resource id: " + value);
        }
        return parsed;
    }
}
