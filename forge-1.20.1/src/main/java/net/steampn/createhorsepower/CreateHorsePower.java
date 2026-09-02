package net.steampn.createhorsepower;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.steampn.createhorsepower.blocks.crank.WorkerRecoveryQueue;
import net.steampn.createhorsepower.compat.OptionalIntegrations;
import net.steampn.createhorsepower.config.Config;
import net.steampn.createhorsepower.gametest.ForgeRecoveryEdgeGameTests;
import net.steampn.createhorsepower.gametest.HorsePowerGameTests;
import net.steampn.createhorsepower.gametest.HorsePowerLifecycleGameTests;
import net.steampn.createhorsepower.registry.BlockRegister;
import net.steampn.createhorsepower.registry.CHPCreativeTabs;
import net.steampn.createhorsepower.registry.TileEntityRegister;
import net.steampn.createhorsepower.utils.CHPBlockPartials;
import net.steampn.createhorsepower.utils.CHPDiagnostics;
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

        net.steampn.createhorsepower.platform.CHPApi.init(
                new Config(),
                OptionalIntegrations.INSTANCE,
                ResourceLocation::new,
                new net.steampn.createhorsepower.platform.ForgeDeferredDetachStore()
        );

        CREATE_REGISTRATE.registerEventListeners(modEventBus);

        CHPCreativeTabs.register(modEventBus);
        BlockRegister.register();
        TileEntityRegister.register();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerGameTests);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("{} is registered!", BlockRegister.HORSE_CRANK.get());
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        CHPBlockPartials.load();
    }

    private void registerGameTests(final RegisterGameTestsEvent event) {
        event.register(HorsePowerGameTests.class);
        event.register(HorsePowerLifecycleGameTests.class);
        event.register(ForgeRecoveryEdgeGameTests.class);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        net.steampn.createhorsepower.command.CHPCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        int workerEntries = Config.SMALL_CREATURES.get().size() + Config.MEDIUM_CREATURES.get().size() + Config.LARGE_CREATURES.get().size();
        int pathEntries = Config.POOR_PATH.get().size() + Config.NORMAL_PATH.get().size() + Config.GREAT_PATH.get().size();
        LOGGER.info("Create Horse Power CE ready (Forge 1.20.1): configured_workers={} configured_paths={} tfc_optional_compat=true debugLogging={}",
                workerEntries, pathEntries, Config.DEBUG_LOGGING.get());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        WorkerRecoveryQueue.clearTransientState();
        CHPDiagnostics.clearRuntimeState();
    }

    /**
     * Queue marked workers at join time, but do not recover them yet. In
     * vanilla 1.20.1 a persisted block-position leash is restored later from
     * Mob.tick(), so join-time leash ownership is not authoritative.
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

    /** Run deferred recovery after all entities in the level have ticked. */
    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            WorkerRecoveryQueue.process(serverLevel);
        }
    }

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }
}
