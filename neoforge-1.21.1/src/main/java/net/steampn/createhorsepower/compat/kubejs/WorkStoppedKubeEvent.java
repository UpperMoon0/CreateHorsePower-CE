package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class WorkStoppedKubeEvent implements KubeEvent {
    private final BlockPos crankPos;
    private final Level level;

    public WorkStoppedKubeEvent(BlockPos crankPos, Level level) {
        this.crankPos = crankPos;
        this.level = level;
    }

    public BlockPos getCrankPos() {
        return crankPos;
    }

    public Level getLevel() {
        return level;
    }
}
