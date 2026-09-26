package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.steampn.createhorsepower.registry.BlockRegister;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

import static net.steampn.createhorsepower.CreateHorsePower.MODID;

public final class CHPBlockTagGenerator extends BlockTagsProvider {
    public CHPBlockTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(BlockRegister.HORSE_CRANK.get());
        tag(BlockTags.NEEDS_STONE_TOOL).add(BlockRegister.HORSE_CRANK.get());
        tag(BlockTags.FENCES).add(BlockRegister.HORSE_CRANK.get());
    }
}
