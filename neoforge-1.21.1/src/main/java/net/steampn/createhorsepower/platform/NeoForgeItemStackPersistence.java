package net.steampn.createhorsepower.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class NeoForgeItemStackPersistence implements ItemStackPersistence {
    @Override
    public CompoundTag saveOne(ItemStack stack, ServerLevel level) {
        ItemStack one = stack.copyWithCount(1);
        Tag encoded = one.save(level.registryAccess());
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalStateException("Serialized ItemStack was not a CompoundTag");
        }
        return compound;
    }

    @Override
    public ItemStack load(CompoundTag tag, ServerLevel level) {
        return ItemStack.parse(level.registryAccess(), tag).orElse(ItemStack.EMPTY);
    }
}
