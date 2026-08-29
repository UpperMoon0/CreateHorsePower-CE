package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.content.stats.WorkerResolver;

public class WorkerAttachedKubeEvent implements KubeEvent {
    private final Mob worker;
    private final BlockPos crankPos;
    private final Level level;
    private final WorkerResolver.ResolvedWorker profile;

    public WorkerAttachedKubeEvent(Mob worker, BlockPos crankPos, Level level, WorkerResolver.ResolvedWorker profile) {
        this.worker = worker;
        this.crankPos = crankPos;
        this.level = level;
        this.profile = profile;
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

    public WorkerResolver.ResolvedWorker getProfile() {
        return profile;
    }
}
