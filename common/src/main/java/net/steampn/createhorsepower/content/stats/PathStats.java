package net.steampn.createhorsepower.content.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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

    /**
     * Validation lives in flatXmap instead of Codec.floatRange because DFU
     * versions differ: 1.20.1 silently falls back to optionalFieldOf defaults
     * on range errors while 1.21.1 propagates them. Explicit validation keeps
     * both versions byte-for-byte consistent.
     */
    private record Raw(float speedMultiplier, float stressMultiplier) {}

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("speed_multiplier", 1.0f).forGetter(Raw::speedMultiplier),
            Codec.FLOAT.optionalFieldOf("stress_multiplier", 1.0f).forGetter(Raw::stressMultiplier)
    ).apply(instance, Raw::new));

    private static DataResult<PathStats> validate(Raw raw) {
        try {
            return DataResult.success(new PathStats(raw.speedMultiplier(), raw.stressMultiplier()));
        } catch (IllegalArgumentException err) {
            return DataResult.error(err::getMessage);
        }
    }

    private static DataResult<Raw> encode(PathStats stats) {
        return DataResult.success(new Raw(stats.speedMultiplier(), stats.stressMultiplier()));
    }

    public static final Codec<PathStats> CODEC = RAW_CODEC.flatXmap(PathStats::validate, PathStats::encode);

    public static PathStats of(float speedMultiplier, float stressMultiplier) {
        return new PathStats(speedMultiplier, stressMultiplier);
    }
}
