package net.steampn.createhorsepower;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
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
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.client.ponders.HorseCrankPonderPlugin;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.gametest.HorsePowerGameTests;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPBlockPartials;
import org.slf4j.Logger;


@Mod(CreateHorsePower.MODID)
public class CreateHorsePower {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "createhorsepower";
    public static final CreateRegistrate CREATE_REGISTRATE = CreateRegistrate.create(MODID)
            .defaultCreativeTab(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MODID, "main")));

    public CreateHorsePower(IEventBus modEventBus, ModContainer modContainer){
        net.steampn.createhorsepower.platform.CHPApi.init(new net.steampn.createhorsepower.config.Config(),
                net.steampn.createhorsepower.compat.OptionalIntegrations.INSTANCE,
                ResourceLocation::fromNamespaceAndPath);

        CREATE_REGISTRATE.addDataGenerator(com.tterrag.registrate.providers.ProviderType.LANG, provider -> {
            PonderIndex.addPlugin(new HorseCrankPonderPlugin());
            PonderIndex.getLangAccess().provideLang(MODID, provider::add);
        });

        CREATE_REGISTRATE.registerEventListeners(modEventBus);

        net.steampn.createhorsepower.registry.CHPCreativeTabs.register(modEventBus);
        BlockRegister.register();
        TileEntityRegister.register();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(net.steampn.createhorsepower.registry.CHPDataMaps::register);
        modEventBus.addListener(this::registerGameTests);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event){
        LOGGER.debug("{} is registered!", BlockRegister.HORSE_CRANK.get());
    }

    private void registerGameTests(final RegisterGameTestsEvent event) {
        event.register(HorsePowerGameTests.class);
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        net.steampn.createhorsepower.command.CHPCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    /**
     * When a mob with a CHP AI-suppression marker loads into a server level,
     * recover it if the owning crank has actually disappeared. The recovery
     * is conservative: it never force-loads chunks, never recovers a marker
     * that still belongs to a live crank, and never treats a different
     * crank at the same coordinates as the same owner.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        WorkerActivityControl.recoverIfOrphaned(mob, serverLevel);
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
