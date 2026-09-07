package net.steampn.createhorsepower.content.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyOutputBalanceTest {
    @Test
    void untouchedLegacyDefaultsKeepRichSpeciesProfile() {
        WorkerStats result = LegacyOutputBalance.apply(
                BuiltinProfiles.HORSE,
                BuiltinProfiles.WorkerTier.LARGE,
                4, 128, 256, 512
        );

        assertSame(BuiltinProfiles.HORSE, result);
        assertEquals(5.0f, result.baseRpm());
        assertEquals(600.0f, result.stressCapacity());
        assertEquals(0.75f, result.speedScaling());
        assertEquals(0.25f, result.healthScaling());
    }

    @Test
    void tfgLegacyBalanceMakesLargeHorseSixteenRpmAndThirtyTwoSu() {
        WorkerStats result = LegacyOutputBalance.apply(
                BuiltinProfiles.HORSE,
                BuiltinProfiles.WorkerTier.LARGE,
                16, 16, 24, 32
        );

        assertEquals(16.0f, result.baseRpm());
        assertEquals(32.0f, result.stressCapacity());
        assertEquals(0.0f, result.speedScaling(), "legacy RPM override must remain authoritative");
        assertEquals(0.0f, result.healthScaling(), "legacy SU override must remain authoritative");
        assertEquals(BuiltinProfiles.HORSE.movementRadius(), result.movementRadius());
        assertEquals(BuiltinProfiles.HORSE.speedReference(), result.speedReference());
        assertEquals(BuiltinProfiles.HORSE.healthReference(), result.healthReference());
    }

    @Test
    void outputAxesOverrideIndependently() {
        WorkerStats stressOnly = LegacyOutputBalance.apply(
                BuiltinProfiles.HORSE,
                BuiltinProfiles.WorkerTier.LARGE,
                4, 128, 256, 32
        );
        assertEquals(5.0f, stressOnly.baseRpm());
        assertEquals(32.0f, stressOnly.stressCapacity());
        assertEquals(0.75f, stressOnly.speedScaling());
        assertEquals(0.0f, stressOnly.healthScaling());

        WorkerStats rpmOnly = LegacyOutputBalance.apply(
                BuiltinProfiles.HORSE,
                BuiltinProfiles.WorkerTier.LARGE,
                16, 128, 256, 512
        );
        assertEquals(16.0f, rpmOnly.baseRpm());
        assertEquals(600.0f, rpmOnly.stressCapacity());
        assertEquals(0.0f, rpmOnly.speedScaling());
        assertEquals(0.25f, rpmOnly.healthScaling());
    }
}
