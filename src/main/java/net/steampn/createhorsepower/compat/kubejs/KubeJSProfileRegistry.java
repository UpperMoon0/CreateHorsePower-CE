package net.steampn.createhorsepower.compat.kubejs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class KubeJSProfileRegistry {
    private static final Map<ResourceLocation, WorkerStats> WORKER_PROFILES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, PathStats> PATH_BLOCK_PROFILES = new ConcurrentHashMap<>();
    private static final Map<TagKey<Block>, PathStats> PATH_TAG_PROFILES = new ConcurrentHashMap<>();

    public static void registerWorker(ResourceLocation id, WorkerStats stats) {
        WORKER_PROFILES.put(id, stats);
    }

    public static void registerPathBlock(ResourceLocation id, PathStats stats) {
        PATH_BLOCK_PROFILES.put(id, stats);
    }

    public static void registerPathTag(TagKey<Block> tag, PathStats stats) {
        PATH_TAG_PROFILES.put(tag, stats);
    }

    public static Optional<WorkerStats> getWorker(ResourceLocation id) {
        return Optional.ofNullable(WORKER_PROFILES.get(id));
    }

    public static Optional<WorkerStats> getWorker(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return getWorker(id);
    }

    public static Optional<PathStats> getPathBlock(ResourceLocation id) {
        return Optional.ofNullable(PATH_BLOCK_PROFILES.get(id));
    }

    public static Optional<PathStats> getPathBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        PathStats stats = PATH_BLOCK_PROFILES.get(id);
        if (stats != null) {
            return Optional.of(stats);
        }

        for (Map.Entry<TagKey<Block>, PathStats> entry : PATH_TAG_PROFILES.entrySet()) {
            if (BuiltInRegistries.BLOCK.wrapAsHolder(block).is(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    public static void clear() {
        WORKER_PROFILES.clear();
        PATH_BLOCK_PROFILES.clear();
        PATH_TAG_PROFILES.clear();
    }

    private KubeJSProfileRegistry() {}
}
