package net.steampn.createhorsepower;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.steampn.createhorsepower.client.ponders.HorseCrankPonderPlugin;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPBlockPartials;
import org.slf4j.Logger;


@Mod(CreateHorsePower.MODID)
public class CreateHorsePower {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "createhorsepower";
    public static final CreateRegistrate CREATE_REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public CreateHorsePower(IEventBus modEventBus, ModContainer modContainer){
        CREATE_REGISTRATE.addDataGenerator(com.tterrag.registrate.providers.ProviderType.LANG, provider -> {
            PonderIndex.addPlugin(new HorseCrankPonderPlugin());
            PonderIndex.getLangAccess().provideLang(MODID, provider::add);
        });

        CREATE_REGISTRATE.registerEventListeners(modEventBus);

        BlockRegister.register();
        TileEntityRegister.register();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(net.steampn.createhorsepower.registry.CHPDataMaps::register);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event){
        LOGGER.debug("{} is registered!", BlockRegister.HORSE_CRANK.get());
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        net.steampn.createhorsepower.command.CHPCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void serverSetup(final ServerStartingEvent event){
        configFileDebug();
    }

    private void configFileDebug(){
        LOGGER.info("Base RPM for all creatures is {}", Config.BASE_CREATURE_RPM.getAsInt());
        LOGGER.info("Stress for Small is {}", Config.SMALL_CREATURE_STRESS.getAsInt());
        LOGGER.info("Stress for Medium is {}", Config.MEDIUM_CREATURE_STRESS.getAsInt());
        LOGGER.info("Stress for Large is {}", Config.LARGE_CREATURE_STRESS.getAsInt());

        Config.SMALL_CREATURES.get().forEach((mob) -> LOGGER.info("Selected Small mob: {}", mob));
        Config.MEDIUM_CREATURES.get().forEach((mob) -> LOGGER.info("Selected Medium mob: {}", mob));
        Config.LARGE_CREATURES.get().forEach((mob) -> LOGGER.info("Selected Large mob: {}", mob));

        Config.POOR_PATH.get().forEach((block) -> LOGGER.info("Selected Poor Path Block: {}", block));
        Config.NORMAL_PATH.get().forEach((block) -> LOGGER.info("Selected Normal Path Block: {}", block));
        Config.GREAT_PATH.get().forEach((block) -> LOGGER.info("Selected Great Path Block: {}", block));
    }

    public static ResourceLocation asResource(String path){
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @Mod(value = CreateHorsePower.MODID, dist = Dist.CLIENT)
    @EventBusSubscriber(modid = CreateHorsePower.MODID, value = Dist.CLIENT)
    public static class ClientModEvents{

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event){
            CHPBlockPartials.load();
            PonderIndex.addPlugin(new HorseCrankPonderPlugin());
        }
    }
}
