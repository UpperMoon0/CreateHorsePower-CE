package net.steampn.createhorsepower.content.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record WorkerStats(
        float baseRpm,
        float stressCapacity,
        float movementRadius,
        float speedScaling,
        float speedReference,
        float healthScaling,
        float healthReference,
        boolean requiresTamed,
        boolean allowBaby
) {
    public static final float DEFAULT_SPEED_REF = 0.225f;
    public static final float DEFAULT_HEALTH_REF = 20.0f;

    public static final WorkerStats DEFAULT = new WorkerStats(4.0f, 256.0f, 2.5f, 0.0f, DEFAULT_SPEED_REF, 0.0f, DEFAULT_HEALTH_REF, false, false);

    private static final Codec<Float> NON_NEGATIVE_FLOAT = Codec.floatRange(0.0f, Float.MAX_VALUE);
    private static final Codec<Float> POSITIVE_FLOAT = Codec.floatRange(0.001f, Float.MAX_VALUE);
    private static final Codec<Float> RADIUS_FLOAT = Codec.floatRange(0.5f, 32.0f);

    public static final Codec<WorkerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NON_NEGATIVE_FLOAT.optionalFieldOf("rpm", 4.0f).forGetter(WorkerStats::baseRpm),
            NON_NEGATIVE_FLOAT.optionalFieldOf("stress", 256.0f).forGetter(WorkerStats::stressCapacity),
            RADIUS_FLOAT.optionalFieldOf("movement_radius", 2.5f).forGetter(WorkerStats::movementRadius),
            NON_NEGATIVE_FLOAT.optionalFieldOf("speed_scaling", 0.0f).forGetter(WorkerStats::speedScaling),
            POSITIVE_FLOAT.optionalFieldOf("speed_reference", DEFAULT_SPEED_REF).forGetter(WorkerStats::speedReference),
            NON_NEGATIVE_FLOAT.optionalFieldOf("health_scaling", 0.0f).forGetter(WorkerStats::healthScaling),
            POSITIVE_FLOAT.optionalFieldOf("health_reference", DEFAULT_HEALTH_REF).forGetter(WorkerStats::healthReference),
            Codec.BOOL.optionalFieldOf("requires_tamed", false).forGetter(WorkerStats::requiresTamed),
            Codec.BOOL.optionalFieldOf("allow_baby", false).forGetter(WorkerStats::allowBaby)
    ).apply(instance, WorkerStats::new));

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
        if (!Float.isFinite(value) || value < 0.5f || value > 32.0f) {
            throw new IllegalArgumentException("movementRadius must be finite and between 0.5 and 32.0 (got: " + value + ")");
        }
        return value;
    }

    public static Builder builder() {
        return new Builder();
    }

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

        public Builder rpm(float rpm) {
            this.baseRpm = validateNonNegativeFinite(rpm, "rpm");
            return this;
        }

        public Builder stress(float stress) {
            this.stressCapacity = validateNonNegativeFinite(stress, "stress");
            return this;
        }

        public Builder movementRadius(float radius) {
            this.movementRadius = validateRadius(radius);
            return this;
        }

        public Builder speedScaling(float scaling) {
            this.speedScaling = validateNonNegativeFinite(scaling, "speedScaling");
            return this;
        }

        public Builder speedReference(float ref) {
            this.speedReference = validatePositiveFinite(ref, "speedReference");
            return this;
        }

        public Builder healthScaling(float scaling) {
            this.healthScaling = validateNonNegativeFinite(scaling, "healthScaling");
            return this;
        }

        public Builder healthReference(float ref) {
            this.healthReference = validatePositiveFinite(ref, "healthReference");
            return this;
        }

        public Builder requiresTamed(boolean requiresTamed) {
            this.requiresTamed = requiresTamed;
            return this;
        }

        public Builder allowBaby(boolean allowBaby) {
            this.allowBaby = allowBaby;
            return this;
        }

        public WorkerStats build() {
            return new WorkerStats(baseRpm, stressCapacity, movementRadius, speedScaling, speedReference, healthScaling, healthReference, requiresTamed, allowBaby);
        }
    }
}
