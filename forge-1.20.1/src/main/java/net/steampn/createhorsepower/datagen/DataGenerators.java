package net.steampn.createhorsepower.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.steampn.createhorsepower.CreateHorsePower;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = CreateHorsePower.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {
    private DataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existing = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new CHPRecipeProvider(output));
        CHPBlockTagGenerator blocks = generator.addProvider(event.includeServer(),
                new CHPBlockTagGenerator(output, lookup, existing));
        generator.addProvider(event.includeServer(), new CHPItemTagGenerator(output, lookup, blocks.contentsGetter(), existing));
        generator.addProvider(event.includeServer(), new CHPEntityTagGenerator(output, lookup, existing));
    }
}
