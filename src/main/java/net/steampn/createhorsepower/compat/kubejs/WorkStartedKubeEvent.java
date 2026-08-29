package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class WorkStartedKubeEvent implements KubeEvent {
    private final Mob worker;
    private final BlockPos crankPos;
    private final Level level;

    public WorkStartedKubeEvent(Mob worker, BlockPos crankPos, Level level) {
        this.worker = worker;
        this.crankPos = crankPos;
        this.level = level;
    }

    public Mob getWorker() {
        return worker;
    }

    public BlockPos getCrankPos() {
        return crankPos;
    }

    public Level getLevel() {
        return level;
    }
}
