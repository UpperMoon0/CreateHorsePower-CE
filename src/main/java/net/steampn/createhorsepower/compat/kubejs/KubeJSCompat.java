package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import org.jetbrains.annotations.Nullable;

public class KubeJSCompat {

    public static boolean fireBeforeAttach(@Nullable Player player, Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        BeforeAttachKubeEvent event = new BeforeAttachKubeEvent(player, worker, pos, level, profile);
        return !HorsePowerKubeEvents.BEFORE_ATTACH.post(ScriptType.SERVER, event).interruptFalse();
    }

    public static void fireWorkerAttached(Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        WorkerAttachedKubeEvent event = new WorkerAttachedKubeEvent(worker, pos, level, profile);
        HorsePowerKubeEvents.WORKER_ATTACHED.post(ScriptType.SERVER, event);
    }

    public static void fireWorkerDetached(@Nullable Mob worker, BlockPos pos, Level level) {
        WorkerDetachedKubeEvent event = new WorkerDetachedKubeEvent(worker, pos, level);
        HorsePowerKubeEvents.WORKER_DETACHED.post(ScriptType.SERVER, event);
    }

    public static boolean fireWorkStarted(Mob worker, BlockPos pos, Level level) {
        WorkStartedKubeEvent event = new WorkStartedKubeEvent(worker, pos, level);
        return !HorsePowerKubeEvents.WORK_STARTED.post(ScriptType.SERVER, event).interruptFalse();
    }

    public static void fireWorkStopped(BlockPos pos, Level level) {
        WorkStoppedKubeEvent event = new WorkStoppedKubeEvent(pos, level);
        HorsePowerKubeEvents.WORK_STOPPED.post(ScriptType.SERVER, event);
    }

    public static float[] fireOutputCalculated(Mob worker, BlockPos pos, Level level, float rpm, float stress) {
        OutputCalculatedKubeEvent event = new OutputCalculatedKubeEvent(worker, pos, level, rpm, stress);
        HorsePowerKubeEvents.OUTPUT_CALCULATED.post(ScriptType.SERVER, event);
        return new float[]{event.getRpmMultiplier(), event.getStressMultiplier()};
    }

    public static float[] firePathEvaluated(BlockPos pos, Level level, PathEvaluator.Result result) {
        PathEvaluatedKubeEvent event = new PathEvaluatedKubeEvent(pos, level, result);
        HorsePowerKubeEvents.PATH_EVALUATED.post(ScriptType.SERVER, event);
        return new float[]{event.getSpeedMultiplier(), event.getStressMultiplier()};
    }
}
