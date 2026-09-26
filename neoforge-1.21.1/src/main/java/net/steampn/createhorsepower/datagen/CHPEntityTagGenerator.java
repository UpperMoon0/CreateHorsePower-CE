package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.steampn.createhorsepower.utils.CHPTags;
import net.steampn.createhorsepower.datagen.CHPDataDefinitions;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

import static net.steampn.createhorsepower.CreateHorsePower.MODID;

public class CHPEntityTagGenerator extends EntityTypeTagsProvider {
    public CHPEntityTagGenerator(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> completableFuture, @Nullable ExistingFileHelper existingFileHelper) {
        super(packOutput, completableFuture, MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(CHPTags.Entities.WORKERS_SMALL)
                .add(CHPDataDefinitions.SMALL_WORKERS);
        this.tag(CHPTags.Entities.SMALL_WORKER_TAG)
                .addTag(CHPTags.Entities.WORKERS_SMALL);

        this.tag(CHPTags.Entities.WORKERS_MEDIUM)
                .add(CHPDataDefinitions.MEDIUM_WORKERS);
        this.tag(CHPTags.Entities.MEDIUM_WORKER_TAG)
                .addTag(CHPTags.Entities.WORKERS_MEDIUM);

        this.tag(CHPTags.Entities.WORKERS_LARGE)
                .add(CHPDataDefinitions.LARGE_WORKERS);
        this.tag(CHPTags.Entities.LARGE_WORKER_TAG)
                .addTag(CHPTags.Entities.WORKERS_LARGE);
    }
}
