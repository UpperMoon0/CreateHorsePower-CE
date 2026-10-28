package net.steampn.createhorsepower.content.stats;

import net.steampn.createhorsepower.compat.IcyBetterHorses;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IcyProfilesTest {
    @Test
    void everyReleasedBreedUsesExactNamespaceWithoutNamespaceWideOptIn() {
        assertEquals(15, IcyBetterHorses.BREEDS.size());
        for (String breed : IcyBetterHorses.BREEDS) {
            assertTrue(IcyBetterHorses.isBreed("icys-better-horses:" + breed));
        }
        for (String excluded : new String[]{"icys_better_horses:arabian_horse", "icys-better-horses:horse_cart", "icys-better-horses:unknown_horse", "other:arabian_horse"}) {
            assertFalse(IcyBetterHorses.isBreed(excluded), excluded);
        }
        assertFalse(IcyBetterHorses.isBreed(null));
    }
}
