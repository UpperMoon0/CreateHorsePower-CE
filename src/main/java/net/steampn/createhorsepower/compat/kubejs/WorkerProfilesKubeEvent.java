package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.content.stats.WorkerStats;

import java.util.Map;

public class WorkerProfilesKubeEvent implements KubeEvent {

    public void add(String entityId, Map<String, Object> properties) {
        ResourceLocation id = ResourceLocation.parse(entityId);
        WorkerStats.Builder builder = WorkerStats.builder();

        if (properties.containsKey("rpm")) {
            builder.rpm(((Number) properties.get("rpm")).floatValue());
        }
        if (properties.containsKey("stress")) {
            builder.stress(((Number) properties.get("stress")).floatValue());
        }
        if (properties.containsKey("movementRadius")) {
            builder.movementRadius(((Number) properties.get("movementRadius")).floatValue());
        }
        if (properties.containsKey("speedScaling")) {
            builder.speedScaling(((Number) properties.get("speedScaling")).floatValue());
        }
        if (properties.containsKey("speedReference")) {
            builder.speedReference(((Number) properties.get("speedReference")).floatValue());
        }
        if (properties.containsKey("healthScaling")) {
            builder.healthScaling(((Number) properties.get("healthScaling")).floatValue());
        }
        if (properties.containsKey("healthReference")) {
            builder.healthReference(((Number) properties.get("healthReference")).floatValue());
        }
        if (properties.containsKey("requiresTamed")) {
            builder.requiresTamed((Boolean) properties.get("requiresTamed"));
        }
        if (properties.containsKey("allowBaby")) {
            builder.allowBaby((Boolean) properties.get("allowBaby"));
        }

        KubeJSProfileRegistry.registerWorker(id, builder.build());
    }

    public void add(String entityId, WorkerStats stats) {
        KubeJSProfileRegistry.registerWorker(ResourceLocation.parse(entityId), stats);
    }
}
