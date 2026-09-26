package net.steampn.createhorsepower.content.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Field-by-field worker overrides applied for one animal-power machine. */
public record WorkerStatsOverride(
        Optional<Float> rpm,
        Optional<Float> stress,
        Optional<Float> movementRadius,
        Optional<Float> speedScaling,
        Optional<Float> speedReference,
        Optional<Float> healthScaling,
        Optional<Float> healthReference,
        Optional<Boolean> requiresTamed,
        Optional<Boolean> allowBaby
) {
    private record Raw(
            Optional<Float> rpm, Optional<Float> stress, Optional<Float> movementRadius,
            Optional<Float> speedScaling, Optional<Float> speedReference,
            Optional<Float> healthScaling, Optional<Float> healthReference,
            Optional<Boolean> requiresTamed, Optional<Boolean> allowBaby
    ) {}

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("rpm").forGetter(Raw::rpm),
            Codec.FLOAT.optionalFieldOf("stress").forGetter(Raw::stress),
            Codec.FLOAT.optionalFieldOf("movement_radius").forGetter(Raw::movementRadius),
            Codec.FLOAT.optionalFieldOf("speed_scaling").forGetter(Raw::speedScaling),
            Codec.FLOAT.optionalFieldOf("speed_reference").forGetter(Raw::speedReference),
            Codec.FLOAT.optionalFieldOf("health_scaling").forGetter(Raw::healthScaling),
            Codec.FLOAT.optionalFieldOf("health_reference").forGetter(Raw::healthReference),
            Codec.BOOL.optionalFieldOf("requires_tamed").forGetter(Raw::requiresTamed),
            Codec.BOOL.optionalFieldOf("allow_baby").forGetter(Raw::allowBaby)
    ).apply(instance, Raw::new));

    private static WorkerStatsOverride fromRaw(Raw raw) {
        return new WorkerStatsOverride(raw.rpm(), raw.stress(), raw.movementRadius(), raw.speedScaling(),
                raw.speedReference(), raw.healthScaling(), raw.healthReference(), raw.requiresTamed(), raw.allowBaby());
    }

    private static Raw toRaw(WorkerStatsOverride value) {
        return new Raw(value.rpm(), value.stress(), value.movementRadius(), value.speedScaling(),
                value.speedReference(), value.healthScaling(), value.healthReference(),
                value.requiresTamed(), value.allowBaby());
    }

    // Semantic validation intentionally occurs in the containing WorkerStats codec.
    // DFU 1.20's optionalFieldOf treats a nested codec error as "field absent"; validating
    // here would therefore silently discard an invalid machines map instead of rejecting it.
    public static final Codec<WorkerStatsOverride> CODEC = RAW_CODEC.xmap(WorkerStatsOverride::fromRaw, WorkerStatsOverride::toRaw);

    public void validateValues() {
        rpm.ifPresent(v -> WorkerStats.validateNonNegativeFinite(v, "machines.rpm"));
        stress.ifPresent(v -> WorkerStats.validateNonNegativeFinite(v, "machines.stress"));
        movementRadius.ifPresent(WorkerStats::validateRadius);
        speedScaling.ifPresent(v -> WorkerStats.validateNonNegativeFinite(v, "machines.speed_scaling"));
        speedReference.ifPresent(v -> WorkerStats.validatePositiveFinite(v, "machines.speed_reference"));
        healthScaling.ifPresent(v -> WorkerStats.validateNonNegativeFinite(v, "machines.health_scaling"));
        healthReference.ifPresent(v -> WorkerStats.validatePositiveFinite(v, "machines.health_reference"));
    }
    public WorkerStats applyTo(WorkerStats base) {
        return new WorkerStats(
                rpm.orElse(base.baseRpm()), stress.orElse(base.stressCapacity()), movementRadius.orElse(base.movementRadius()),
                speedScaling.orElse(base.speedScaling()), speedReference.orElse(base.speedReference()),
                healthScaling.orElse(base.healthScaling()), healthReference.orElse(base.healthReference()),
                requiresTamed.orElse(base.requiresTamed()), allowBaby.orElse(base.allowBaby()), base.machines());
    }
}
