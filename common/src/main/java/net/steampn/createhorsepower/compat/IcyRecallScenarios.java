package net.steampn.createhorsepower.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.steampn.createhorsepower.blocks.crank.AbstractHorseCrankBlockEntity;
import net.steampn.createhorsepower.blocks.crank.HorseCrankEngine;
import net.steampn.createhorsepower.blocks.crank.WorkerActivityControl;
import net.steampn.createhorsepower.blocks.crank.WorkerAttachmentControl;
import net.steampn.createhorsepower.content.stats.WorkerResolver;
import net.steampn.createhorsepower.platform.CHPApi;
import net.steampn.createhorsepower.registry.BlockRegister;

import java.util.UUID;

/** Acceptance coverage of Icy's public management commands using its real released entities. */
public final class IcyRecallScenarios {
    private static final BlockPos SAFE_DESTINATION = new BlockPos(11, 2, 4);
    private static final BlockPos UNSAFE_DESTINATION = new BlockPos(11, 70, 4);

    private record Fixture(AbstractHorseCrankBlockEntity crank, Horse horse, ServerPlayer player) {}

    private IcyRecallScenarios() {}

    private static boolean present(GameTestHelper helper) {
        boolean present = BuiltInRegistries.ENTITY_TYPE.containsKey(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        helper.assertTrue(present == Boolean.getBoolean("createhorsepower.icyExpected"),
                "Public command acceptance must use the requested real Icy runtime");
        if (!present) helper.succeed();
        return present;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = IcyTestActors.serverPlayer(helper);
        player.getAbilities().instabuild = true;
        placePlayer(helper, player, SAFE_DESTINATION);
        return player;
    }

    private static void placePlayer(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos target = helper.absolutePos(relative);
        player.moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0, 0);
    }

    private static Object data(Horse horse, String method, Class<?> argumentType, Object argument)
            throws ReflectiveOperationException {
        return Class.forName("icy.betterhorses.net.IHorseData").getMethod(method, argumentType).invoke(horse, argument);
    }

    private static void register(Horse horse) throws ReflectiveOperationException {
        Class.forName("icy.betterhorses.net.HorseTracker").getMethod("register", AbstractHorse.class).invoke(null, horse);
    }

    private static Fixture fixture(GameTestHelper helper) throws ReflectiveOperationException {
        for (int x = 1; x <= 15; x++) {
            for (int z = 1; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        BlockPos crankPos = new BlockPos(4, 2, 4);
        for (BlockPos offset : HorseCrankEngine.generateOffsetsForRadius(2.5F)) {
            helper.setBlock(crankPos.offset(offset), Blocks.DIRT_PATH);
        }
        helper.setBlock(crankPos, BlockRegister.HORSE_CRANK.get());
        AbstractHorseCrankBlockEntity crank = (AbstractHorseCrankBlockEntity) helper.getLevel()
                .getBlockEntity(helper.absolutePos(crankPos));
        ServerPlayer player = player(helper);
        var type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        Horse horse = (Horse) type.create(helper.getLevel());
        helper.assertTrue(horse != null, "Released Icy horse factory must succeed");
        horse.moveTo(helper.absolutePos(new BlockPos(6, 2, 4)), 0, 0);
        horse.setTamed(true);
        data(horse, "bh_setOwner", UUID.class, player.getUUID());
        data(horse, "bh_setBond", int.class, 2);
        helper.getLevel().addFreshEntity(horse);
        attach(crank, horse);
        return new Fixture(crank, horse, player);
    }

    private static void attach(AbstractHorseCrankBlockEntity crank, Horse horse) {
        horse.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(horse.level(), crank.getBlockPos()), false);
        crank.engine().attachWorker(horse, WorkerResolver.resolve(horse));
        WorkerActivityControl.acquire(horse, crank.getBlockPos(), crank.engine().crankInstanceUuid());
    }

    private static boolean command(String name, ServerPlayer player, UUID id) throws ReflectiveOperationException {
        Object outcome = Class.forName("icy.betterhorses.net.HorseManagement")
                .getMethod(name, ServerPlayer.class, UUID.class).invoke(null, player, id);
        return Boolean.TRUE.equals(outcome.getClass().getMethod("ok").invoke(outcome));
    }

    private static void assertReserved(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.horse().getUUID().equals(fixture.crank().engine().getWorkerUuid()),
                "Failed public management command must preserve the worker reservation");
        helper.assertTrue(WorkerAttachmentControl.hasMarker(fixture.horse()),
                "Failed public management command must preserve the original attachment marker");
    }

