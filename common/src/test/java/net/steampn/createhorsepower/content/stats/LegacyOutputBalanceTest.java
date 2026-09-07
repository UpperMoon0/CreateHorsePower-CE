package net.steampn.createhorsepower.content.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyOutputBalanceTest {
    private static WorkerStats horseProfile() {
        return new WorkerStats(
                5.0f, 600.0f, 2.5f,
                0.75f, 0.225f,
                0.25f, 22.0f,
                false, false
        );
    }

    @Test
    void untouchedLegacyDefaultsKeepRichSpeciesProfile() {
        WorkerStats horse = horseProfile();
        WorkerStats result = LegacyOutputBalance.apply(
                horse,
                BuiltinProfiles.WorkerTier.LARGE,
                4, 128, 256, 512
        );

        assertSame(horse, result);
        assertEquals(5.0f, result.baseRpm());
        assertEquals(600.0f, result.stressCapacity());
        assertEquals(0.75f, result.speedScaling());
        assertEquals(0.25f, result.healthScaling());
    }

    @Test
    void tfgLegacyBalanceMakesLargeHorseSixteenRpmAndThirtyTwoSu() {
        WorkerStats horse = horseProfile();
        WorkerStats result = LegacyOutputBalance.apply(
                horse,
                BuiltinProfiles.WorkerTier.LARGE,
                16, 16, 24, 32
        );

        assertEquals(16.0f, result.baseRpm());
        assertEquals(32.0f, result.stressCapacity());
        assertEquals(0.0f, result.speedScaling(), "legacy RPM override must remain authoritative");
        assertEquals(0.0f, result.healthScaling(), "legacy SU override must remain authoritative");
        assertEquals(horse.movementRadius(), result.movementRadius());
        assertEquals(horse.speedReference(), result.speedReference());
        assertEquals(horse.healthReference(), result.healthReference());
    }

    @Test
    void outputAxesOverrideIndependently() {
        WorkerStats horse = horseProfile();
        WorkerStats stressOnly = LegacyOutputBalance.apply(
                horse,
                BuiltinProfiles.WorkerTier.LARGE,
                4, 128, 256, 32
        );
        assertEquals(5.0f, stressOnly.baseRpm());
        assertEquals(32.0f, stressOnly.stressCapacity());
        assertEquals(0.75f, stressOnly.speedScaling());
        assertEquals(0.0f, stressOnly.healthScaling());

        WorkerStats rpmOnly = LegacyOutputBalance.apply(
                horse,
                BuiltinProfiles.WorkerTier.LARGE,
                16, 128, 256, 512
        );
        assertEquals(16.0f, rpmOnly.baseRpm());
        assertEquals(600.0f, rpmOnly.stressCapacity());
        assertEquals(0.0f, rpmOnly.speedScaling());
        assertEquals(0.25f, rpmOnly.healthScaling());
    }

    @Test
    void legacyOnlyWorkerKeepsHistoricalScalingAtDefaultBalance() {
        WorkerStats smallLegacyWorker = LegacyOutputBalance.legacyProfile(
                BuiltinProfiles.WorkerTier.SMALL,
                4, 128, 256, 512
        );

        assertEquals(4.0f, smallLegacyWorker.baseRpm());
        assertEquals(128.0f, smallLegacyWorker.stressCapacity());
        assertEquals(0.5f, smallLegacyWorker.speedScaling(),
                "legacy-only workers must keep the pre-1.2 speed scaling baseline");
        assertEquals(0.2f, smallLegacyWorker.healthScaling(),
                "legacy-only workers must keep the pre-1.2 health scaling baseline");
    }

    @Test
    void legacyOnlyWorkerSuppressesOnlyExplicitlyOverriddenAxes() {
        WorkerStats stressOnly = LegacyOutputBalance.legacyProfile(
                BuiltinProfiles.WorkerTier.MEDIUM,
                4, 128, 24, 512
        );
        assertEquals(4.0f, stressOnly.baseRpm());
        assertEquals(24.0f, stressOnly.stressCapacity());
        assertEquals(0.5f, stressOnly.speedScaling());
        assertEquals(0.0f, stressOnly.healthScaling());

        WorkerStats rpmOnly = LegacyOutputBalance.legacyProfile(
                BuiltinProfiles.WorkerTier.MEDIUM,
                16, 128, 256, 512
        );
        assertEquals(16.0f, rpmOnly.baseRpm());
        assertEquals(256.0f, rpmOnly.stressCapacity());
        assertEquals(0.0f, rpmOnly.speedScaling());
        assertEquals(0.2f, rpmOnly.healthScaling());
    }
}
