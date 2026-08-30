package net.steampn.createhorsepower.blocks.horse_crank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;

/**
 * NeoForge 1.21.1 shell. Gameplay lives in the shared
 * {@link AbstractHorseCrankBlockEntity}; only the 1.21.1 NBT signature stays here.
 */
public class HorseCrankTileEntity extends AbstractHorseCrankBlockEntity {

    public HorseCrankTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        engine().write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        engine().read(compound, clientPacket);
    }
}
