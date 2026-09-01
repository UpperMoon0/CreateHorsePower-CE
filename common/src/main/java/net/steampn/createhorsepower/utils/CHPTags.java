package net.steampn.createhorsepower.utils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.steampn.createhorsepower.CHPConstants;
import net.steampn.createhorsepower.platform.CHPApi;

public class CHPTags {
    public static class Entities {
        public static final TagKey<EntityType<?>> WORKERS_SMALL = tag("workers/small");
        public static final TagKey<EntityType<?>> WORKERS_MEDIUM = tag("workers/medium");
        public static final TagKey<EntityType<?>> WORKERS_LARGE = tag("workers/large");

        public static final TagKey<EntityType<?>> SMALL_WORKER_TAG = tag("worker_small");
        public static final TagKey<EntityType<?>> MEDIUM_WORKER_TAG = tag("worker_medium");
        public static final TagKey<EntityType<?>> LARGE_WORKER_TAG = tag("worker_large");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, modResource(name));
        }
    }

    public static class Items {
        public static final TagKey<Item> WORKER_LEASHES = tag("worker_leashes");
        public static final TagKey<Item> ATTACHMENT_ITEMS = tag("attachment_items");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, modResource(name));
        }
    }

    private static ResourceLocation modResource(String name) {
        return CHPApi.id(CHPConstants.MODID, name);
    }
}
