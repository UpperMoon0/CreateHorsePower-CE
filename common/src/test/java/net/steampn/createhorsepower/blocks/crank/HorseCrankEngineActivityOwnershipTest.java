package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseCrankEngineActivityOwnershipTest {

    @Test
    void staleCrankRecognizesMarkerReassignedToDifferentUuid() {
        BlockPos crankPos = new BlockPos(2, 3, 4);
        UUID staleCrank = UUID.randomUUID();
        UUID newCrank = UUID.randomUUID();

        assertTrue(HorseCrankEngine.markerOwnedByDifferentCrank(
                        crankPos, newCrank, crankPos, staleCrank),
                "stale crank must not release a marker now owned by a newer crank UUID");
    }

    @Test
    void sameUuidAtDifferentPositionIsStillForeign() {
        UUID clonedUuid = UUID.randomUUID();
        BlockPos originalPos = new BlockPos(1, 2, 3);
        BlockPos clonedPos = new BlockPos(9, 2, 3);

        assertTrue(HorseCrankEngine.markerOwnedByDifferentCrank(
                        clonedPos, clonedUuid, originalPos, clonedUuid),
                "a cloned crank UUID at another position must not impersonate the original owner");
    }

    @Test
    void currentCrankMayReleaseItsOwnOrLegacyMarker() {
        UUID crank = UUID.randomUUID();
        BlockPos crankPos = new BlockPos(4, 5, 6);

        assertFalse(HorseCrankEngine.markerOwnedByDifferentCrank(
                        crankPos, crank, crankPos, crank),
                "current owner must still be allowed to release its own marker");
        assertFalse(HorseCrankEngine.markerOwnedByDifferentCrank(
                        null, crank, crankPos, crank),
                "legacy markers without a position keep existing same-UUID recovery behavior");
        assertFalse(HorseCrankEngine.markerOwnedByDifferentCrank(
                        crankPos, null, crankPos, crank),
                "legacy/malformed markers without an owner keep existing recovery behavior");
    }
}
