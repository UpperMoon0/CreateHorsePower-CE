package net.steampn.createhorsepower.content.stats;

import net.steampn.createhorsepower.compat.IcyBetterHorses;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IcyProfilesTest {
    @Test
    void everyReleasedBreedUsesHorseProfileWithoutNamespaceWideOptIn() {
        assertEquals(15, IcyBetterHorses.BREEDS.size());
        for (String breed : IcyBetterHorses.BREEDS) {
            assertEquals(BuiltinProfiles.HORSE, BuiltinProfiles.optionalWorker("icys-better-horses:" + breed).orElseThrow());
        }
        for (String excluded : new String[]{"icys_better_horses:arabian_horse", "icys-better-horses:horse_cart", "icys-better-horses:unknown_horse", "other:arabian_horse"}) {
            assertTrue(BuiltinProfiles.optionalWorker(excluded).isEmpty(), excluded);
        }
        assertFalse(IcyBetterHorses.isBreed(null));
        assertEquals(BuiltinProfiles.HORSE, BuiltinProfiles.optionalWorker("tfc:horse").orElseThrow());
    }
}
