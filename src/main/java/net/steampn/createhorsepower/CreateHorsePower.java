package net.steampn.createhorsepower;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import java.util.function.Function;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    public CreateHorsePower(){
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CREATE_REGISTRATE.registerEventListeners(modEventBus);

        BlockRegister.register();
        TileEntityRegister.register();
        modEventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event){
        LOGGER.debug("{} is registered!", BlockRegister.HORSE_CRANK.get());
    }

    @SubscribeEvent
    public void serverSetup(final ServerStartingEvent event){
        configFileDebug();
    }

    private void configFileDebug(){
        LOGGER.info("Base RPM for all creatures is {}", Config.BASE_CREATURE_RPM.get());
        LOGGER.info("Stress for Small is {}", Config.SMALL_CREATURE_STRESS.get());
        LOGGER.info("Stress for Medium is {}", Config.MEDIUM_CREATURE_STRESS.get());
        LOGGER.info("Stress for Large is {}", Config.LARGE_CREATURE_STRESS.get());

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

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents{

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event){
            CHPBlockPartials.load();
            PonderIndex.addPlugin(new HorseCrankPonderPlugin());
        }
    }
}
