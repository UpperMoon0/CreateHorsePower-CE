package net.steampn.createhorsepower.utils;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.platform.CHPApi;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/** Gated, transition-oriented field diagnostics. Normal steady-state ticks are never logged. */
public final class CHPDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CHPDiagnostics() {}

    public static boolean enabled() {
        try {
            return CHPApi.config().debugLogging();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void event(
            String event,
            @Nullable Level level,
            @Nullable BlockPos crankPos,
            @Nullable UUID crankUuid,
            @Nullable Mob worker,
            String detail
    ) {
        if (!enabled()) return;
        String dimension = level == null ? "<none>" : level.dimension().location().toString();
        String workerType = worker == null ? "<none>" : BuiltInRegistries.ENTITY_TYPE.getKey(worker.getType()).toString();
        String workerUuid = worker == null ? "<none>" : worker.getUUID().toString();
        // INFO is intentionally gated by debugLogging so diagnostics remain visible
        // without requiring pack/server owners to reconfigure Log4j's root level.
        LOGGER.info("[CHP debug] event={} dimension={} crank={} crank_uuid={} worker_type={} worker_uuid={} {}",
                event, dimension, crankPos, crankUuid, workerType, workerUuid, detail == null ? "" : detail);
    }

    public static void warnInvariant(String event, @Nullable Level level, @Nullable BlockPos crankPos, String detail) {
        String dimension = level == null ? "<none>" : level.dimension().location().toString();
        LOGGER.warn("[CHP invariant] event={} dimension={} crank={} {}", event, dimension, crankPos, detail);
    }
}