    private static void assertReleased(GameTestHelper helper, Fixture fixture, Horse current) {
        helper.assertTrue(helper.getLevel().getEntity(fixture.horse().getUUID()) == current,
                "Only upstream's registered horse body may be released");
        helper.assertTrue(fixture.crank().engine().getWorkerUuid() == null,
                "Successful public management command must release the crank reservation");
        helper.assertTrue(!WorkerAttachmentControl.hasMarker(current) && !WorkerActivityControl.hasMarker(current)
                        && !current.isNoAi() && !current.isLeashed(),
                "Successful public command must restore the AI baseline and clear current and pending attachments");
    }

    @FunctionalInterface
    private interface CheckedAction { void run() throws ReflectiveOperationException; }

    private static void later(GameTestHelper helper, long ticks, CheckedAction action) {
        helper.runAfterDelay(ticks, () -> {
            try { action.run(); }
            catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        });
    }

    public static void loadedCommandsRespectFailureAndRelease(GameTestHelper helper) throws ReflectiveOperationException {
        if (!present(helper)) return;
        Fixture fixture = fixture(helper);
        later(helper, 2, () -> {
            Horse horse = fixture.horse();
            UUID id = horse.getUUID();
            data(horse, "bh_setOwner", UUID.class, UUID.randomUUID());
            helper.assertTrue(!command("whistle", fixture.player(), id)
                            && !command("sendHome", fixture.player(), id),
                    "Unowned horse must reject both public commands");
            assertReserved(helper, fixture);
            data(horse, "bh_setOwner", UUID.class, fixture.player().getUUID());
            data(horse, "bh_setBond", int.class, 0);
            helper.assertTrue(!command("whistle", fixture.player(), id), "Unbonded whistle must fail");
            assertReserved(helper, fixture);
            data(horse, "bh_setBond", int.class, 2);
            data(horse, "bh_setHome", BlockPos.class, helper.absolutePos(UNSAFE_DESTINATION));
            helper.assertTrue(!command("sendHome", fixture.player(), id), "Unsafe loaded home must fail");
            assertReserved(helper, fixture);
            placePlayer(helper, fixture.player(), UNSAFE_DESTINATION);
            helper.assertTrue(!command("whistle", fixture.player(), id), "Unsafe loaded whistle must fail");
            assertReserved(helper, fixture);
            placePlayer(helper, fixture.player(), SAFE_DESTINATION);
            helper.assertTrue(command("whistle", fixture.player(), id), "Owned bonded nearby public whistle must succeed");
            assertReleased(helper, fixture, horse);
            attach(fixture.crank(), horse);
            data(horse, "bh_setHome", BlockPos.class, helper.absolutePos(SAFE_DESTINATION));
            helper.assertTrue(command("sendHome", fixture.player(), id), "Safe loaded public sendHome must succeed");
            assertReleased(helper, fixture, horse);
            later(helper, 3, () -> { assertReleased(helper, fixture, horse); helper.succeed(); });
        });
    }

    public static void snapshotWhistleRespectsFailureAndRelease(GameTestHelper helper) throws ReflectiveOperationException {
        if (!present(helper)) return;
        Fixture fixture = fixture(helper);
        register(fixture.horse());
        fixture.horse().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        later(helper, 2, () -> {
            UUID id = fixture.horse().getUUID();
            helper.assertTrue(helper.getLevel().getEntity(id) == null, "CHP must not recreate an unloaded worker");
            placePlayer(helper, fixture.player(), UNSAFE_DESTINATION);
            helper.assertTrue(!command("whistle", fixture.player(), id), "Unsafe public snapshot whistle must fail");
            assertReserved(helper, fixture);
            helper.assertTrue(helper.getLevel().getEntity(id) == null, "Failed snapshot whistle must not add a body");
            placePlayer(helper, fixture.player(), SAFE_DESTINATION);
            helper.assertTrue(command("whistle", fixture.player(), id), "Safe public snapshot whistle must succeed");
            Horse recalled = (Horse) helper.getLevel().getEntity(id);
            helper.assertTrue(recalled != fixture.horse(), "Upstream whistle must register its own replacement body");
            assertReleased(helper, fixture, recalled);
            later(helper, 3, () -> { assertReleased(helper, fixture, recalled); helper.succeed(); });
        });
    }

