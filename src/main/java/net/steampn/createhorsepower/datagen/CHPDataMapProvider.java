package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;
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
        workerBuilder.add(CHPTags.Entities.WORKERS_SMALL, WorkerStats.SMALL_DEFAULT, false);
        workerBuilder.add(CHPTags.Entities.WORKERS_MEDIUM, WorkerStats.MEDIUM_DEFAULT, false);
        workerBuilder.add(CHPTags.Entities.WORKERS_LARGE, WorkerStats.LARGE_DEFAULT, false);

        // Specific entity profiles with individual attribute scaling
        workerBuilder.add(EntityType.HORSE.builtInRegistryHolder(),
                new WorkerStats(5.0f, 600.0f, 2.5f, 0.75f, 0.25f, true, false), false);
        workerBuilder.add(EntityType.DONKEY.builtInRegistryHolder(),
                new WorkerStats(4.0f, 650.0f, 2.5f, 0.50f, 0.30f, true, false), false);
        workerBuilder.add(EntityType.MULE.builtInRegistryHolder(),
                new WorkerStats(4.5f, 700.0f, 2.5f, 0.60f, 0.35f, true, false), false);
        workerBuilder.add(EntityType.CAMEL.builtInRegistryHolder(),
                new WorkerStats(4.0f, 750.0f, 2.5f, 0.40f, 0.40f, true, false), false);
        workerBuilder.add(EntityType.LLAMA.builtInRegistryHolder(),
                new WorkerStats(3.5f, 350.0f, 2.5f, 0.30f, 0.20f, true, false), false);
        workerBuilder.add(EntityType.TRADER_LLAMA.builtInRegistryHolder(),
                new WorkerStats(3.5f, 350.0f, 2.5f, 0.30f, 0.20f, true, false), false);
        workerBuilder.add(EntityType.COW.builtInRegistryHolder(),
                new WorkerStats(3.0f, 300.0f, 2.5f, 0.0f, 0.20f, false, false), false);
        workerBuilder.add(EntityType.PIG.builtInRegistryHolder(),
                new WorkerStats(3.5f, 200.0f, 2.5f, 0.20f, 0.10f, false, false), false);
        workerBuilder.add(EntityType.SHEEP.builtInRegistryHolder(),
                new WorkerStats(3.0f, 180.0f, 2.5f, 0.0f, 0.10f, false, false), false);
        workerBuilder.add(EntityType.WOLF.builtInRegistryHolder(),
                new WorkerStats(4.0f, 150.0f, 2.5f, 0.0f, 0.0f, true, false), false);

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
