package net.steampn.createhorsepower.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/** Guards the precedence contract: bundled defaults must not masquerade as pack Data Map overrides. */
public class TfcWorkerProfilePrecedenceTest {
    @Test
    void bundledWorkerProfilesAreNotShippedAsDataMapOverrides() {
        assertNull(
                TfcWorkerProfilePrecedenceTest.class.getResourceAsStream(
                        "/data/createhorsepower/data_maps/entity_type/worker_stats.json"),
                "bundled worker profiles must resolve through common code so server balance config can govern them"
        );
    }
}
