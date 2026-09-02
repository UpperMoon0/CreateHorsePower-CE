package net.steampn.createhorsepower.content.stats;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.Optional;

/** Canonical built-in profiles shared by every supported loader. */
public final class BuiltinProfiles {
    private BuiltinProfiles() {}

    public static final WorkerStats SMALL = new WorkerStats(4.0f, 128.0f, 2.5f, 0.0f, 0.225f, 0.0f, 20.0f, false, false);
    public static final WorkerStats MEDIUM = new WorkerStats(4.0f, 256.0f, 2.5f, 0.0f, 0.225f, 0.0f, 20.0f, false, false);
    public static final WorkerStats LARGE = new WorkerStats(4.0f, 512.0f, 2.5f, 0.5f, 0.225f, 0.2f, 20.0f, false, false);

    public static final WorkerStats HORSE = new WorkerStats(5.0f, 600.0f, 2.5f, 0.75f, 0.225f, 0.25f, 22.0f, false, false);
    public static final WorkerStats DONKEY = new WorkerStats(4.0f, 650.0f, 2.5f, 0.50f, 0.175f, 0.30f, 20.0f, false, false);
    public static final WorkerStats MULE = new WorkerStats(4.5f, 700.0f, 2.5f, 0.60f, 0.200f, 0.35f, 24.0f, false, false);
    public static final WorkerStats CAMEL = new WorkerStats(4.0f, 750.0f, 2.5f, 0.40f, 0.090f, 0.40f, 32.0f, false, false);
    public static final WorkerStats LLAMA = new WorkerStats(3.5f, 350.0f, 2.5f, 0.30f, 0.175f, 0.20f, 20.0f, false, false);
    public static final WorkerStats COW = new WorkerStats(3.0f, 300.0f, 2.5f, 0.0f, 0.200f, 0.20f, 10.0f, false, false);
    public static final WorkerStats PIG = new WorkerStats(3.5f, 200.0f, 2.5f, 0.20f, 0.200f, 0.10f, 10.0f, false, false);
    public static final WorkerStats SHEEP = new WorkerStats(3.0f, 180.0f, 2.5f, 0.0f, 0.200f, 0.10f, 8.0f, false, false);
    public static final WorkerStats WOLF = new WorkerStats(4.0f, 150.0f, 2.5f, 0.0f, 0.300f, 0.0f, 20.0f, false, false);

    public static final PathStats DIRT = PathStats.of(0.70f, 0.90f);
    public static final PathStats COARSE_DIRT = PathStats.of(0.75f, 0.90f);
    public static final PathStats GRAVEL = PathStats.of(1.10f, 1.00f);
    public static final PathStats MOSSY_STONE_BRICKS = PathStats.of(1.15f, 1.05f);
    public static final PathStats CRACKED_STONE_BRICKS = PathStats.of(1.10f, 1.00f);

    private static final Map<EntityType<?>, WorkerStats> WORKERS = Map.ofEntries(
            Map.entry(EntityType.HORSE, HORSE), Map.entry(EntityType.DONKEY, DONKEY),
            Map.entry(EntityType.MULE, MULE), Map.entry(EntityType.CAMEL, CAMEL),
            Map.entry(EntityType.LLAMA, LLAMA), Map.entry(EntityType.TRADER_LLAMA, LLAMA),
            Map.entry(EntityType.COW, COW), Map.entry(EntityType.PIG, PIG),
            Map.entry(EntityType.SHEEP, SHEEP), Map.entry(EntityType.WOLF, WOLF));

    // Optional compatibility by registry ID keeps TFC completely absent from
    // CHP's compile/runtime dependency graph. TFC 1.20.x and 1.21.x register
    // horse, donkey and mule under these stable IDs.
    private static final Map<String, WorkerStats> OPTIONAL_WORKERS = Map.ofEntries(
            Map.entry("tfc:horse", HORSE),
            Map.entry("tfc:donkey", DONKEY),
            Map.entry("tfc:mule", MULE),
            Map.entry("tfc:cow", COW),
            Map.entry("tfc:pig", PIG),
            Map.entry("tfc:sheep", SHEEP),
            // TFC 1.21.x camel variants extend vanilla Camel through AbstractCamel.
            Map.entry("tfc:dromedary_camel", CAMEL),
            Map.entry("tfc:bactrian_camel", CAMEL));

    private static final Map<Block, PathStats> PATHS = Map.ofEntries(
            Map.entry(Blocks.DIRT_PATH, PathStats.NORMAL), Map.entry(Blocks.DIRT, DIRT),
            Map.entry(Blocks.COARSE_DIRT, COARSE_DIRT), Map.entry(Blocks.GRAVEL, GRAVEL),
            Map.entry(Blocks.STONE_BRICKS, PathStats.GREAT), Map.entry(Blocks.MOSSY_STONE_BRICKS, MOSSY_STONE_BRICKS),
            Map.entry(Blocks.CRACKED_STONE_BRICKS, CRACKED_STONE_BRICKS), Map.entry(Blocks.COBBLESTONE, PathStats.NORMAL),
            Map.entry(Blocks.MOSSY_COBBLESTONE, PathStats.NORMAL), Map.entry(Blocks.POLISHED_ANDESITE, PathStats.GREAT),
            Map.entry(Blocks.POLISHED_DIORITE, PathStats.GREAT), Map.entry(Blocks.POLISHED_GRANITE, PathStats.GREAT),
            Map.entry(Blocks.POLISHED_DEEPSLATE, PathStats.GREAT), Map.entry(Blocks.SMOOTH_STONE, PathStats.GREAT));

    public static Optional<WorkerStats> worker(EntityType<?> type) {
        WorkerStats exact = WORKERS.get(type);
        if (exact != null) {
            return Optional.of(exact);
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        return optionalWorker(id);
    }

    static Optional<WorkerStats> optionalWorker(String entityId) {
        return Optional.ofNullable(OPTIONAL_WORKERS.get(entityId));
    }

    public static Optional<PathStats> path(Block block) {
        return Optional.ofNullable(PATHS.get(block));
    }
}
