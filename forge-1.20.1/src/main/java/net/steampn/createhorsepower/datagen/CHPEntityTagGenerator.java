package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.steampn.createhorsepower.utils.CHPTags;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

import static net.steampn.createhorsepower.CreateHorsePower.MODID;

public final class CHPEntityTagGenerator extends EntityTypeTagsProvider {
    public CHPEntityTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                 @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(CHPTags.Entities.WORKERS_SMALL).add(CHPDataDefinitions.SMALL_WORKERS);
        tag(CHPTags.Entities.SMALL_WORKER_TAG).addTag(CHPTags.Entities.WORKERS_SMALL);
        tag(CHPTags.Entities.WORKERS_MEDIUM).add(CHPDataDefinitions.MEDIUM_WORKERS);
        tag(CHPTags.Entities.MEDIUM_WORKER_TAG).addTag(CHPTags.Entities.WORKERS_MEDIUM);
        tag(CHPTags.Entities.WORKERS_LARGE).add(CHPDataDefinitions.LARGE_WORKERS);
        tag(CHPTags.Entities.LARGE_WORKER_TAG).addTag(CHPTags.Entities.WORKERS_LARGE);
    }
}