    public static void snapshotSendHomeReleases(GameTestHelper helper) throws ReflectiveOperationException {
        snapshotSendHome(helper, false);
    }

    public static void snapshotSendHomeUnsafePreservesAssignment(GameTestHelper helper) throws ReflectiveOperationException {
        snapshotSendHome(helper, true);
    }

    private static void snapshotSendHome(GameTestHelper helper, boolean unsafe) throws ReflectiveOperationException {
        if (!present(helper)) return;
        Fixture fixture = fixture(helper);
        data(fixture.horse(), "bh_setHome", BlockPos.class,
                helper.absolutePos(unsafe ? UNSAFE_DESTINATION : SAFE_DESTINATION));
        register(fixture.horse());
        fixture.horse().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        later(helper, 2, () -> {
            UUID id = fixture.horse().getUUID();
            helper.assertTrue(helper.getLevel().getEntity(id) == null, "CHP must not create a body before public sendHome");
            boolean success = command("sendHome", fixture.player(), id);
            helper.assertTrue(success != unsafe, "Public snapshot sendHome must report the actual placement outcome");
            if (unsafe) {
                assertReserved(helper, fixture);
                helper.assertTrue(helper.getLevel().getEntity(id) == null, "Failed snapshot sendHome must not add a body");
                helper.succeed();
            } else {
                Horse recalled = (Horse) helper.getLevel().getEntity(id);
                helper.assertTrue(recalled != fixture.horse(), "Upstream sendHome must register its own replacement body");
                assertReleased(helper, fixture, recalled);
                later(helper, 3, () -> { assertReleased(helper, fixture, recalled); helper.succeed(); });
            }
        });
    }

    public static void revivalRespectsFailureAndReleases(GameTestHelper helper) throws ReflectiveOperationException {
        if (!present(helper)) return;
        Fixture fixture = fixture(helper);
        later(helper, 2, () -> {
            UUID id = fixture.horse().getUUID();
            helper.assertTrue(!command("revive", fixture.player(), id), "Public revive without a death record must fail");
            assertReserved(helper, fixture);
            // Use upstream's real death-record serializer, retaining any CHP
            // markers it copies, rather than constructing a synthetic body.
            Class<?> stateType = Class.forName("icy.betterhorses.net.HorseTrackerState");
            Object state = stateType.getMethod("get", MinecraftServer.class).invoke(null, helper.getLevel().getServer());
            stateType.getMethod("recordDeath", AbstractHorse.class).invoke(state, fixture.horse());
            fixture.horse().remove(Entity.RemovalReason.KILLED);
            later(helper, 2, () -> {
                helper.assertTrue(helper.getLevel().getEntity(id) == null, "Dead worker must remain absent before public revival");
                placePlayer(helper, fixture.player(), UNSAFE_DESTINATION);
                helper.assertTrue(!command("revive", fixture.player(), id), "Unsafe public revive must fail");
                assertReserved(helper, fixture);
                helper.assertTrue(helper.getLevel().getEntity(id) == null, "Failed revival must not add a body");
                placePlayer(helper, fixture.player(), SAFE_DESTINATION);
                helper.assertTrue(command("revive", fixture.player(), id), "Safe public revival must succeed");
                Horse revived = (Horse) helper.getLevel().getEntity(id);
                helper.assertTrue(revived != fixture.horse(), "Only upstream may create the revived body");
                assertReleased(helper, fixture, revived);
                later(helper, 3, () -> { assertReleased(helper, fixture, revived); helper.succeed(); });
            });
        });
    }

    public static void crossDimensionRevivalHonorsSettledNoDrop(GameTestHelper helper) throws ReflectiveOperationException {
        crossDimensionRevivalDisposition(helper, true);
    }

    public static void crossDimensionRevivalReturnsOneAttachment(GameTestHelper helper) throws ReflectiveOperationException {
        crossDimensionRevivalDisposition(helper, false);
    }

