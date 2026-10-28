package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.steampn.createhorsepower.platform.DeferredDetachStore;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeferredDetachDropPolicyTest {
    @Test
    void exactNoDropTombstoneCannotBeUpgradedByStaleBreakOrDetach() {
        UUID owner = UUID.randomUUID();
        BlockPos pos = new BlockPos(4, 2, 4);
        var noDrop = new DeferredDetachStore.Entry(pos, owner, false);
        assertFalse(DeferredDetachStore.effectiveDropPolicy(noDrop, pos, owner, true));
        assertFalse(DeferredDetachStore.effectiveDropPolicy(noDrop, pos, owner, false));
    }

    @Test
    void foreignTombstoneCannotSuppressAnotherCranksOwnItemPolicy() {
        UUID owner = UUID.randomUUID();
        BlockPos pos = new BlockPos(4, 2, 4);
        var foreign = new DeferredDetachStore.Entry(pos, UUID.randomUUID(), false);
        assertTrue(DeferredDetachStore.effectiveDropPolicy(foreign, pos, owner, true));
        var clonedElsewhere = new DeferredDetachStore.Entry(pos.above(), owner, false);
        assertTrue(DeferredDetachStore.effectiveDropPolicy(clonedElsewhere, pos, owner, true));
    }

    @Test
    void freshAndDropEnabledPoliciesRetainRequestedBehavior() {
        UUID owner = UUID.randomUUID();
        BlockPos pos = BlockPos.ZERO;
        assertTrue(DeferredDetachStore.effectiveDropPolicy(null, pos, owner, true));
        assertFalse(DeferredDetachStore.effectiveDropPolicy(null, pos, owner, false));
        var drop = new DeferredDetachStore.Entry(pos, owner, true);
        assertTrue(DeferredDetachStore.effectiveDropPolicy(drop, pos, owner, true));
        assertFalse(DeferredDetachStore.effectiveDropPolicy(drop, pos, owner, false));
    }
}
