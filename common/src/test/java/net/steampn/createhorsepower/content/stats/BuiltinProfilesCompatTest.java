package net.steampn.createhorsepower.content.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuiltinProfilesCompatTest {
    @Test
    void tfcHorseFamilyUsesMatchingBuiltInProfilesWithoutTfcClasses() {
        assertEquals(BuiltinProfiles.HORSE, BuiltinProfiles.optionalWorker("tfc:horse").orElseThrow());
        assertEquals(BuiltinProfiles.DONKEY, BuiltinProfiles.optionalWorker("tfc:donkey").orElseThrow());
        assertEquals(BuiltinProfiles.MULE, BuiltinProfiles.optionalWorker("tfc:mule").orElseThrow());
    }

    @Test
    void unknownTfcEntityDoesNotBecomeAWorkerImplicitly() {
        assertTrue(BuiltinProfiles.optionalWorker("tfc:not_a_real_worker").isEmpty());
    }
}
