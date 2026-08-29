package net.steampn.createhorsepower.content.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record WorkerStats(
        float baseRpm,
        float stressCapacity,
        float movementRadius,
        float speedScaling,
        float healthScaling,
        boolean requiresTamed,
        boolean allowBaby
) {
    public static final WorkerStats DEFAULT = new WorkerStats(4.0f, 256.0f, 2.5f, 0.0f, 0.0f, false, false);
    public static final WorkerStats SMALL_DEFAULT = new WorkerStats(4.0f, 128.0f, 2.5f, 0.0f, 0.0f, false, false);
    public static final WorkerStats MEDIUM_DEFAULT = new WorkerStats(4.0f, 256.0f, 2.5f, 0.0f, 0.0f, false, false);
    public static final WorkerStats LARGE_DEFAULT = new WorkerStats(4.0f, 512.0f, 2.5f, 0.5f, 0.2f, false, false);

    public static final Codec<WorkerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("rpm", 4.0f).forGetter(WorkerStats::baseRpm),
            Codec.FLOAT.optionalFieldOf("stress", 256.0f).forGetter(WorkerStats::stressCapacity),
            Codec.FLOAT.optionalFieldOf("movement_radius", 2.5f).forGetter(WorkerStats::movementRadius),
            Codec.FLOAT.optionalFieldOf("speed_scaling", 0.0f).forGetter(WorkerStats::speedScaling),
            Codec.FLOAT.optionalFieldOf("health_scaling", 0.0f).forGetter(WorkerStats::healthScaling),
            Codec.BOOL.optionalFieldOf("requires_tamed", false).forGetter(WorkerStats::requiresTamed),
            Codec.BOOL.optionalFieldOf("allow_baby", false).forGetter(WorkerStats::allowBaby)
    ).apply(instance, WorkerStats::new));

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float baseRpm = 4.0f;
        private float stressCapacity = 256.0f;
        private float movementRadius = 2.5f;
        private float speedScaling = 0.0f;
        private float healthScaling = 0.0f;
        private boolean requiresTamed = false;
        private boolean allowBaby = false;

        public Builder rpm(float rpm) {
            this.baseRpm = rpm;
            return this;
        }

        public Builder stress(float stress) {
            this.stressCapacity = stress;
            return this;
        }

        public Builder movementRadius(float radius) {
            this.movementRadius = radius;
            return this;
        }

        public Builder speedScaling(float scaling) {
            this.speedScaling = scaling;
            return this;
        }

        public Builder healthScaling(float scaling) {
            this.healthScaling = scaling;
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
            return new WorkerStats(baseRpm, stressCapacity, movementRadius, speedScaling, healthScaling, requiresTamed, allowBaby);
        }
    }
}
