package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.steampn.createhorsepower.compat.kubejs.KubeJSCompat;
import net.steampn.createhorsepower.content.path.PathEvaluator;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.ScriptHooks;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge script-integration facade. Registered into {@link net.steampn.createhorsepower.platform.CHPApi}
 * at construction so shared engine logic can fire KubeJS events without depending on the loader.
 */
public final class OptionalIntegrations implements ScriptHooks {
    public static final OptionalIntegrations INSTANCE = new OptionalIntegrations();

    private static final boolean KUBE_JS_LOADED = isKubeJsPresent();

    private static boolean isKubeJsPresent() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("kubejs");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private OptionalIntegrations() {}

    @Override
    public boolean fireBeforeAttach(@Nullable Player player, Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        if (!KUBE_JS_LOADED) {
            return true;
        }
        return KubeJSCompat.fireBeforeAttach(player, worker, pos, level, profile);
    }

    @Override
    public void fireWorkerAttached(Mob worker, BlockPos pos, Level level, WorkerResolver.ResolvedWorker profile) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkerAttached(worker, pos, level, profile);
        }
    }

    @Override
    public void fireWorkerDetached(@Nullable Mob worker, BlockPos pos, Level level) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkerDetached(worker, pos, level);
        }
    }

    @Override
    public boolean fireBeforeWorkStart(Mob worker, BlockPos pos, Level level) {
        if (!KUBE_JS_LOADED) {
            return true;
        }
        return KubeJSCompat.fireBeforeWorkStart(worker, pos, level);
    }

    @Override
    public void fireWorkStarted(Mob worker, BlockPos pos, Level level) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkStarted(worker, pos, level);
        }
    }

    @Override
    public void fireWorkStopped(BlockPos pos, Level level) {
        if (KUBE_JS_LOADED) {
            KubeJSCompat.fireWorkStopped(pos, level);
        }
    }

    @Override
    public float[] fireOutputCalculated(Mob worker, BlockPos pos, Level level, float rpm, float stress) {
        if (KUBE_JS_LOADED) {
            return KubeJSCompat.fireOutputCalculated(worker, pos, level, rpm, stress);
        }
        return new float[]{1.0f, 1.0f};
    }

    @Override
    public float[] firePathEvaluated(BlockPos pos, Level level, PathEvaluator.Result result) {
        if (KUBE_JS_LOADED) {
            return KubeJSCompat.firePathEvaluated(pos, level, result);
        }
        return new float[]{1.0f, 1.0f};
    }
}
