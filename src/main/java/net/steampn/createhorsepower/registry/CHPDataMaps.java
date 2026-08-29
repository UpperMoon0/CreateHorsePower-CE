package net.steampn.createhorsepower.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.steampn.createhorsepower.CreateHorsePower;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.WorkerStats;

public class CHPDataMaps {
    public static final DataMapType<EntityType<?>, WorkerStats> WORKER_STATS = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateHorsePower.MODID, "worker_stats"),
            Registries.ENTITY_TYPE,
            WorkerStats.CODEC
    ).synced(WorkerStats.CODEC, false).build();

    public static final DataMapType<Block, PathStats> PATH_STATS = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateHorsePower.MODID, "path_stats"),
            Registries.BLOCK,
            PathStats.CODEC
    ).synced(PathStats.CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(WORKER_STATS);
        event.register(PATH_STATS);
    }
}
