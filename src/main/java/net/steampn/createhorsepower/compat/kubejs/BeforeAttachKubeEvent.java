package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import org.jetbrains.annotations.Nullable;

public class BeforeAttachKubeEvent implements KubePlayerEvent {
    @Nullable
    private final Player player;
    private final Mob worker;
    private final BlockPos crankPos;
    private final Level level;
    private final WorkerResolver.ResolvedWorker profile;

    public BeforeAttachKubeEvent(@Nullable Player player, Mob worker, BlockPos crankPos, Level level, WorkerResolver.ResolvedWorker profile) {
        this.player = player;
        this.worker = worker;
        this.crankPos = crankPos;
        this.level = level;
        this.profile = profile;
    }

    @Override
    public @Nullable Player getEntity() {
        return player;
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
