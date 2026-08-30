package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;
import net.steampn.createhorsepower.content.stats.BuiltinProfiles;
import net.steampn.createhorsepower.registry.CHPDataMaps;
import net.steampn.createhorsepower.utils.CHPTags;

import java.util.concurrent.CompletableFuture;

public class CHPDataMapProvider extends DataMapProvider {
    public CHPDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }

    @Override
    protected void gather() {
        var workerBuilder = this.builder(CHPDataMaps.WORKER_STATS);

        // Tier tags
        workerBuilder.add(CHPTags.Entities.WORKERS_SMALL,
                BuiltinProfiles.SMALL, false);
        workerBuilder.add(CHPTags.Entities.WORKERS_MEDIUM,
                BuiltinProfiles.MEDIUM, false);
        workerBuilder.add(CHPTags.Entities.WORKERS_LARGE,
                BuiltinProfiles.LARGE, false);

        // Specific entity profiles with individual attribute scaling and reference values
        workerBuilder.add(EntityType.HORSE.builtInRegistryHolder(),
                BuiltinProfiles.HORSE, false);
        workerBuilder.add(EntityType.DONKEY.builtInRegistryHolder(),
                BuiltinProfiles.DONKEY, false);
        workerBuilder.add(EntityType.MULE.builtInRegistryHolder(),
                BuiltinProfiles.MULE, false);
        workerBuilder.add(EntityType.CAMEL.builtInRegistryHolder(),
                BuiltinProfiles.CAMEL, false);
        workerBuilder.add(EntityType.LLAMA.builtInRegistryHolder(),
                BuiltinProfiles.LLAMA, false);
        workerBuilder.add(EntityType.TRADER_LLAMA.builtInRegistryHolder(),
                BuiltinProfiles.LLAMA, false);
        workerBuilder.add(EntityType.COW.builtInRegistryHolder(),
                BuiltinProfiles.COW, false);
        workerBuilder.add(EntityType.PIG.builtInRegistryHolder(),
                BuiltinProfiles.PIG, false);
        workerBuilder.add(EntityType.SHEEP.builtInRegistryHolder(),
                BuiltinProfiles.SHEEP, false);
        workerBuilder.add(EntityType.WOLF.builtInRegistryHolder(),
                BuiltinProfiles.WOLF, false);

        var pathBuilder = this.builder(CHPDataMaps.PATH_STATS);
        pathBuilder.add(Blocks.DIRT_PATH.builtInRegistryHolder(), PathStats.NORMAL, false);
        pathBuilder.add(Blocks.DIRT.builtInRegistryHolder(), PathStats.of(0.70f, 0.90f), false);
        pathBuilder.add(Blocks.COARSE_DIRT.builtInRegistryHolder(), PathStats.of(0.75f, 0.90f), false);
        pathBuilder.add(Blocks.GRAVEL.builtInRegistryHolder(), PathStats.of(1.10f, 1.00f), false);
        pathBuilder.add(Blocks.STONE_BRICKS.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.MOSSY_STONE_BRICKS.builtInRegistryHolder(), PathStats.of(1.15f, 1.05f), false);
        pathBuilder.add(Blocks.CRACKED_STONE_BRICKS.builtInRegistryHolder(), PathStats.of(1.10f, 1.00f), false);
        pathBuilder.add(Blocks.COBBLESTONE.builtInRegistryHolder(), PathStats.of(1.00f, 1.00f), false);
        pathBuilder.add(Blocks.MOSSY_COBBLESTONE.builtInRegistryHolder(), PathStats.of(1.00f, 1.00f), false);
        pathBuilder.add(Blocks.POLISHED_ANDESITE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.POLISHED_DIORITE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.POLISHED_GRANITE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.POLISHED_DEEPSLATE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.SMOOTH_STONE.builtInRegistryHolder(), PathStats.GREAT, false);
    }
}
