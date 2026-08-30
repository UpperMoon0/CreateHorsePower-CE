package net.steampn.createhorsepower;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.steampn.createhorsepower.compat.OptionalIntegrations;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.CHPCreativeTabs;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPBlockPartials;
import org.slf4j.Logger;

@Mod(CreateHorsePower.MODID)
public class CreateHorsePower {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = net.steampn.createhorsepower.CHPConstants.MODID;
    public static final CreateRegistrate CREATE_REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab(ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    new ResourceLocation(MODID, "main")));

    public CreateHorsePower() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        net.steampn.createhorsepower.platform.CHPApi.init(new Config(), OptionalIntegrations.INSTANCE, ResourceLocation::new);

        CREATE_REGISTRATE.registerEventListeners(modEventBus);

        CHPCreativeTabs.register(modEventBus);
        BlockRegister.register();
        TileEntityRegister.register();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("{} is registered!", BlockRegister.HORSE_CRANK.get());
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        CHPBlockPartials.load();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        net.steampn.createhorsepower.command.CHPCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }
}
