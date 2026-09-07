package net.steampn.createhorsepower.content.stats;

/**
 * Bridges the legacy tier output knobs to the richer bundled 1.2 worker
 * profiles without weakening explicit Data Map/KubeJS overrides.
 */
final class LegacyOutputBalance {
    static final int DEFAULT_RPM = 4;

    private LegacyOutputBalance() {}

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
