package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.steampn.createhorsepower.utils.CHPTags;
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
                .add(EntityType.WOLF, EntityType.CAT, EntityType.OCELOT, EntityType.FOX);
        this.tag(CHPTags.Entities.SMALL_WORKER_TAG)
                .addTag(CHPTags.Entities.WORKERS_SMALL);

        this.tag(CHPTags.Entities.WORKERS_MEDIUM)
                .add(EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.GOAT, EntityType.LLAMA, EntityType.TRADER_LLAMA);
        this.tag(CHPTags.Entities.MEDIUM_WORKER_TAG)
                .addTag(CHPTags.Entities.WORKERS_MEDIUM);

        this.tag(CHPTags.Entities.WORKERS_LARGE)
                .add(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.CAMEL, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE);
        this.tag(CHPTags.Entities.LARGE_WORKER_TAG)
                .addTag(CHPTags.Entities.WORKERS_LARGE);
    }
}