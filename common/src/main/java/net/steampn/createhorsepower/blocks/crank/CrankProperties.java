package net.steampn.createhorsepower.blocks.crank;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Block state properties shared by both platform block implementations. */
public final class CrankProperties {
    public static final BooleanProperty HAS_WORKER = BooleanProperty.create("has_worker");
    public static final BooleanProperty SMALL_WORKER_STATE = BooleanProperty.create("small_worker");
    public static final BooleanProperty MEDIUM_WORKER_STATE = BooleanProperty.create("medium_worker");
    public static final BooleanProperty LARGE_WORKER_STATE = BooleanProperty.create("large_worker");

    private CrankProperties() {}
}
