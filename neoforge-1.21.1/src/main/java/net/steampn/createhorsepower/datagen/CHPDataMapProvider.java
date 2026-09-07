package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.steampn.createhorsepower.content.stats.PathStats;
import net.steampn.createhorsepower.content.stats.BuiltinProfiles;
import net.steampn.createhorsepower.registry.CHPDataMaps;

import java.util.concurrent.CompletableFuture;

public class CHPDataMapProvider extends DataMapProvider {
    public CHPDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(packOutput, lookupProvider);
    }

    @Override
    protected void gather() {
        // Worker defaults intentionally live in common code instead of a bundled
        // Data Map. This keeps Data Maps as an explicit pack override layer and
        // lets migrated server configs still control CE's bundled profiles.
        var pathBuilder = this.builder(CHPDataMaps.PATH_STATS);
        pathBuilder.add(Blocks.DIRT_PATH.builtInRegistryHolder(), PathStats.NORMAL, false);
        pathBuilder.add(Blocks.DIRT.builtInRegistryHolder(), BuiltinProfiles.DIRT, false);
        pathBuilder.add(Blocks.COARSE_DIRT.builtInRegistryHolder(), BuiltinProfiles.COARSE_DIRT, false);
        pathBuilder.add(Blocks.GRAVEL.builtInRegistryHolder(), BuiltinProfiles.GRAVEL, false);
        pathBuilder.add(Blocks.STONE_BRICKS.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.MOSSY_STONE_BRICKS.builtInRegistryHolder(), BuiltinProfiles.MOSSY_STONE_BRICKS, false);
        pathBuilder.add(Blocks.CRACKED_STONE_BRICKS.builtInRegistryHolder(), BuiltinProfiles.CRACKED_STONE_BRICKS, false);
        pathBuilder.add(Blocks.COBBLESTONE.builtInRegistryHolder(), PathStats.of(1.00f, 1.00f), false);
        pathBuilder.add(Blocks.MOSSY_COBBLESTONE.builtInRegistryHolder(), PathStats.of(1.00f, 1.00f), false);
        pathBuilder.add(Blocks.POLISHED_ANDESITE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.POLISHED_DIORITE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.POLISHED_GRANITE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.POLISHED_DEEPSLATE.builtInRegistryHolder(), PathStats.GREAT, false);
        pathBuilder.add(Blocks.SMOOTH_STONE.builtInRegistryHolder(), PathStats.GREAT, false);
    }
}
