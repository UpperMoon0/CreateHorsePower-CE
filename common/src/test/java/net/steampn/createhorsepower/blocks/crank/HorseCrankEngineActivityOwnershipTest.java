package net.steampn.createhorsepower.blocks.crank;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseCrankEngineActivityOwnershipTest {

    @Test
    void staleCrankRecognizesMarkerReassignedToNewCrank() {
        UUID staleCrank = UUID.randomUUID();
        UUID newCrank = UUID.randomUUID();

        assertTrue(HorseCrankEngine.markerOwnedByDifferentCrank(newCrank, staleCrank),
                "stale crank must not release a marker now owned by a newer crank");
    }

    @Test
    void currentCrankMayReleaseItsOwnOrLegacyMarker() {
        UUID crank = UUID.randomUUID();

        assertFalse(HorseCrankEngine.markerOwnedByDifferentCrank(crank, crank),
                "current owner must still be allowed to release its own marker");
        assertFalse(HorseCrankEngine.markerOwnedByDifferentCrank(null, crank),
                "legacy/malformed markers without an owner keep existing recovery behavior");
    }
}
