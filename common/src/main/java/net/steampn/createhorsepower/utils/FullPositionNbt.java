package net.steampn.createhorsepower.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Exact positions for worker ownership/recovery; retains packed legacy keys for old saves. */
public final class FullPositionNbt {
    private FullPositionNbt() {}
    public static void put(CompoundTag tag, String key, BlockPos pos) {
        tag.putLong(key, pos.asLong());
        tag.putIntArray(key + "XYZ", new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }
    public static BlockPos get(CompoundTag tag, String key) {
        String exact = key + "XYZ";
        if (!tag.contains(exact)) return BlockPos.of(tag.getLong(key));
        if (!tag.contains(exact, 11)) throw new IllegalArgumentException("Invalid full position " + key);
        int[] xyz = tag.getIntArray(exact);
        if (xyz.length != 3) throw new IllegalArgumentException("Incomplete full position " + key);
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }
}
