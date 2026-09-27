package net.steampn.createhorsepower.content.stats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Loader-neutral datapack worker profiles shared by Forge and NeoForge. */
public final class WorkerProfileRegistry {
    public record Entry(ResourceLocation id, ResourceLocation entityId, int priority, WorkerStats stats) {}

    private static volatile Map<ResourceLocation, Entry> BY_ENTITY = Map.of();

    private WorkerProfileRegistry() {}

    public static void replace(Collection<Entry> entries) {
        Map<ResourceLocation, Entry> resolved = new HashMap<>();
        for (Entry entry : entries) {
            resolved.merge(entry.entityId(), entry, WorkerProfileRegistry::preferred);
        }
        BY_ENTITY = Map.copyOf(resolved);
    }


    private static Entry preferred(Entry left, Entry right) {
        if (left.priority() != right.priority()) {
            return left.priority() > right.priority() ? left : right;
        }
        return left.id().toString().compareTo(right.id().toString()) <= 0 ? left : right;
    }

    public static Optional<WorkerStats> get(ResourceLocation entityId) {
        Entry entry = BY_ENTITY.get(entityId);
        return entry == null ? Optional.empty() : Optional.of(entry.stats());
    }

    public static Optional<WorkerStats> get(EntityType<?> type) {
        return get(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static void clear() {
        BY_ENTITY = Map.of();
    }
}
