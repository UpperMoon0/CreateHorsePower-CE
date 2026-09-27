package net.steampn.createhorsepower.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class ForgeItemStackPersistence implements ItemStackPersistence {
    @Override
    public CompoundTag saveOne(ItemStack stack, ServerLevel level) {
        ItemStack one = stack.copy();
        one.setCount(1);
        return one.save(new CompoundTag());
    }

    @Override
    public ItemStack load(CompoundTag tag, ServerLevel level) {
        return ItemStack.of(tag);
    }
}
