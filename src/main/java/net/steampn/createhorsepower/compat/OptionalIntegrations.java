package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.steampn.createhorsepower.compat.kubejs.KubeJSCompat;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import org.jetbrains.annotations.Nullable;

public final class OptionalIntegrations {
    private static final boolean KUBE_JS_LOADED = isKubeJsPresent();

    private static boolean isKubeJsPresent() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean fireBeforeAttach(@Nullable Player player, Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        if (!KUBE_JS_LOADED) {
            return true;
        }
        return KubeJSCompat.fireBeforeAttach(player, worker, pos, level, profile);
    }

    public static void fireWorkerAttached(Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkerAttached(worker, pos, level, profile);
        }
    }

    public static void fireWorkerDetached(@Nullable Mob worker, BlockPos pos, Level level) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkerDetached(worker, pos, level);
        }
    }

    public static boolean fireWorkStarted(Mob worker, BlockPos pos, Level level) {
        if (!KUBE_JS_LOADED) {
            return true;
        }
        return KubeJSCompat.fireWorkStarted(worker, pos, level);
    }

    public static void fireWorkStopped(BlockPos pos, Level level) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkStopped(pos, level);
        }
    }

    public static float[] fireOutputCalculated(Mob worker, BlockPos pos, Level level, float rpm, float stress) {
        if (KUBE_JS_LOADED) {
            return KubeJSCompat.fireOutputCalculated(worker, pos, level, rpm, stress);
        }
        return new float[]{1.0f, 1.0f};
    }

    public static float[] firePathEvaluated(BlockPos pos, Level level, PathEvaluator.Result result) {
        if (KUBE_JS_LOADED) {
            return KubeJSCompat.firePathEvaluated(pos, level, result);
        }
        return new float[]{1.0f, 1.0f};
    }

    private OptionalIntegrations() {}
}
