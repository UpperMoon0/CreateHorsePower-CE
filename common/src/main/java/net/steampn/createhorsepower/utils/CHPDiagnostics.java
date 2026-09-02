package net.steampn.createhorsepower.utils;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.steampn.createhorsepower.platform.CHPApi;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Gated, transition-oriented field diagnostics. Normal steady-state ticks are never logged. */
public final class CHPDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long WORK_START_VETO_REMINDER_TICKS = 1200L;
    private static final Map<RateLimitKey, Long> NEXT_RATE_LIMITED_EVENT = new HashMap<>();

    private record RateLimitKey(
            String event,
            String dimension,
            @Nullable BlockPos crankPos,
            @Nullable UUID crankUuid,
            @Nullable UUID workerUuid
    ) {}

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
        if (!enabled() || !allowEvent(event, level, crankPos, crankUuid, worker)) return;
        String dimension = level == null ? "<none>" : level.dimension().location().toString();
        String workerType = worker == null ? "<none>" : BuiltInRegistries.ENTITY_TYPE.getKey(worker.getType()).toString();
        String workerUuid = worker == null ? "<none>" : worker.getUUID().toString();
        // INFO is intentionally gated by debugLogging so diagnostics remain visible
        // without requiring pack/server owners to reconfigure Log4j's root level.
        LOGGER.info("[CHP debug] event={} dimension={} crank={} crank_uuid={} worker_type={} worker_uuid={} {}",
                event, dimension, crankPos, crankUuid, workerType, workerUuid, detail == null ? "" : detail);
    }

    private static boolean allowEvent(
            String event,
            @Nullable Level level,
            @Nullable BlockPos crankPos,
            @Nullable UUID crankUuid,
            @Nullable Mob worker
    ) {
        // Script hooks intentionally retry once per second. A persistent veto is
        // a steady state, not a new transition, so emit the first failure and
        // only a slow reminder afterwards instead of one INFO line per retry.
        if (!"work_start_vetoed".equals(event) || level == null) {
            return true;
        }

        long now = level.getGameTime();
        RateLimitKey key = new RateLimitKey(
                event,
                level.dimension().location().toString(),
                crankPos == null ? null : crankPos.immutable(),
                crankUuid,
                worker == null ? null : worker.getUUID()
        );
        long next = NEXT_RATE_LIMITED_EVENT.getOrDefault(key, Long.MIN_VALUE);
        if (now < next) {
            return false;
        }
        NEXT_RATE_LIMITED_EVENT.put(key, now + WORK_START_VETO_REMINDER_TICKS);
        return true;
    }

    /** Clear process-local diagnostic throttles when a server instance stops. */
    public static void clearRuntimeState() {
        NEXT_RATE_LIMITED_EVENT.clear();
    }

    public static void warnInvariant(String event, @Nullable Level level, @Nullable BlockPos crankPos, String detail) {
        String dimension = level == null ? "<none>" : level.dimension().location().toString();
        LOGGER.warn("[CHP invariant] event={} dimension={} crank={} {}", event, dimension, crankPos, detail);
    }
}
