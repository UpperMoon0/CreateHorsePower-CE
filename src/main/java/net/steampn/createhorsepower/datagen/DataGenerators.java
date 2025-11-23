package net.steampn.createhorsepower.datagen;

import com.tterrag.registrate.providers.ProviderType;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.fml.common.EventBusSubscriber;
import net.steampn.createhorsepower.CreateHorsePower;
import net.steampn.createhorsepower.client.ponders.HorseCrankPonderPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static net.steampn.createhorsepower.CreateHorsePower.MODID;

@EventBusSubscriber(modid = CreateHorsePower.MODID)
public class DataGenerators {
    @net.neoforged.bus.api.SubscribeEvent
    public static void gatherData(net.neoforged.neoforge.data.event.GatherDataEvent event){
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        net.neoforged.neoforge.common.data.ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new CHPRecipeProvider(packOutput, lookupProvider));

        CHPBlockTagGenerator blockTagGenerator = generator.addProvider(event.includeServer(),
                new CHPBlockTagGenerator(packOutput, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeServer(), new CHPItemTagGenerator(packOutput, lookupProvider, blockTagGenerator.contentsGetter(), existingFileHelper));
        CreateHorsePower.CREATE_REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> {
            BiConsumer<String, String> langConsumer = provider::add;
            providePonderLang(langConsumer);
        });
    }

    private static void providePonderLang(BiConsumer<String, String> consumer) {
        PonderIndex.addPlugin(new HorseCrankPonderPlugin());

        PonderIndex.getLangAccess().provideLang(MODID, consumer);
    }
}
