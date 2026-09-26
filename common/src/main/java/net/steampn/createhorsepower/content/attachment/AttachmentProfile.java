package net.steampn.createhorsepower.content.attachment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.steampn.createhorsepower.content.stats.WorkerStats;

import java.util.Set;

/** Immutable datapack-defined attachment/harness profile. */
public record AttachmentProfile(
        ResourceLocation id,
        int priority,
        Set<ResourceLocation> items,
        Set<ResourceLocation> itemTags,
        AttachmentMode mode,
        float maxWorkingRadius,
        Set<ResourceLocation> workers,
        Set<ResourceLocation> workerTags,
        Set<ResourceLocation> machines,
        boolean consumeOnAttach,
        boolean dropOnDetach,
        float outputMultiplier
) {
    public AttachmentProfile {
        if (id == null || mode == null) throw new IllegalArgumentException("attachment profile id/mode cannot be null");
        items = Set.copyOf(items);
        itemTags = Set.copyOf(itemTags);
        workers = Set.copyOf(workers);
        workerTags = Set.copyOf(workerTags);
        machines = Set.copyOf(machines);
        WorkerStats.validateRadius(maxWorkingRadius);
        WorkerStats.validatePositiveFinite(outputMultiplier, "attachment output_multiplier");
        if (items.isEmpty() && itemTags.isEmpty()) {
            throw new IllegalArgumentException("attachment profile " + id + " must select at least one item or item tag");
        }
    }

    public boolean matchesItem(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (items.contains(itemId)) return true;
        var holder = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
        for (ResourceLocation tagId : itemTags) {
            if (holder.is(TagKey.create(Registries.ITEM, tagId))) return true;
        }
        return false;
    }

    public boolean matchesExactItem(ItemStack stack) {
        return items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public boolean supportsMachine(ResourceLocation machineId) {
        return machines.isEmpty() || machines.contains(machineId);
    }

    public boolean supportsWorker(Mob mob) {
        if (workers.isEmpty() && workerTags.isEmpty()) return true;
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (workers.contains(entityId)) return true;
        for (ResourceLocation tagId : workerTags) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
            if (mob.getType().is(tag)) return true;
        }
        return false;
    }
}
