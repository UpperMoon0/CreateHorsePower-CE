package net.steampn.createhorsepower.content.stats;

/**
 * Bridges the legacy tier output knobs to the richer bundled 1.2 worker
 * profiles without weakening explicit Data Map/KubeJS overrides.
 */
final class LegacyOutputBalance {
    static final int DEFAULT_RPM = 4;

    private static final float LEGACY_MOVEMENT_RADIUS = 2.5f;
    private static final float LEGACY_SPEED_SCALING = 0.5f;
    private static final float LEGACY_HEALTH_SCALING = 0.2f;

    private LegacyOutputBalance() {}

    /**
     * Historical profile used by workers that only exist through legacy config
     * lists/tags. Keep its old attribute scaling, then apply the same per-axis
     * override suppression used for richer bundled species profiles.
     */
    static WorkerStats legacyProfile(
            BuiltinProfiles.WorkerTier tier,
            int configuredRpm,
            int smallStress,
            int mediumStress,
            int largeStress
    ) {
        WorkerStats legacyBaseline = new WorkerStats(
                DEFAULT_RPM,
                tier.legacyDefaultStress(),
                LEGACY_MOVEMENT_RADIUS,
                LEGACY_SPEED_SCALING,
                WorkerStats.DEFAULT_SPEED_REF,
                LEGACY_HEALTH_SCALING,
                WorkerStats.DEFAULT_HEALTH_REF,
                false,
                false
        );
        return apply(legacyBaseline, tier, configuredRpm, smallStress, mediumStress, largeStress);
    }

    static WorkerStats apply(
            WorkerStats profile,
            BuiltinProfiles.WorkerTier tier,
            int configuredRpm,
            int smallStress,
            int mediumStress,
            int largeStress
    ) {
        boolean rpmOverridden = configuredRpm != DEFAULT_RPM;
        int configuredStress = switch (tier) {
            case SMALL -> smallStress;
            case MEDIUM -> mediumStress;
            case LARGE -> largeStress;
        };
        boolean stressOverridden = configuredStress != tier.legacyDefaultStress();

        if (!rpmOverridden && !stressOverridden) {
            return profile;
        }

        return new WorkerStats(
                rpmOverridden ? configuredRpm : profile.baseRpm(),
                stressOverridden ? configuredStress : profile.stressCapacity(),
                profile.movementRadius(),
                rpmOverridden ? 0.0f : profile.speedScaling(),
                profile.speedReference(),
                stressOverridden ? 0.0f : profile.healthScaling(),
                profile.healthReference(),
                profile.requiresTamed(),
                profile.allowBaby()
        );
    }
}
