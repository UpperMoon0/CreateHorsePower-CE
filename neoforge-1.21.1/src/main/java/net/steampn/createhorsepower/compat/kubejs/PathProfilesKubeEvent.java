package net.steampn.createhorsepower.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.steampn.createhorsepower.content.stats.PathStats;

import java.util.Map;

public class PathProfilesKubeEvent implements KubeEvent {

    public void add(String blockId, Map<String, Object> properties) {
        ResourceLocation id = ResourceLocation.parse(blockId);
        float speed = properties.containsKey("speedMultiplier") ? ((Number) properties.get("speedMultiplier")).floatValue() : 1.0f;
        float stress = properties.containsKey("stressMultiplier") ? ((Number) properties.get("stressMultiplier")).floatValue() : 1.0f;
        KubeJSProfileRegistry.registerPathBlock(id, PathStats.of(speed, stress));
    }

    public void add(String blockId, PathStats stats) {
        KubeJSProfileRegistry.registerPathBlock(ResourceLocation.parse(blockId), stats);
    }

    public void addTag(String tagId, Map<String, Object> properties) {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagId));
        float speed = properties.containsKey("speedMultiplier") ? ((Number) properties.get("speedMultiplier")).floatValue() : 1.0f;
        float stress = properties.containsKey("stressMultiplier") ? ((Number) properties.get("stressMultiplier")).floatValue() : 1.0f;
        KubeJSProfileRegistry.registerPathTag(tag, PathStats.of(speed, stress));
    }

    public void addTag(String tagId, PathStats stats) {
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagId));
        KubeJSProfileRegistry.registerPathTag(tag, stats);
    }
}
