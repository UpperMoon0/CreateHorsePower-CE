package net.steampn.createhorsepower.content.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

public record WorkerStats(
        float baseRpm,
        float stressCapacity,
        float movementRadius,
        float speedScaling,
        float speedReference,
        float healthScaling,
        float healthReference,
        boolean requiresTamed,
        boolean allowBaby,
        Map<String, WorkerStatsOverride> machines
) {
    public WorkerStats {
        machines = machines == null ? Map.of() : Map.copyOf(machines);
        for (Map.Entry<String, WorkerStatsOverride> machine : machines.entrySet()) {
            String machineId = machine.getKey();
            if (!machineId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                throw new IllegalArgumentException("Invalid machine id in worker profile: " + machineId);
            }
            machine.getValue().validateValues();
        }
        validateNonNegativeFinite(baseRpm, "baseRpm");
        validateNonNegativeFinite(stressCapacity, "stressCapacity");
        validateRadius(movementRadius);
        validateNonNegativeFinite(speedScaling, "speedScaling");
        validatePositiveFinite(speedReference, "speedReference");
        validateNonNegativeFinite(healthScaling, "healthScaling");
        validatePositiveFinite(healthReference, "healthReference");
    }

    /** Backward-compatible constructor for pre-machine-override callers. */
    public WorkerStats(float baseRpm, float stressCapacity, float movementRadius, float speedScaling,
                       float speedReference, float healthScaling, float healthReference,
                       boolean requiresTamed, boolean allowBaby) {
        this(baseRpm, stressCapacity, movementRadius, speedScaling, speedReference,
                healthScaling, healthReference, requiresTamed, allowBaby, Map.of());
    }

    public static final float DEFAULT_SPEED_REF = 0.225f;
    public static final float DEFAULT_HEALTH_REF = 20.0f;
    public static final float MIN_MOVEMENT_RADIUS = 0.5f;
    public static final float MAX_MOVEMENT_RADIUS = 6.0f;

    public static final WorkerStats DEFAULT = new WorkerStats(4.0f, 256.0f, 2.5f, 0.0f,
            DEFAULT_SPEED_REF, 0.0f, DEFAULT_HEALTH_REF, false, false);

    private record Raw(
            float baseRpm, float stressCapacity, float movementRadius,
            float speedScaling, float speedReference, float healthScaling, float healthReference,
            boolean requiresTamed, boolean allowBaby, Map<String, WorkerStatsOverride> machines
    ) {}

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("rpm", 4.0f).forGetter(Raw::baseRpm),
            Codec.FLOAT.optionalFieldOf("stress", 256.0f).forGetter(Raw::stressCapacity),
            Codec.FLOAT.optionalFieldOf("movement_radius", 2.5f).forGetter(Raw::movementRadius),
            Codec.FLOAT.optionalFieldOf("speed_scaling", 0.0f).forGetter(Raw::speedScaling),
            Codec.FLOAT.optionalFieldOf("speed_reference", DEFAULT_SPEED_REF).forGetter(Raw::speedReference),
            Codec.FLOAT.optionalFieldOf("health_scaling", 0.0f).forGetter(Raw::healthScaling),
            Codec.FLOAT.optionalFieldOf("health_reference", DEFAULT_HEALTH_REF).forGetter(Raw::healthReference),
            Codec.BOOL.optionalFieldOf("requires_tamed", false).forGetter(Raw::requiresTamed),
            Codec.BOOL.optionalFieldOf("allow_baby", false).forGetter(Raw::allowBaby),
            Codec.unboundedMap(Codec.STRING, WorkerStatsOverride.CODEC)
                    .optionalFieldOf("machines", Map.of()).forGetter(Raw::machines)
    ).apply(instance, Raw::new));

    private static DataResult<WorkerStats> validate(Raw raw) {
        try {
            return DataResult.success(new WorkerStats(
                    raw.baseRpm(), raw.stressCapacity(), raw.movementRadius(),
                    raw.speedScaling(), raw.speedReference(), raw.healthScaling(),
                    raw.healthReference(), raw.requiresTamed(), raw.allowBaby(), raw.machines()));
        } catch (IllegalArgumentException err) {
            return DataResult.error(err::getMessage);
        }
    }

    private static DataResult<Raw> encode(WorkerStats stats) {
        return DataResult.success(new Raw(
                stats.baseRpm(), stats.stressCapacity(), stats.movementRadius(),
                stats.speedScaling(), stats.speedReference(), stats.healthScaling(),
                stats.healthReference(), stats.requiresTamed(), stats.allowBaby(), stats.machines()));
    }

    public static final Codec<WorkerStats> CODEC = RAW_CODEC.flatXmap(WorkerStats::validate, WorkerStats::encode);

    public WorkerStats forMachine(String machineId) {
        WorkerStatsOverride override = machines.get(machineId);
        return override == null ? this : override.applyTo(this);
    }

    public boolean hasMachineOverride(String machineId) {
        return machines.containsKey(machineId);
    }

    public static float validateNonNegativeFinite(float value, String fieldName) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(fieldName + " must be finite and >= 0 (got: " + value + ")");
        }
        return value;
    }

    public static float validatePositiveFinite(float value, String fieldName) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(fieldName + " must be finite and > 0 (got: " + value + ")");
        }
        return value;
    }

    public static float validateRadius(float value) {
        if (!Float.isFinite(value) || value < MIN_MOVEMENT_RADIUS || value > MAX_MOVEMENT_RADIUS) {
            throw new IllegalArgumentException("movementRadius must be finite and between "
                    + MIN_MOVEMENT_RADIUS + " and " + MAX_MOVEMENT_RADIUS + " (got: " + value + ")");
        }
        return value;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private float baseRpm = 4.0f;
        private float stressCapacity = 256.0f;
        private float movementRadius = 2.5f;
        private float speedScaling = 0.0f;
        private float speedReference = DEFAULT_SPEED_REF;
        private float healthScaling = 0.0f;
        private float healthReference = DEFAULT_HEALTH_REF;
        private boolean requiresTamed = false;
        private boolean allowBaby = false;
        private Map<String, WorkerStatsOverride> machines = Map.of();

        public Builder rpm(float rpm) { this.baseRpm = validateNonNegativeFinite(rpm, "rpm"); return this; }
        public Builder stress(float stress) { this.stressCapacity = validateNonNegativeFinite(stress, "stress"); return this; }
        public Builder movementRadius(float radius) { this.movementRadius = validateRadius(radius); return this; }
        public Builder speedScaling(float scaling) { this.speedScaling = validateNonNegativeFinite(scaling, "speedScaling"); return this; }
        public Builder speedReference(float ref) { this.speedReference = validatePositiveFinite(ref, "speedReference"); return this; }
        public Builder healthScaling(float scaling) { this.healthScaling = validateNonNegativeFinite(scaling, "healthScaling"); return this; }
        public Builder healthReference(float ref) { this.healthReference = validatePositiveFinite(ref, "healthReference"); return this; }
        public Builder requiresTamed(boolean value) { this.requiresTamed = value; return this; }
        public Builder allowBaby(boolean value) { this.allowBaby = value; return this; }

        public Builder machine(String machineId, WorkerStatsOverride override) {
            Map<String, WorkerStatsOverride> copy = new HashMap<>(machines);
            copy.put(machineId, override);
            machines = Map.copyOf(copy);
            return this;
        }

        public WorkerStats build() {
            return new WorkerStats(baseRpm, stressCapacity, movementRadius, speedScaling, speedReference,
                    healthScaling, healthReference, requiresTamed, allowBaby, machines);
        }
    }
}
