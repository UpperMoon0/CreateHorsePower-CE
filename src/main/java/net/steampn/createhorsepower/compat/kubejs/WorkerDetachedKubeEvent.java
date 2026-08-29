package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class WorkerDetachedKubeEvent implements KubeEvent {
    @Nullable
    private final Mob worker;
    private final BlockPos crankPos;
    private final Level level;

    public WorkerDetachedKubeEvent(@Nullable Mob worker, BlockPos crankPos, Level level) {
        this.worker = worker;
        this.crankPos = crankPos;
        this.level = level;
    }

    public @Nullable Mob getWorker() {
        return worker;
    }

    public BlockPos getCrankPos() {
        return crankPos;
    }

    public Level getLevel() {
        return level;
    }
}
