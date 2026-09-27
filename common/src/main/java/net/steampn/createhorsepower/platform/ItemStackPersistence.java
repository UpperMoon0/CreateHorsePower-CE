package net.steampn.createhorsepower.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Version-specific ItemStack serialization preserving per-stack state. */
public interface ItemStackPersistence {
    CompoundTag saveOne(ItemStack stack, ServerLevel level);
    ItemStack load(CompoundTag tag, ServerLevel level);
}
