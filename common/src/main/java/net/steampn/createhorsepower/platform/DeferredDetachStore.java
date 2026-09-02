package net.steampn.createhorsepower.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Level-persistent detach intents for workers that are unloaded when an addon
 * or crank requests detach. The crank block entity is deliberately not the
 * durable owner: it may be broken or replaced before the worker reloads.
 */
public interface DeferredDetachStore {

    record Entry(BlockPos crankPos, UUID crankUuid, boolean dropLead) {
        public boolean matches(BlockPos pos, UUID uuid) {
            return crankPos.equals(pos) && crankUuid.equals(uuid);
        }
    }

    void put(ServerLevel level, UUID workerUuid, Entry entry);

    @Nullable
    Entry get(ServerLevel level, UUID workerUuid);

    @Nullable
    Entry remove(ServerLevel level, UUID workerUuid);
}
