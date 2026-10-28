package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.steampn.createhorsepower.content.crank.RedstoneMode;
import net.steampn.createhorsepower.content.machine.AnimalPowerMachinePolicy;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IcyWorkerPermissionPersistenceTest {
    private static AnimalPowerEngine engine() {
        return new AnimalPowerEngine(new FakeHost(), RedstoneMode.IGNORE,
                new AnimalPowerMachinePolicy(ResourceLocation.tryParse("createhorsepower:horse_crank"), 1));
    }

    @Test
    void vanillaBodyBeforeIcyConversionRetainsPermissionRequirementAcrossSave() {
        CompoundTag saved = new CompoundTag();
        saved.putUUID("WorkerUUID", UUID.randomUUID());
        saved.putString("WorkerEntityId", "minecraft:horse");
        saved.putBoolean("WorkerRequiresIcyPermission", true);
        AnimalPowerEngine first = engine();
        first.read(saved, false);
        CompoundTag roundTrip = new CompoundTag();
        first.write(roundTrip, false);
        AnimalPowerEngine reloaded = engine();
        reloaded.read(roundTrip, false);
        assertTrue(reloaded.requiresIcyWorkerPermission());
        assertEquals("minecraft:horse", roundTrip.getString("WorkerEntityId"));
    }

    @Test
    void exactIcyRegistryHintFailsClosedEvenWithoutBooleanHint() {
        CompoundTag saved = new CompoundTag();
        saved.putUUID("WorkerUUID", UUID.randomUUID());
        saved.putString("WorkerEntityId", "icys-better-horses:arabian_horse");
        AnimalPowerEngine engine = engine();
        engine.read(saved, false);
        assertTrue(engine.requiresIcyWorkerPermission());
    }

    @Test
    void readingLegacyVanillaSaveDoesNotRetainPreviousIcyPermissionHint() {
        AnimalPowerEngine engine = engine();
        CompoundTag icy = new CompoundTag();
        icy.putUUID("WorkerUUID", UUID.randomUUID());
        icy.putBoolean("WorkerRequiresIcyPermission", true);
        engine.read(icy, false);
        CompoundTag legacy = new CompoundTag();
        legacy.putUUID("WorkerUUID", UUID.randomUUID());
        engine.read(legacy, false);
        assertFalse(engine.requiresIcyWorkerPermission());
    }

    private static final class FakeHost implements AnimalPowerEngine.Host {
        @Override public Level level() { return null; }
        @Override public BlockPos pos() { return BlockPos.ZERO; }
        @Override public BlockState blockState() { return null; }
        @Override public boolean hasWorkerProperty() { return false; }
        @Override public void setWorkerPresent(boolean present) {}
        @Override public void setBlockState(BlockState state) {}
        @Override public float theoreticalSpeed() { return 0; }
        @Override public void refreshKinetic() {}
        @Override public void syncToClient() {}
        @Override public void markDirty() {}
        @Override public void clearKineticInfo() {}
        @Override public void requestSpeedUpdate() {}
        @Override public void setLastCapacityProvided(float capacity) {}
    }
}
