package net.steampn.createhorsepower.datagen;

import net.minecraft.world.entity.EntityType;

/** Loader-neutral resource definitions consumed by both datagen implementations. */
public final class CHPDataDefinitions {
    public static final EntityType<?>[] SMALL_WORKERS = {
            EntityType.WOLF, EntityType.CAT, EntityType.OCELOT, EntityType.FOX
    };
    public static final EntityType<?>[] MEDIUM_WORKERS = {
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.GOAT, EntityType.LLAMA, EntityType.TRADER_LLAMA
    };
    public static final EntityType<?>[] LARGE_WORKERS = {
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.CAMEL,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE
    };
    public static final String[] HORSE_CRANK_RECIPE = {" F ", " C ", "SSS"};

    private CHPDataDefinitions() {}
}
