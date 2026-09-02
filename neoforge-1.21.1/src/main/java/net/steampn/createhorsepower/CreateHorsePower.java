package net.steampn.createhorsepower;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import net.steampn.createhorsepower.client.ponders.HorseCrankPonderPlugin;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.gametest.CrankIdentityCollisionGameTests;
import net.steampn.createhorsepower.gametest.HorsePowerGameTests;
import net.steampn.createhorsepower.gametest.HorsePowerLifecycleGameTests;
import net.steampn.createhorsepower.gametest.NeoForgeRecoveryEdgeGameTests;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPBlockPartials;
import net.steampn.createhorsepower.utils.CHPDiagnostics;
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
                ResourceLocation::fromNamespaceAndPath,
                new net.steampn.createhorsepower.platform.NeoForgeDeferredDetachStore());

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
        event.register(HorsePowerLifecycleGameTests.class);
        event.register(CrankIdentityCollisionGameTests.class);
        event.register(NeoForgeRecoveryEdgeGameTests.class);
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        net.steampn.createhorsepower.command.CHPCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    /**
     * Queue marked workers at join time. Vanilla 1.21.1 keeps a restored leash
     * as delayed BlockPos data until Leashable.tickLeash(), so join-time
     * getLeashHolder() is intentionally not trusted.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && event.getLevel() instanceof ServerLevel serverLevel) {
            WorkerRecoveryQueue.enqueue(mob, serverLevel);
        }
    }

    /** Run deferred recovery after vanilla has completed the level's entity ticks. */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            WorkerRecoveryQueue.process(serverLevel);
        }
    }

    @SubscribeEvent
    public void serverSetup(final ServerStartingEvent event){
        int workerEntries = Config.SMALL_CREATURES.get().size() + Config.MEDIUM_CREATURES.get().size() + Config.LARGE_CREATURES.get().size();
        int pathEntries = Config.POOR_PATH.get().size() + Config.NORMAL_PATH.get().size() + Config.GREAT_PATH.get().size();
        LOGGER.info("Create Horse Power CE ready (NeoForge 1.21.1): configured_workers={} configured_paths={} tfc_optional_compat=true debugLogging={}",
                workerEntries, pathEntries, Config.DEBUG_LOGGING.get());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        WorkerRecoveryQueue.clearTransientState();
        CHPDiagnostics.clearRuntimeState();
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
