package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.content.stats.WorkerStatsOverride;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class WorkerProfilesKubeEvent implements KubeEvent {
    private static final Set<String> PROFILE_KEYS = Set.of(
            "rpm", "stress", "movementRadius", "speedScaling", "speedReference",
            "healthScaling", "healthReference", "requiresTamed", "allowBaby", "machines"
    );
    private static final Set<String> MACHINE_KEYS = Set.of(
            "rpm", "stress", "movementRadius", "speedScaling", "speedReference",
            "healthScaling", "healthReference", "requiresTamed", "allowBaby"
    );

    public void add(String entityId, Map<String, Object> properties) {
        rejectUnknownKeys(properties, PROFILE_KEYS, "worker profile");
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
        if (properties.containsKey("machines")) {
            Object machinesValue = properties.get("machines");
            if (!(machinesValue instanceof Map<?, ?> machines)) {
                throw new IllegalArgumentException("machines must be an object keyed by namespaced machine id");
            }
            for (Map.Entry<?, ?> entry : machines.entrySet()) {
                String machineId = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> override)) {
                    throw new IllegalArgumentException("machines." + machineId + " must be an object");
                }
                builder.machine(machineId, machineOverride(machineId, override));
            }
        }

        KubeJSProfileRegistry.registerWorker(id, builder.build());
    }

    private static WorkerStatsOverride machineOverride(String machineId, Map<?, ?> values) {
        rejectUnknownKeys(values, MACHINE_KEYS, "machines." + machineId);
        WorkerStatsOverride override = new WorkerStatsOverride(
                number(values, "rpm"),
                number(values, "stress"),
                number(values, "movementRadius"),
                number(values, "speedScaling"),
                number(values, "speedReference"),
                number(values, "healthScaling"),
                number(values, "healthReference"),
                bool(values, "requiresTamed"),
                bool(values, "allowBaby")
        );
        try {
            override.validateValues();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid machines." + machineId + ": " + ex.getMessage(), ex);
        }
        return override;
    }

    private static Optional<Float> number(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be numeric");
        }
        return Optional.of(number.floatValue());
    }

    private static Optional<Boolean> bool(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(key + " must be boolean");
        }
        return Optional.of(flag);
    }


    private static void rejectUnknownKeys(Map<?, ?> values, Set<String> allowed, String context) {
        for (Object rawKey : values.keySet()) {
            String key = String.valueOf(rawKey);
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(context + " has unknown field '" + key + "'");
            }
        }
    }

    public void add(String entityId, WorkerStats stats) {
        KubeJSProfileRegistry.registerWorker(ResourceLocation.parse(entityId), stats);
    }
}
