package net.steampn.createhorsepower.platform;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;

/**
 * Script (KubeJS) integration hooks. Platforms wire their compat layer here;
 * when no integration is installed the no-op default is used.
 */
public interface ScriptHooks {
    ScriptHooks NOOP = new ScriptHooks() {
    };

    default boolean fireBeforeAttach(@Nullable Player player, Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        return true;
    }

    default void fireWorkerAttached(Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
    }

    default void fireWorkerDetached(@Nullable Mob worker, BlockPos pos, Level level) {
    }

    default boolean fireBeforeWorkStart(Mob worker, BlockPos pos, Level level) {
        return true;
    }

    default void fireWorkStarted(Mob worker, BlockPos pos, Level level) {
    }

    default void fireWorkStopped(BlockPos pos, Level level) {
    }

    default float[] fireOutputCalculated(Mob worker, BlockPos pos, Level level, float rpm, float stress) {
        return new float[]{1.0f, 1.0f};
    }

    default float[] firePathEvaluated(BlockPos pos, Level level, PathEvaluator.Result result) {
        return new float[]{1.0f, 1.0f};
    }
}
