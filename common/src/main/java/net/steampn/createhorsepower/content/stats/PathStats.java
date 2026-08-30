package net.steampn.createhorsepower.content.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PathStats(
        float speedMultiplier,
        float stressMultiplier
) {
    public PathStats {
        WorkerStats.validateNonNegativeFinite(speedMultiplier, "speedMultiplier");
        WorkerStats.validateNonNegativeFinite(stressMultiplier, "stressMultiplier");
    }

    public static final PathStats DEFAULT = new PathStats(1.0f, 1.0f);
    public static final PathStats NORMAL = new PathStats(1.0f, 1.0f);
    public static final PathStats POOR = new PathStats(0.75f, 0.90f);
    public static final PathStats GREAT = new PathStats(1.25f, 1.10f);

    private static final Codec<Float> NON_NEGATIVE_FLOAT = Codec.floatRange(0.0f, Float.MAX_VALUE);

    public static final Codec<PathStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NON_NEGATIVE_FLOAT.optionalFieldOf("speed_multiplier", 1.0f).forGetter(PathStats::speedMultiplier),
            NON_NEGATIVE_FLOAT.optionalFieldOf("stress_multiplier", 1.0f).forGetter(PathStats::stressMultiplier)
    ).apply(instance, PathStats::new));

    public static PathStats of(float speedMultiplier, float stressMultiplier) {
        return new PathStats(speedMultiplier, stressMultiplier);
    }
}
