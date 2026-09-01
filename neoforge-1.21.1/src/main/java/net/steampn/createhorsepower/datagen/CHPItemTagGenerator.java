package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

import static net.steampn.createhorsepower.CreateHorsePower.MODID;

public class CHPItemTagGenerator extends ItemTagsProvider {
    public CHPItemTagGenerator(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> providerCompletableFuture,
                               CompletableFuture<TagLookup<Block>> completableFuture, @Nullable net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper) {
        super(packOutput, providerCompletableFuture, completableFuture, MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(net.steampn.createhorsepower.utils.CHPTags.Items.WORKER_LEASHES)
                .add(net.minecraft.world.item.Items.LEAD);
        tag(net.steampn.createhorsepower.utils.CHPTags.Items.ATTACHMENT_ITEMS)
                .addTag(net.steampn.createhorsepower.utils.CHPTags.Items.WORKER_LEASHES);
    }
}
