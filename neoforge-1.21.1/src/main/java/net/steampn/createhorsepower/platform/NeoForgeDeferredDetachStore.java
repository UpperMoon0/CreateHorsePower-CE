package net.steampn.createhorsepower.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** NeoForge 1.21.1 SavedData backend for deferred worker detach intents. */
public final class NeoForgeDeferredDetachStore implements DeferredDetachStore {
    private static final String DATA_NAME = "createhorsepower_deferred_detaches";
    private static final SavedData.Factory<Data> FACTORY = new SavedData.Factory<>(
            Data::new,
            Data::load,
            DataFixTypes.LEVEL
    );

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Override
    public void put(ServerLevel level, UUID workerUuid, Entry entry) {
        data(level).put(workerUuid, entry);
    }

    @Override
    public @Nullable Entry get(ServerLevel level, UUID workerUuid) {
        return data(level).get(workerUuid);
    }

    @Override
    public @Nullable Entry remove(ServerLevel level, UUID workerUuid) {
        return data(level).remove(workerUuid);
    }

    private static final class Data extends SavedData {
        private final Map<UUID, Entry> entries = new HashMap<>();

        static Data load(CompoundTag root, HolderLookup.Provider registries) {
            Data data = new Data();
            CompoundTag entriesTag = root.getCompound("Entries");
            for (String key : entriesTag.getAllKeys()) {
                try {
                    UUID workerUuid = UUID.fromString(key);
                    CompoundTag tag = entriesTag.getCompound(key);
                    if (!tag.contains("CrankPos") || !tag.hasUUID("CrankUuid")) {
                        continue;
                    }
                    data.entries.put(workerUuid, new Entry(
                            BlockPos.of(tag.getLong("CrankPos")),
                            tag.getUUID("CrankUuid"),
                            tag.getBoolean("DropLead")
                    ));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy/user-edited records instead of preventing world load.
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
            CompoundTag entriesTag = new CompoundTag();
            entries.forEach((workerUuid, entry) -> {
                CompoundTag tag = new CompoundTag();
                tag.putLong("CrankPos", entry.crankPos().asLong());
                tag.putUUID("CrankUuid", entry.crankUuid());
                tag.putBoolean("DropLead", entry.dropLead());
                entriesTag.put(workerUuid.toString(), tag);
            });
            root.put("Entries", entriesTag);
            return root;
        }

        @Nullable
        Entry get(UUID workerUuid) {
            return entries.get(workerUuid);
        }

        void put(UUID workerUuid, Entry entry) {
            entries.put(workerUuid, entry);
            setDirty();
        }

        @Nullable
        Entry remove(UUID workerUuid) {
            Entry removed = entries.remove(workerUuid);
            if (removed != null) {
                setDirty();
            }
            return removed;
        }
    }
}