    private static void crossDimensionRevivalDisposition(GameTestHelper helper, boolean settledNoDrop)
            throws ReflectiveOperationException {
        if (!present(helper)) return;
        // The real public revival command can cross dimensions even though
        // Icy's ordinary snapshot whistle deliberately cannot do so.
        ServerPlayer player = player(helper);
        for (int x = 1; x <= 15; x++) {
            for (int z = 1; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        var source = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(source != null && source != helper.getLevel(), "Independent source dimension must exist");
        BlockPos sourcePos = new BlockPos(22000000, 100, 22000000);
        helper.assertTrue(!source.hasChunkAt(sourcePos), "Source fixture must not load the old crank chunk");
        var type = BuiltInRegistries.ENTITY_TYPE.get(CHPApi.id(IcyBetterHorses.NAMESPACE, "arabian_horse"));
        Horse horse = (Horse) type.create(source);
        helper.assertTrue(horse != null, "Real released source-dimension horse must be constructible");
        horse.moveTo(sourcePos, 0, 0);
        horse.setTamed(true);
        data(horse, "bh_setOwner", UUID.class, player.getUUID());
        data(horse, "bh_setBond", int.class, 2);
        UUID owner = UUID.randomUUID();
        var profile = new net.steampn.createhorsepower.content.attachment.AttachmentProfile(
                CHPApi.modId("icy_cross_dimension_revival"), 1,
                java.util.Set.of(CHPApi.id("minecraft", "saddle")), java.util.Set.of(),
                net.steampn.createhorsepower.content.attachment.AttachmentMode.VIRTUAL_TETHER, 3,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), true, true, 1);
        WorkerAttachmentControl.markAttached(horse, sourcePos, owner, profile,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE), true);
        WorkerActivityControl.acquire(horse, sourcePos, owner);
        if (settledNoDrop) CHPApi.deferredDetaches().put(source, horse.getUUID(),
                new net.steampn.createhorsepower.platform.DeferredDetachStore.Entry(sourcePos, owner, false));
        Class<?> stateType = Class.forName("icy.betterhorses.net.HorseTrackerState");
        Object state = stateType.getMethod("get", MinecraftServer.class).invoke(null, helper.getLevel().getServer());
        // A serializer fixture captures a real Icy source-level body without
        // registering it or creating blocks/chunks in the other dimension.
        stateType.getMethod("recordDeath", AbstractHorse.class).invoke(state, horse);
        var bounds = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                .expandTowards(16, 8, 16);
        int before = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds,
                item -> item.getItem().is(net.minecraft.world.item.Items.SADDLE)).size();
        helper.assertTrue(command("revive", player, horse.getUUID()), "Real public revival must register destination body");
        Horse revived = (Horse) helper.getLevel().getEntity(horse.getUUID());
        helper.assertTrue(revived != null && revived.level() == helper.getLevel()
                        && !WorkerAttachmentControl.hasMarker(revived) && !WorkerActivityControl.hasMarker(revived)
                        && !revived.isNoAi(), "Cross-world revival must release attachment and restore baseline immediately");
        int after = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds,
                item -> item.getItem().is(net.minecraft.world.item.Items.SADDLE)).size();
        helper.assertTrue(after == before + (settledNoDrop ? 0 : 1),
                "Settled source no-drop must survive revival; a new stale-body guard must still return exactly one item");
        var guard = CHPApi.deferredDetaches().get(source, horse.getUUID());
        helper.assertTrue(guard != null && guard.matches(sourcePos, owner) && !guard.dropLead(),
                "Source dimension must retain its exact no-drop guard for any stale original body");
        var sourceBounds = new net.minecraft.world.phys.AABB(sourcePos).inflate(8);
        int sourceItems = source.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sourceBounds).size();
        WorkerAttachmentControl.recoverAfterTimeout(horse, source);
        helper.assertTrue(!WorkerAttachmentControl.hasMarker(horse) && !horse.isNoAi()
                        && CHPApi.deferredDetaches().get(source, horse.getUUID()) == null
                        && source.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sourceBounds).size() == sourceItems,
                "Stale source serializer fixture must consume the guard and restore AI without a second refund");
        helper.assertTrue(!source.hasChunkAt(sourcePos), "Revival cleanup must never load the source crank chunk");
        later(helper, 3, () -> {
            helper.assertTrue(!revived.isNoAi() && !WorkerAttachmentControl.hasMarker(revived),
                    "Destination must remain released after normal ticks");
            helper.succeed();
        });
    }
}
